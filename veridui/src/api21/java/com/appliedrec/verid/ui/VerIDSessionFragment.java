package com.appliedrec.verid.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceCapture;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.IFaceCaptureListener;
import com.appliedrec.verid.core.IFaceDetectionListener;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.ui.databinding.FragmentSessionBinding;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VerIDSessionFragment extends Fragment implements IVerIDSessionFragment2, TextureView.SurfaceTextureListener, IVideoRecorder {

    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private Integer sensorOrientation;
    private Size previewSize;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            VerIDSessionFragment.this.cameraDevice = cameraDevice;
            startPreview();
            cameraOpenCloseLock.release();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            stopRecordingVideo();
            cameraDevice.close();
            VerIDSessionFragment.this.cameraDevice = null;
            cameraId = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            stopRecordingVideo();
            cameraDevice.close();
            VerIDSessionFragment.this.cameraDevice = null;
            cameraId = null;
        }

    };
    private CaptureRequest.Builder previewBuilder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private IStringTranslator stringTranslator;
    private ITextSpeaker textSpeaker;
    private final Matrix faceBoundsMatrix = new Matrix();
    private String cameraId;
    private MediaRecorder mediaRecorder;
    private final Object mediaRecorderLock = new Object();
    private boolean isRecordingVideo = false;
    private File videoFile;
    private Size videoSize;
    private FragmentSessionBinding viewBinding;
    private T sessionSettings;
    private boolean shouldShowCameraOverlay = false;

    // region Fragment lifecycle


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            sessionSettings = args.getParcelable(ARG_SESSION_SETTINGS);
            if (sessionSettings != null && sessionSettings.shouldRecordSessionVideo()) {
                try {
                    videoFile = File.createTempFile("verid_video_", ".mp4");
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewBinding = FragmentSessionBinding.inflate(inflater, container, false);
        if (shouldShowCameraOverlay) {
            showCameraOverlay();
        }
        return viewBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBackgroundThread();
        if (viewBinding != null) {
            viewBinding.viewFinder.setSurfaceTextureListener(null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closePreviewSession();
        stopRecordingVideo();
        closeCamera();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            listener = (Listener)context;
        }
        if (context instanceof IStringTranslator) {
            stringTranslator = (IStringTranslator) context;
        }
        if (context instanceof ITextSpeaker) {
            textSpeaker = (ITextSpeaker) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
        stringTranslator = null;
        sessionException = null;
        textSpeaker = null;
    }

    // endregion

    @Override
    public void showCameraOverlay() {
        new Handler(Looper.getMainLooper()).post(() -> {
            shouldShowCameraOverlay = true;
            if (viewBinding != null) {
                viewBinding.detectedFaceView.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void hideCameraOverlay() {
        new Handler(Looper.getMainLooper()).post(() -> {
            shouldShowCameraOverlay = false;
            if (viewBinding != null) {
                viewBinding.detectedFaceView.setVisibility(View.GONE);
            }
        });
    }

    public void startCamera() {
        startBackgroundThread();
        textureView.setSurfaceTextureListener(this);
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
    }

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, @Nullable EulerAngle offsetAngleFromBearing, String labelText) {
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        if (getDelegate() == null || getDelegate().getSessionSettings() == null) {
            return;
        }
        VerIDSessionSettings sessionSettings = getDelegate().getSessionSettings();
        if (sessionSettings != null && sessionResult.getAttachments().length >= sessionSettings.getNumberOfResultsToCollect()) {
            ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
            cutoutBounds = new RectF(ovalBounds);
            faceAngle = null;
        } else {
            switch (faceDetectionResult.getStatus()) {
                case FACE_FIXED:
                case FACE_ALIGNED:
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds());
                    faceAngle = null;
                    break;
                case FACE_MISALIGNED:
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds());
                    faceAngle = faceDetectionResult.getFaceAngle();
                    break;
                case FACE_TURNED_TOO_FAR:
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = new RectF(ovalBounds);
                    faceAngle = null;
                    break;
                default:
                    ovalBounds = defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds);
                    faceAngle = null;
            }
        }
        try {
            faceBoundsMatrix.mapRect(ovalBounds);
            faceBoundsMatrix.mapRect(cutoutBounds);
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
            if (faceAngle != null && offsetAngleFromBearing != null) {
                angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
            }
            int backgroundColour = 0x80000000;
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

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, @Nullable EulerAngle offsetAngleFromBearing) {
        @Nullable String labelText;
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        if (getDelegate() == null || getDelegate().getSessionSettings() == null) {
            return;
        }
        VerIDSessionSettings sessionSettings = getDelegate().getSessionSettings();
        if (sessionSettings != null && sessionResult.getAttachments().length >= sessionSettings.getNumberOfResultsToCollect()) {
            labelText = getTranslatedString("Please wait");
            ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
            cutoutBounds = new RectF(ovalBounds);
            faceAngle = null;
        } else {
            switch (faceDetectionResult.getStatus()) {
                case FACE_FIXED:
                case FACE_ALIGNED:
                    labelText = getTranslatedString("Great, hold it");
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds());
                    faceAngle = null;
                    break;
                case FACE_MISALIGNED:
                    labelText = getTranslatedString("Slowly turn to follow the arrow");
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds());
                    faceAngle = faceDetectionResult.getFaceAngle();
                    break;
                case FACE_TURNED_TOO_FAR:
                    labelText = null;
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = new RectF(ovalBounds);
                    faceAngle = null;
                    break;
                default:
                    labelText = getTranslatedString("Align your face with the oval");
                    ovalBounds = defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds);
                    faceAngle = null;
            }
        }
        try {
            faceBoundsMatrix.mapRect(ovalBounds);
            faceBoundsMatrix.mapRect(cutoutBounds);
            int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), sessionResult.getError());
            int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), sessionResult.getError());
            instructionTextView.setText(labelText);
            instructionTextView.setTextColor(textColour);
            instructionTextView.setBackgroundColor(colour);
            instructionTextView.setVisibility(labelText != null ? View.VISIBLE : View.GONE);

            if (getDelegate().getSessionSettings().shouldSpeakPrompts() && labelText != null && getContext() != null) {
                Locale locale = null;
                if (stringTranslator != null && stringTranslator instanceof ILocaleProvider) {
                    locale = ((ILocaleProvider)stringTranslator).getLocale();
                }
                if (textSpeaker != null) {
                    textSpeaker.speak(labelText, locale, false);
                }
            }

            ((ConstraintLayout.LayoutParams)instructionTextView.getLayoutParams()).topMargin = (int) (ovalBounds.top - instructionTextView.getHeight() - getResources().getDisplayMetrics().density * 16f);
            setTextViewColour(colour, textColour);
            Double angle = null;
            Double distance = null;
            if (faceAngle != null && offsetAngleFromBearing != null) {
                angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
            }
            int backgroundColour = 0x80000000;
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
        if (viewBinding == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float[] corners = new float[8];
        Arrays.fill(corners, 10 * density);
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(corners, null, null));
        shapeDrawable.setPadding((int)(8f * density), (int)(4f * density), (int)(8f * density), (int)(4f * density));
        shapeDrawable.getPaint().setColor(background);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            viewBinding.instructionTextview.setBackgroundDrawable(shapeDrawable);
        } else {
            viewBinding.instructionTextview.setBackground(shapeDrawable);
        }
        viewBinding.instructionTextview.setTextColor(text);
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
        if (imageProcessingExecutor.getActiveCount() == 0 && imageProcessingExecutor.getQueue().isEmpty()) {
            int rotation = textureView.getDisplay().getRotation();
            int surfaceRotationDegrees;
            switch (rotation) {
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
            Bitmap bitmap;
            if (sensorOrientation % 180 == 0) {
                bitmap = textureView.getBitmap(previewSize.getWidth(), previewSize.getHeight());
            } else {
                bitmap = textureView.getBitmap(previewSize.getHeight(), previewSize.getWidth());
            }
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

    protected void onError(Exception e) {
        if (listener != null) {
            listener.onError(e);
        }
    }

    /**
     * Indicates how to transform an image of the given size to fit to the fragment view.
     * @param size Image size
     * @return Transformation matrix
     * @since 1.0.0
     */
    public Matrix imageScaleTransformAtImageSize(com.appliedrec.verid.core.Size size) {
        return faceBoundsMatrix;
    }

    private void openCamera(int width, int height) {
        if (cameraId != null) {
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing() || !isAdded()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onError(new Exception("Missing camera permission"));
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            sessionException = new Exception("Camera manager unreachable");
            return;
        }
        try {
            if (!cameraOpenCloseLock.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new TimeoutException("Time out waiting to acquire camera lock.");
            }
            String[] cameras = manager.getCameraIdList();
            cameraId = null;
            int requestedLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
            if (getRequestedFacingOfLens() == VerIDSessionSettings.LensFacing.BACK) {
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
                for (String camId : cameras) {
                    Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                        cameraId = camId;
                        break;
                    }
                }
                if (cameraId == null) {
                    throw new Exception("Camera not available");
                }
            }

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new Exception("Cannot get video sizes");
            }
            int w, h;
            if (sensorOrientation != null && (sensorOrientation - getDisplayRotation()) % 180 == 0) {
                w = width;
                h = height;
            } else {
                w = height;
                h = width;
            }

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), w, h);
            if (shouldRecordVideo()) {
                videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            }
            configureTransform(previewSize.getWidth(), previewSize.getHeight());
            manager.openCamera(cameraId, stateCallback, null);
        } catch (TimeoutException e) {
            onError(e);
        } catch (Exception e) {
            onError(e);
            cameraOpenCloseLock.release();
        }
    }

    protected VerIDSessionSettings.LensFacing getRequestedFacingOfLens() {
        return sessionSettings.getFacingOfCameraLens();
    }

    protected int getNumberOfResultsToCollect() {
        return sessionSettings.getNumberOfResultsToCollect();
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
    protected Size chooseOptimalSize(Size[] choices, int width, int height) {
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


    protected final Comparator<Size> videoSizeComparator = new Comparator<Size>() {

        private boolean is4x3(Size size) {
            return size.getWidth() == size.getHeight() * 4 / 3;
        }
        @Override
        public int compare(Size s1, Size s2) {
            if (is4x3(s1) != is4x3(s2)) {
                return is4x3(s1) ? -1 : 1;
            }
            return Math.abs(s1.getWidth()-640) < Math.abs(s2.getWidth()-640) ? -1 : 1;
        }
    };

    protected Size chooseVideoSize(Size[] choices) {
        ArrayList<Size> sizes = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                sizes.add(size);
            }
        }
        if (sizes.isEmpty()) {
            return choices[choices.length-1];
        }
        Collections.sort(sizes, videoSizeComparator);
        return sizes.get(0);
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (viewBinding != null && null == cameraDevice || !viewBinding.viewFinder.isAvailable() || null == previewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = viewBinding.viewFinder.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            ArrayList<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            previewBuilder.addTarget(previewSurface);
            surfaces.add(previewSurface);

            try {
                setUpMediaRecorder();
                synchronized (mediaRecorderLock) {
                    if (mediaRecorder != null) {
                        Surface recorderSurface = mediaRecorder.getSurface();
                        previewBuilder.addTarget(recorderSurface);
                        surfaces.add(recorderSurface);
                    }
                }
            } catch (Exception ignore) {
            }

            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            updatePreview();
                            synchronized (mediaRecorderLock) {
                                if (shouldRecordVideo() && mediaRecorder != null) {
                                    isRecordingVideo = true;
                                    mediaRecorder.start();
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            onError(new Exception("Failed to start preview"));
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            captureSession.close();
            captureSession = null;
        }
    }

    @MainThread
    private void closeCamera() {
        try {
            if (cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                cameraOpenCloseLock.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
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
        if (viewBinding == null || viewBinding.viewFinder.getDisplay() == null) {
            return;
        }
        RectF viewRect = new RectF(0,0, viewBinding.detectedFaceView.getWidth(), viewBinding.detectedFaceView.getHeight());
        float rotationDegrees = 0;
        try {
            rotationDegrees = (float)getDisplayRotation();
        } catch (Exception ignored) {

        }
        float w, h;
        if ((sensorOrientation - rotationDegrees) % 180 == 0) {
            w = width;
            h = height;
        } else {
            w = height;
            h = width;
        }
        float viewAspectRatio = viewRect.width()/viewRect.height();
        float imageAspectRatio = w/h;
        final float scale;
        if (viewAspectRatio > imageAspectRatio) {
            scale = viewRect.width()/w;
        } else {
            scale = viewRect.height()/h;
        }
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(viewBinding.viewFinder.getLayoutParams());
        Matrix matrix = new Matrix();
        layoutParams.width = (int) (scale * w);
        layoutParams.height = (int) (scale * h);
        layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        viewBinding.viewFinder.setLayoutParams(layoutParams);

        RectF textureRect = new RectF(0, 0, layoutParams.width, layoutParams.height);
        float centerX = textureRect.centerX();
        float centerY = textureRect.centerY();
        if (rotationDegrees != 0) {
            if (rotationDegrees % 180 != 0) {
                matrix.setScale((float) height / (float) width, (float) width / (float) height, centerX, centerY);
            }
            matrix.postRotate(0 - rotationDegrees, centerX, centerY);
        }
        viewBinding.viewFinder.setTransform(matrix);

        // Configure transform for displaying faces
        RectF imageRect = new RectF();
        imageRect.right = w;
        imageRect.bottom = h;
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

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity || !shouldRecordVideo() || videoFile == null) {
            return;
        }
        synchronized (mediaRecorderLock) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(videoFile.getPath());
            mediaRecorder.setVideoEncodingBitRate(1000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            int rotationDegrees = 0;
            try {
                rotationDegrees = getDisplayRotation();
            } catch (Exception ignored) {

            }
            mediaRecorder.setOrientationHint(sensorOrientation - rotationDegrees);
            mediaRecorder.prepare();
        }
    }

    private boolean shouldRecordVideo() {
        return sessionSettings.shouldRecordSessionVideo();
    }

    private void stopRecordingVideo() {
        synchronized (mediaRecorderLock) {
            if (mediaRecorder != null) {
                if (isRecordingVideo) {
                    mediaRecorder.stop();
                }
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            isRecordingVideo = false;
        }
    }

    @Override
    public File getVideoFile() {
        stopRecordingVideo();
        return this.videoFile;
    }

    @Override
    public void onFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        if (viewBinding == null) {
            return;
        }
        @Nullable String labelText;
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        boolean showArrow;
        switch (faceDetectionResult.getStatus()) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                labelText = getTranslatedString("Great, hold it");
                ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : faceDetectionResult.getDefaultFaceBounds();
                cutoutBounds = null;
                faceAngle = null;
                showArrow = false;
                break;
            case FACE_MISALIGNED:
                labelText = getTranslatedString("Slowly turn to follow the arrow");
                ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : faceDetectionResult.getDefaultFaceBounds();
                cutoutBounds = null;
                faceAngle = faceDetectionResult.getFaceAngle();
                showArrow = true;
                break;
            case FACE_TURNED_TOO_FAR:
                labelText = null;
                ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : faceDetectionResult.getDefaultFaceBounds();
                cutoutBounds = null;
                faceAngle = null;
                showArrow = false;
                break;
            default:
                labelText = getTranslatedString("Align your face with the oval");
                ovalBounds = faceDetectionResult.getDefaultFaceBounds();
                cutoutBounds = faceDetectionResult.getFaceBounds();
                faceAngle = null;
                showArrow = false;
        }
        try {
            faceBoundsMatrix.mapRect(ovalBounds);
            if (cutoutBounds != null) {
                faceBoundsMatrix.mapRect(cutoutBounds);
            }
            int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), null);
            int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), null);
            viewBinding.instructionTextview.setText(labelText);
            viewBinding.instructionTextview.setTextColor(textColour);
            viewBinding.instructionTextview.setBackgroundColor(colour);
            viewBinding.instructionTextview.setVisibility(labelText != null ? View.VISIBLE : View.GONE);

            ((ConstraintLayout.LayoutParams) viewBinding.instructionTextview.getLayoutParams()).topMargin = (int) (ovalBounds.top - viewBinding.instructionTextview.getHeight() - getResources().getDisplayMetrics().density * 16f);
            setTextViewColour(colour, textColour);
            Double angle = null;
            Double distance = null;
            if (faceAngle != null && faceDetectionResult.getOffsetAngleFromBearing() != null && showArrow) {
                angle = Math.atan2(faceDetectionResult.getOffsetAngleFromBearing().getPitch(), faceDetectionResult.getOffsetAngleFromBearing().getYaw());
                distance = Math.hypot(faceDetectionResult.getOffsetAngleFromBearing().getYaw(), 0 - faceDetectionResult.getOffsetAngleFromBearing().getPitch()) * 2;
            }
            int backgroundColour = 0x80000000;
            viewBinding.detectedFaceView.setVisibility(View.VISIBLE);
            viewBinding.detectedFaceView.setFaceRect(ovalBounds, cutoutBounds, colour, backgroundColour, angle, distance);
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
            onError(e);
        }
    }

    @Override
    public void onFaceCapture(FaceCapture<Face> faceCapture) {

    }
}
