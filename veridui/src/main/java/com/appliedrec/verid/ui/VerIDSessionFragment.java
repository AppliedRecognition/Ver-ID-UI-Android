package com.appliedrec.verid.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.media.ExifInterface;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.core.Size;
import com.appliedrec.verid.core.VerIDImage;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VerIDSessionFragment extends Fragment implements IVerIDSessionFragment, ICameraPreviewView.CameraPreviewViewListener, Camera.PreviewCallback {

    private ICameraPreviewView cameraSurfaceView;
    private TransformableRelativeLayout cameraOverlaysView;

    public TransformableRelativeLayout getViewOverlays() {
        return viewOverlays;
    }

    private TransformableRelativeLayout viewOverlays;
    private DetectedFaceView detectedFaceView;
    private ThreadPoolExecutor previewProcessingExecutor;
    private VerIDSessionFragmentDelegate delegate;
    private IStringTranslator stringTranslator;
    private int cameraOrientation = 0;
    private int deviceOrientation = 0;
    private Camera.Size previewSize;
    private int previewFormat;
    private int exifOrientation = ExifInterface.ORIENTATION_NORMAL;
    private Camera camera;
    private VerIDImage currentImage;

    protected TextView instructionTextView;
    protected View instructionView;

    private static final int IMAGE_FORMAT_CERIDIAN_NV12 = 0x103;
    private int backgroundColour = 0x80000000;

    //region Fragment lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        TransformableRelativeLayout view = new TransformableRelativeLayout(container.getContext());
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        view.setLayoutParams(layoutParams);
        view.setBackgroundResource(android.R.color.black);

        viewOverlays = new TransformableRelativeLayout(getActivity());
        layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        viewOverlays.setLayoutParams(layoutParams);
        view.addView(viewOverlays);

        detectedFaceView = (DetectedFaceView) inflater.inflate(R.layout.detected_face_view, null, false);
        viewOverlays.addTransformableView(detectedFaceView);
        inflater.inflate(R.layout.verid_authentication_fragment, viewOverlays, true);
        instructionView = viewOverlays.findViewById(R.id.instruction);
        instructionView.setVisibility(View.GONE);
        instructionTextView = viewOverlays.findViewById(R.id.instruction_textview);
        instructionTextView.setText(getTranslatedString("Preparing face detection"));
        setTextViewColour(getOvalColourFromFaceDetectionStatus(FaceDetectionStatus.STARTED, null), getTextColourFromFaceDetectionStatus(FaceDetectionStatus.STARTED, null));
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof VerIDSessionFragmentDelegate) {
            delegate = (VerIDSessionFragmentDelegate)context;
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

    @Override
    public void onPause() {
        super.onPause();
        releaseCamera();
    }

    //endregion

    //region Camera preview listener

    @Override
    public void onCameraPreviewStarted(Camera camera) {

    }

    //endregion

    protected final void runOnUIThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    //region Camera

    protected ICameraPreviewView createCameraView() {
        if (getCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return new CameraTextureView(getActivity(), null);
        } else {
            return new CameraSurfaceView(getActivity(), null);
        }
    }

    @UiThread
    private void addCameraView() {
        removeCameraView();
        ViewGroup view = (ViewGroup) getView();
        if (view == null) {
            return;
        }
        cameraSurfaceView = createCameraView();
        cameraSurfaceView.setId(2);
        cameraSurfaceView.setListener(this);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        cameraSurfaceView.setLayoutParams(layoutParams);
        view.addView((View)cameraSurfaceView, 0);

        cameraOverlaysView = new TransformableRelativeLayout(getActivity());
        view.addView(cameraOverlaysView, 1);
        layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.ALIGN_LEFT, cameraSurfaceView.getId());
        layoutParams.addRule(RelativeLayout.ALIGN_TOP, cameraSurfaceView.getId());
        layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, cameraSurfaceView.getId());
        layoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, cameraSurfaceView.getId());
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        cameraOverlaysView.setLayoutParams(layoutParams);
    }

    @UiThread
    private void removeCameraView() {
        if (cameraSurfaceView != null && ((View)cameraSurfaceView).getParent() != null) {
            ((ViewGroup)((View)cameraSurfaceView).getParent()).removeView(((View)cameraSurfaceView));
            cameraSurfaceView = null;
        }
        if (cameraOverlaysView != null && cameraOverlaysView.getParent() != null) {
            ((ViewGroup)cameraOverlaysView.getParent()).removeView(cameraOverlaysView);
        }
    }

    protected void setupCamera() {
        final Activity activity = getActivity();
        if (activity == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) || activity.isFinishing() || camera == null) {
            return;
        }
        final Point displaySize = new Point(getView().getWidth(), getView().getHeight());
        final Display display = ((WindowManager) activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        final int rotation = display.getRotation();
        previewProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (camera == null) {
                    return;
                }
                final Camera.Parameters params = camera.getParameters();

                deviceOrientation = 0;
                int rotationDegrees = 0;
                switch (rotation) {
                    case Surface.ROTATION_0:
                        rotationDegrees = 0;
                        break;
                    case Surface.ROTATION_90:
                        rotationDegrees = 90;
                        break;
                    case Surface.ROTATION_180:
                        rotationDegrees = 180;
                        break;
                    case Surface.ROTATION_270:
                        rotationDegrees = 270;
                        break;
                }

                // From Android sample code
                int orientation = (rotationDegrees + 45) / 90 * 90;
                int cameraRotation;

                int orientationDegrees;
                if (getDelegate() != null && getDelegate().getSessionSettings().getFacingOfCameraLens() == VerIDSessionSettings.LensFacing.BACK) {
                    deviceOrientation = (cameraOrientation - rotationDegrees + 360) % 360;
                    orientationDegrees = (cameraOrientation - rotationDegrees + 360) % 360;
                    cameraRotation = (cameraOrientation + orientation) % 360;
                } else {
                    deviceOrientation = (cameraOrientation + rotationDegrees) % 360;
                    deviceOrientation = (360 - deviceOrientation) % 360;
                    orientationDegrees = (cameraOrientation + rotationDegrees) % 360;
                    cameraRotation = (cameraOrientation - orientation + 360) % 360;
                }

                switch (orientationDegrees) {
                    case 90:
                        exifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
                        break;
                    case 180:
                        exifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
                        break;
                    case 270:
                        exifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
                        break;
                    default:
                        exifOrientation = ExifInterface.ORIENTATION_NORMAL;
                }


                final Point adjustedDisplaySize;
                if (cameraRotation % 180 > 0) {
                    adjustedDisplaySize = new Point(displaySize.y, displaySize.x);
                } else {
                    adjustedDisplaySize = new Point(displaySize);
                }
                float screenDensity = getResources().getDisplayMetrics().density;
                adjustedDisplaySize.x /= screenDensity;
                adjustedDisplaySize.y /= screenDensity;
                previewSize = getOptimalCameraSizeForDimensions(params.getSupportedPreviewSizes(), adjustedDisplaySize.x, adjustedDisplaySize.y);
                String previewFormats = params.get("preview-format-values");
                if (previewFormats!=null && previewFormats.contains("fslNV21isNV12")) {
                    previewFormat = IMAGE_FORMAT_CERIDIAN_NV12;
                } else {
                    previewFormat = params.getPreviewFormat();
                }

                final Camera.Size scaledSize;
                if (deviceOrientation % 180 > 0) {
                    scaledSize = camera.new Size(previewSize.height, previewSize.width);
                } else {
                    scaledSize = camera.new Size(previewSize.width, previewSize.height);
                }
                List<String> supportedFocusModes = params.getSupportedFocusModes();
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
                params.setPreviewSize(previewSize.width, previewSize.height);
                // Some camera drivers write the rotation to exif but some apply it to the raw jpeg data.
                // If the camera rotation is set to 0 then we can use the exif orientation to right the image reliably.
                params.setRotation(0);
                updateCameraParams(params);
                Log.d("CameraFocus", "setFocusMode "+params.getFocusMode());
                camera.setParameters(params);
                camera.setDisplayOrientation(deviceOrientation);

                setPreviewCallbackWithBuffer();

                float scale;
                if ((float)displaySize.x / (float)displaySize.y > (float)scaledSize.width / (float)scaledSize.height) {
                    scale = (float)displaySize.x / (float)scaledSize.width;
                } else {
                    scale = (float)displaySize.y / (float)scaledSize.height;
                }
                scaledSize.width *= scale;
                scaledSize.height *= scale;
                runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (camera == null || cameraSurfaceView == null) {
                            return;
                        }
                        cameraSurfaceView.setCamera(camera);
                        cameraSurfaceView.setFixedSize(scaledSize.width, scaledSize.height);
                    }
                });
            }
        });
    }

    protected Camera openCamera() {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == getCameraId()) {
                return Camera.open(i);
            }
        }
        return null;
    }

    protected int getOrientationOfCamera() {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == getCameraId()) {
                return info.orientation;
            }
        }
        return 0;
    }

    protected int getCameraId() {
        if (getDelegate() != null && getDelegate().getSessionSettings().getFacingOfCameraLens() == VerIDSessionSettings.LensFacing.BACK) {
            return Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        return Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    private Camera.Size getOptimalCameraSizeForDimensions(List<Camera.Size> supportedSizes, final int desiredWidth, final int desiredHeight) {

        final int desiredSizeArea = desiredWidth * desiredHeight;
        final float desiredSizeAspectRatio = (float)desiredWidth/(float)desiredHeight;
        final boolean desiredSizeIsLandscape = desiredSizeAspectRatio > 1;

        Comparator<Camera.Size> comparator = new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size size1, Camera.Size size2) {
                float size1AspectRatio = (float)size1.width/(float)size1.height;
                float size2AspectRatio = (float)size2.width/(float)size2.height;
                boolean size1IsLandscape = size1AspectRatio > 1;
                boolean size2IsLandscape = size2AspectRatio > 1;
                if (size1IsLandscape == size2IsLandscape) {
                    int size1Area = size1.width * size1.height;
                    int size2Area = size2.width * size2.height;
                    float size1AreaDiff = Math.abs(size1Area - desiredSizeArea);
                    float size2AreaDiff = Math.abs(size2Area - desiredSizeArea);
                    if (size1AreaDiff == size2AreaDiff) {
                        float size1AspectRatioDiff = Math.abs(size1AspectRatio - desiredSizeAspectRatio);
                        float size2AspectRatioDiff = Math.abs(size2AspectRatio - desiredSizeAspectRatio);
                        if (size2AspectRatioDiff == size2AspectRatioDiff) {
                            return 0;
                        }
                        return size1AspectRatioDiff < size2AspectRatioDiff ? -1 : 1;
                    }
                    return size1AreaDiff < size2AreaDiff ? -1 : 1;
                } else {
                    return size1IsLandscape == desiredSizeIsLandscape ? -1 : 1;
                }
            }
        };

        Collections.sort(supportedSizes, comparator);

        return supportedSizes.get(0);
    }

    // Override if you need to update camera parameters while the camera is being initialized
    protected void updateCameraParams(Camera.Parameters params) {
    }


    protected void setPreviewCallbackWithBuffer() {
        final int bufferLength;
        if (previewFormat == IMAGE_FORMAT_CERIDIAN_NV12) {
            bufferLength = (previewSize.width * previewSize.height * 12 + 7) / 8;
        } else {
            bufferLength = previewSize.width * previewSize.height * ImageFormat.getBitsPerPixel(previewFormat) / 8;
        }
        camera.addCallbackBuffer(new byte[bufferLength]);
        camera.setPreviewCallbackWithBuffer(VerIDSessionFragment.this);
    }

    protected final void releaseCamera() {
        if (previewProcessingExecutor == null) {
            return;
        }
        previewProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (camera != null) {
                    camera.stopPreview();
                    camera.release();
                    camera = null;
                }
                runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cameraSurfaceView != null) {
                            cameraSurfaceView.setCamera(null);
                        }
                    }
                });
            }
        });
        previewProcessingExecutor = null;
    }

    //endregion

    /**
     * Indicates how to transform an image of the given size to fit to the fragment view.
     * @param size Image size
     * @return Transformation matrix
     * @since 1.0.0
     */
    public Matrix imageScaleTransformAtImageSize(Size size) {
        float width = (float)viewOverlays.getWidth();
        float height = (float)viewOverlays.getHeight();
        float viewAspectRatio = width / height;
        float imageAspectRatio = (float)size.width / (float)size.height;
        RectF rect = new RectF();
        if (imageAspectRatio > viewAspectRatio) {
            rect.bottom = size.height;
            float w = size.height * viewAspectRatio;
            rect.left = size.width / 2 - w / 2;
            rect.right = size.width / 2 + w / 2;
        } else {
            rect.right = size.width;
            float h = size.width / viewAspectRatio;
            rect.top = size.height / 2 - h / 2;
            rect.bottom = size.height / 2 + h / 2;
        }
        float scale = width / rect.width();
        Matrix matrix = new Matrix();
        matrix.setTranslate(0-rect.left, 0-rect.top);
        matrix.postScale(scale, scale);
        return matrix;
    }

    protected VerIDSessionFragmentDelegate getDelegate() {
        return delegate;
    }

    //region Ver-ID session fragment interface

    @UiThread
    public void startCamera() {
        addCameraView();
        if (previewProcessingExecutor == null || previewProcessingExecutor.isShutdown()) {
            previewProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        previewProcessingExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraOrientation = getOrientationOfCamera();
                    if (camera == null) {
                        camera = openCamera();
                    }
                    if (camera == null) {
                        throw new Exception("Unable to access camera");
                    }
                    runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            setupCamera();
                        }
                    });
                } catch (final Exception e) {
                    runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (delegate != null) {
                                delegate.veridSessionFragmentDidFailWithError(VerIDSessionFragment.this, e);
                            }
                        }
                    });
                }
            }
        });
    }

    @UiThread
    @Override
    public void clearCameraPreview() {
        removeCameraView();
    }

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, EulerAngle offsetAngleFromBearing) {
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
        Matrix matrix = imageScaleTransformAtImageSize(faceDetectionResult.getImageSize());
        matrix.mapRect(ovalBounds);
        if (cutoutBounds != null) {
            matrix.mapRect(cutoutBounds);
        }
        int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), sessionResult.getError());
        int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus(), sessionResult.getError());
        instructionTextView.setText(labelText);
        instructionTextView.setTextColor(textColour);
        instructionTextView.setBackgroundColor(colour);
        instructionView.setVisibility(labelText != null ? View.VISIBLE : View.GONE);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(instructionView.getLayoutParams());
        params.topMargin = (int)(ovalBounds.top - instructionView.getHeight() - getResources().getDisplayMetrics().density * 16f);
        instructionView.setLayoutParams(params);
        setTextViewColour(colour, textColour);
        Double angle = null;
        Double distance = null;
        if (faceAngle != null && offsetAngleFromBearing != null && showArrow) {
            angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
            distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0-offsetAngleFromBearing.getPitch()) * 2;
        }
        detectedFaceView.setFaceRect(ovalBounds, cutoutBounds, colour, backgroundColour, angle, distance);
    }

    @Override
    public void clearCameraOverlay() {
        instructionView.setVisibility(View.GONE);
        detectedFaceView.setFaceRect(null, null, getOvalColourFromFaceDetectionStatus(FaceDetectionStatus.STARTED, null), backgroundColour, null, null);
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
    public VerIDImage dequeueImage() throws Exception {
        synchronized (this) {
            while (currentImage == null) {
                this.wait();
            }
            VerIDImage image = currentImage;
            currentImage = null;
            return image;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        synchronized (this) {
            if (currentImage == null) {
                YuvImage image = new YuvImage(data, previewFormat, previewSize.width, previewSize.height, null);
                currentImage = new VerIDImage(image, exifOrientation);
                camera.addCallbackBuffer(data);
                notify();
            } else {
                camera.addCallbackBuffer(data);
            }
        }
    }

    private String getTranslatedString(String original, Object ...args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        } else {
            return String.format(original, args);
        }
    }
}
