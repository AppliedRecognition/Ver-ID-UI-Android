package com.appliedrec.verid.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.VerIDImage;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VerIDSessionFragment extends Fragment implements IVerIDSessionFragment, TextureView.SurfaceTextureListener {

    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private Integer sensorOrientation;
    private Size previewSize;
    private CameraDevice cameraDevice;
    private TextureView textureView;
    private TextView instructionTextView;
    private DetectedFaceView detectedFaceView;
    private CameraCaptureSession captureSession;
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            VerIDSessionFragment.this.cameraDevice = cameraDevice;
            startPreview();
            cameraOpenCloseLock.release();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            VerIDSessionFragment.this.cameraDevice = null;
            cameraId = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            VerIDSessionFragment.this.cameraDevice = null;
            cameraId = null;
        }

    };
    private CaptureRequest.Builder previewBuilder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private SynchronousQueue<VerIDImage> imageQueue = new SynchronousQueue<>();
    private ThreadPoolExecutor imageProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private VerIDSessionFragmentDelegate delegate;
    private IStringTranslator stringTranslator;
    private Matrix faceBoundsMatrix = new Matrix();
    private int backgroundColour = 0x80000000;
    private String cameraId;

    // region Fragment lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ConstraintLayout view = (ConstraintLayout) inflater.inflate(R.layout.fragment_session, container, false);
        textureView = view.findViewById(R.id.view_finder);
        instructionTextView = view.findViewById(R.id.instruction_textview);
        detectedFaceView = view.findViewById(R.id.detectedFaceView);
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBackgroundThread();
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof VerIDSessionFragmentDelegate) {
            delegate = (VerIDSessionFragmentDelegate) context;
        }
        if (context instanceof IStringTranslator) {
            stringTranslator = (IStringTranslator) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        delegate = null;
        stringTranslator = null;
    }

    // endregion

    @Override
    public void startCamera() {
        startBackgroundThread();
        textureView.setSurfaceTextureListener(this);
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
    }

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, @Nullable EulerAngle offsetAngleFromBearing) {
        @Nullable String labelText;
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        boolean showArrow;
        if (getDelegate() == null || getDelegate().getSessionSettings() == null) {
            return;
        }
        VerIDSessionSettings sessionSettings = getDelegate().getSessionSettings();
        if (sessionSettings != null && sessionResult.getAttachments().length >= sessionSettings.getNumberOfResultsToCollect()) {
            labelText = getTranslatedString("Please wait");
            ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
            cutoutBounds = null;
            faceAngle = null;
            showArrow = false;
        } else {
            switch (faceDetectionResult.getStatus()) {
                case FACE_FIXED:
                case FACE_ALIGNED:
                    labelText = getTranslatedString("Great, hold it");
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = null;
                    faceAngle = null;
                    showArrow = false;
                    break;
                case FACE_MISALIGNED:
                    labelText = getTranslatedString("Slowly turn to follow the arrow");
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = null;
                    faceAngle = faceDetectionResult.getFaceAngle();
                    showArrow = true;
                    break;
                case FACE_TURNED_TOO_FAR:
                    labelText = null;
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = null;
                    faceAngle = null;
                    showArrow = false;
                    break;
                default:
                    labelText = getTranslatedString("Align your face with the oval");
                    ovalBounds = defaultFaceBounds;
                    cutoutBounds = faceDetectionResult.getFaceBounds();
                    faceAngle = null;
                    showArrow = false;
            }
        }
        try {
            faceBoundsMatrix.mapRect(ovalBounds);
            if (cutoutBounds != null) {
                faceBoundsMatrix.mapRect(cutoutBounds);
            }
            int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), sessionResult.getError());
            int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), sessionResult.getError());
            instructionTextView.setText(labelText);
            instructionTextView.setTextColor(textColour);
            instructionTextView.setBackgroundColor(colour);
            instructionTextView.setVisibility(labelText != null ? View.VISIBLE : View.GONE);

            ((ConstraintLayout.LayoutParams)instructionTextView.getLayoutParams()).topMargin = (int) (ovalBounds.top - instructionTextView.getHeight() - getResources().getDisplayMetrics().density * 16f);
            setTextViewColour(colour, textColour);
            Double angle = null;
            Double distance = null;
            if (faceAngle != null && offsetAngleFromBearing != null && showArrow) {
                angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
            }
            detectedFaceView.setFaceRect(ovalBounds, cutoutBounds, colour, backgroundColour, angle, distance);
//            // Uncomment to plot face landmarks for debugging purposes
//            if (faceDetectionResult.getFace() != null && faceDetectionResult.getFace().getLandmarks() != null && faceDetectionResult.getFace().getLandmarks().length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                float[] landmarks = new float[faceDetectionResult.getFace().getLandmarks().length*2];
//                int i=0;
//                for (PointF pt : faceDetectionResult.getFace().getLandmarks()) {
//                    landmarks[i++] = pt.x;
//                    landmarks[i++] = pt.y;
//                }
//                faceBoundsMatrix.mapPoints(landmarks);
//                PointF[] pointLandmarks = new PointF[faceDetectionResult.getFace().getLandmarks().length];
//                Arrays.parallelSetAll(pointLandmarks, idx -> new PointF(landmarks[idx*2], landmarks[idx*2+1]));
//                detectedFaceView.setFaceLandmarks(pointLandmarks);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected VerIDSessionFragmentDelegate getDelegate() {
        return delegate;
    }

    private String getTranslatedString(String original, Object ...args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        } else {
            return String.format(original, args);
        }
    }

    /**
     * Get the colour of the oval drawn around the face and of the background of the instruction text label. The colour should reflect the supplied state of the face detection.
     * @param faceDetectionStatus Face detection status
     * @param resultError Error that will be returned in the session result
     * @return Integer representing a colour in ARGB space
     * @since 1.6.0
     */
    public int getOvalColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus, @Nullable Exception resultError) {
        if (resultError != null) {
            return 0xFFFF0000;
        }
        switch (faceDetectionStatus) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return 0xFF36AF00;
            default:
                return 0xFFFFFFFF;
        }
    }

    /**
     * Get the colour of the text inside the instruction text label. The colour should reflect the supplied state of the face detection.
     * @param faceDetectionStatus Face detection status
     * @return Integer representing a colour in ARGB space
     * @since 1.6.0
     */
    public int getTextColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus, @Nullable Exception resultError) {
        if (resultError != null) {
            return 0xFFFFFFFF;
        }
        switch (faceDetectionStatus) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return 0xFFFFFFFF;
            default:
                return 0xFF000000;
        }
    }

    private void setTextViewColour(int background, int text) {
        float density = getResources().getDisplayMetrics().density;
        float[] corners = new float[8];
        Arrays.fill(corners, 10 * density);
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(corners, null, null));
        shapeDrawable.setPadding((int)(8f * density), (int)(4f * density), (int)(8f * density), (int)(4f * density));
        shapeDrawable.getPaint().setColor(background);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            instructionTextView.setBackgroundDrawable(shapeDrawable);
        } else {
            instructionTextView.setBackground(shapeDrawable);
        }
        instructionTextView.setTextColor(text);
    }

    @Override
    public void clearCameraOverlay() {

    }

    @Override
    public void clearCameraPreview() {

    }

    @Override
    public VerIDImage dequeueImage() throws Exception {
        return imageQueue.take();
    }

    @Override
    public int getOrientationOfCamera() {
        return sensorOrientation;
    }

    // region Surface texture listener

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
//        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (imageProcessingExecutor != null && imageProcessingExecutor.getActiveCount() == 0 && imageProcessingExecutor.getQueue().isEmpty()) {
            int rotation = textureView.getDisplay().getRotation();
            int surfaceRotationDegrees;
            switch (rotation) {
                case Surface.ROTATION_0:
                    surfaceRotationDegrees = 0;
                    break;
                case Surface.ROTATION_90:
                    surfaceRotationDegrees = 90;
                    break;
                case Surface.ROTATION_180:
                    surfaceRotationDegrees = 180;
                    break;
                case Surface.ROTATION_270:
                    surfaceRotationDegrees = 270;
                    break;
                default:
                    surfaceRotationDegrees = 0;
            }
            Bitmap bitmap = textureView.getBitmap(previewSize.getHeight(), previewSize.getWidth());
            imageProcessingExecutor.execute(() -> {
                Matrix matrix = new Matrix();
                if (getDelegate().getSessionSettings().getFacingOfCameraLens() != VerIDSessionSettings.LensFacing.BACK) {
                    matrix.setScale(-1, 1);
                }
                matrix.postRotate(surfaceRotationDegrees);
                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                bitmap.recycle();
                VerIDImage verIDImage = new VerIDImage(flippedBitmap, ExifInterface.ORIENTATION_NORMAL);
                try {
                    imageQueue.put(verIDImage);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    // endregion

    /**
     * Indicates how to transform an image of the given size to fit to the fragment view.
     * @param size Image size
     * @return Transformation matrix
     * @since 1.0.0
     */
    public Matrix imageScaleTransformAtImageSize(com.appliedrec.verid.core.Size size) throws Exception {
        return faceBoundsMatrix;
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        if (cameraId != null) {
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Time out waiting to acquire camera lock.");
            }
            String[] cameras = manager.getCameraIdList();
            cameraId = null;
            int requestedLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
            if (getDelegate().getSessionSettings().getFacingOfCameraLens() == VerIDSessionSettings.LensFacing.BACK) {
                requestedLensFacing = CameraCharacteristics.LENS_FACING_BACK;
            }
            for (String camId : cameras) {
                Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null && lensFacing == requestedLensFacing) {
                    cameraId = camId;
                    break;
                }
            }
            if (cameraId == null) {
                throw new Exception("Camera not available");
            }

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get video sizes");
            }
            int w, h;
            if ((sensorOrientation - getDisplayRotation()) % 180 == 0) {
                w = width;
                h = height;
            } else {
                w = height;
                h = width;
            }

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), w, h);
            configureTransform(previewSize.getWidth(), previewSize.getHeight());
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access camera", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
        } catch (InterruptedException e) {
            throw new RuntimeException("Opening of camera interrupted");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, (lhs, rhs) -> Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight()));
        } else {
            return choices[0];
        }
    }


    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == cameraDevice || !textureView.isAvailable() || null == previewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            previewBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = getActivity();
                            if (null != activity) {
                                Toast.makeText(activity, "Failed to start preview", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            captureSession.close();
            captureSession = null;
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == cameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(previewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    @MainThread
    protected int getDisplayRotation() throws Exception {
        if (getView() == null) {
            throw new Exception("View not loaded");
        }
        switch (getView().getDisplay().getRotation()) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return  90;
            case Surface.ROTATION_180:
                return  180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `textureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `textureView` is fixed.
     *
     * @param width  The width of `textureView`
     * @param height The height of `textureView`
     */
    protected void configureTransform(int width, int height) {
        View view = getView();
        if (view == null) {
            return;
        }
        if (textureView.getDisplay() == null) {
            return;
        }
        RectF viewRect = new RectF(0,0, detectedFaceView.getWidth(), detectedFaceView.getHeight());
        float rotationDegrees = 0;
        try {
            rotationDegrees = (float)getDisplayRotation();
        } catch (Exception e) {

        }
        float w;
        float h;
        if (sensorOrientation % 180 == 0) {
            w = width;
            h = height;
        } else {
            w = height;
            h = width;
        }
        float viewAspectRatio = viewRect.width()/viewRect.height();
        float imageAspectRatio = rotationDegrees % 180 == 0 ? w/h : h/w;
        float scale = 1f;
        if (viewAspectRatio > imageAspectRatio) {
            scale = viewRect.width()/(rotationDegrees % 180 == 0 ? height : width);
        } else {
            scale = viewRect.height()/(rotationDegrees % 180 == 0 ? width : height);
        }
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(textureView.getLayoutParams());
        Matrix matrix = new Matrix();
        if (rotationDegrees % 180 == 0) {
            layoutParams.width = (int) (scale * w);
            layoutParams.height = (int) (scale * h);
        } else {
            layoutParams.width = (int) (scale * width);
            layoutParams.height = (int) (scale * height);
        }
        layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        textureView.setLayoutParams(layoutParams);

        RectF textureRect = new RectF(0, 0, layoutParams.width, layoutParams.height);
        float centerX = textureRect.centerX();
        float centerY = textureRect.centerY();
        if (rotationDegrees != 0) {
            if (rotationDegrees % 180 != 0) {
                matrix.setScale((float) height / (float) width, (float) width / (float) height, centerX, centerY);
            }
            matrix.postRotate(0 - rotationDegrees, centerX, centerY);
        }
        textureView.setTransform(matrix);

        // Configure transform for displaying faces
        RectF imageRect = new RectF();
        if ((rotationDegrees - sensorOrientation) % 180 == 0) {
            imageRect.right = width;
            imageRect.bottom = height;
        } else {
            imageRect.right = height;
            imageRect.bottom = width;
        }
        RectF targetRect = new RectF();
        float cameraAspectRatio = imageRect.width() / imageRect.height();
        if (cameraAspectRatio > viewAspectRatio) {
            targetRect.right = viewRect.height() * cameraAspectRatio;
            targetRect.bottom = viewRect.height();
        } else {
            targetRect.right = viewRect.width();
            targetRect.bottom = viewRect.width() / cameraAspectRatio;
        }
        targetRect.offset(viewRect.centerX()-targetRect.centerX(), viewRect.centerY()-targetRect.centerY());

        faceBoundsMatrix.reset();
        faceBoundsMatrix.setRectToRect(imageRect, targetRect, Matrix.ScaleToFit.FILL);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (backgroundThread == null) {
            return;
        }
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}