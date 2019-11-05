package com.appliedrec.verid.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.Size;
import com.appliedrec.verid.core.VerIDImage;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VerIDSessionFragmentCamerax extends Fragment implements IVerIDSessionFragment {

    private SynchronousQueue<VerIDImage> imageQueue = new SynchronousQueue<>();
    private TextureView viewFinder;
    protected TextView instructionTextView;
    private DetectedFaceView detectedFaceView;
    private ExecutorService imageAnalysisExecutor = Executors.newSingleThreadExecutor();
    private ThreadPoolExecutor previewExecutor = new ThreadPoolExecutor(0, 1, 120, TimeUnit.MINUTES, new LinkedBlockingQueue<>(1));
    private VerIDSessionFragmentDelegate delegate;
    private IStringTranslator stringTranslator;
    private Long startTime;
    private int frameCount;
    private int backgroundColour = 0x80000000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ConstraintLayout view = (ConstraintLayout) inflater.inflate(R.layout.fragment_session, container, false);
        viewFinder = view.findViewById(R.id.view_finder);
        viewFinder.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            updateTransform();
        });
        instructionTextView = view.findViewById(R.id.instruction_textview);
        detectedFaceView = view.findViewById(R.id.detectedFaceView);
        instructionTextView.setVisibility(View.GONE);
        return view;
    }

    private void updateTransform() {
        if (getView() == null || getView().getWidth() == 0 || getView().getHeight() == 0) {
            return;
        }
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(viewFinder.getLayoutParams());
        float viewAspectRatio = (float)getView().getWidth() / (float)getView().getHeight();
        float cameraAspectRatio;
        if (viewAspectRatio > 1) {
            cameraAspectRatio = 4f / 3f;
        } else {
            cameraAspectRatio = 3f / 4f;
        }
        if (cameraAspectRatio > viewAspectRatio) {
            layoutParams.width = getView().getWidth();
            layoutParams.height = (int)((float)getView().getWidth() * cameraAspectRatio);
        } else {
            layoutParams.height = getView().getHeight();
            layoutParams.width = (int)((float)getView().getHeight() * cameraAspectRatio);
        }
        viewFinder.setLayoutParams(layoutParams);
        Matrix matrix = new Matrix();
        float centerX = (float)viewFinder.getWidth() / 2f;
        float centerY = (float)viewFinder.getHeight() / 2f;

        if (viewFinder.getDisplay() == null) {
            return;
        }
        int rotationDegrees = viewFinder.getDisplay().getRotation();
        matrix.postRotate(0-(float)rotationDegrees, centerX, centerY);
        viewFinder.setTransform(matrix);
    }

    private byte[] yuv420888ToNv21(ImageProxy image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            int yBufferPos = width - rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride - width;
                yBuffer.position(yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            vBuffer.put(1, (byte)0);
            if (uBuffer.get(0) == 0) {
                vBuffer.put(1, (byte)255);
                if (uBuffer.get(0) == 255) {
                    vBuffer.put(1, savePixel);
                    vBuffer.get(nv21, ySize, uvSize);

                    return nv21; // shortcut
                }
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    @Override
    public void startCamera() {
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                .setTargetResolution(new Size(viewFinder.getMeasuredWidth(), viewFinder.getMeasuredHeight()))
                .setLensFacing(CameraX.LensFacing.FRONT)
                .build();
        Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(output -> {
            ViewGroup parent = (ViewGroup) viewFinder.getParent();
            parent.removeView(viewFinder);
            parent.addView(viewFinder, 0);
            viewFinder.setSurfaceTexture(output.getSurfaceTexture());
            updateTransform();
        });

        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig.Builder()
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setLensFacing(CameraX.LensFacing.FRONT)
                .build();
        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(imageAnalysisExecutor, (ImageProxy image, int rotationDegrees) -> {
            long now = System.currentTimeMillis();
            long nowSecs = now / 1000;
            if (startTime == null || startTime != nowSecs) {
                if (startTime != null) {
                    Log.d("VerID", "Preview running at "+frameCount+" FPS");
                }
                startTime = nowSecs;
                frameCount = 1;
            } else {
                frameCount ++;
            }
            if (previewExecutor != null && !previewExecutor.isShutdown() && previewExecutor.getActiveCount() == 0 && previewExecutor.getQueue().isEmpty()) {
//                byte[] nv21 = yuv420888ToNv21(image);
                ByteBuffer grayscaleBuffer = image.getPlanes()[0].getBuffer();
                byte[] grayscale = new byte[grayscaleBuffer.capacity()];
                grayscaleBuffer.get(grayscale);
                int rowStride = image.getPlanes()[0].getRowStride();
                int width = image.getWidth();
                int height = image.getHeight();

                previewExecutor.execute(() -> {
                    int exifOrientation;
                    switch (rotationDegrees) {
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
//                    YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, rowStride, height, null);
//                    VerIDImage verIDImage = new VerIDImage(yuvImage, exifOrientation);
                    VerIDImage verIDImage = new VerIDImage(grayscale, rowStride, height, exifOrientation);
                    try {
                        imageQueue.put(verIDImage);
                    } catch (Exception e) {
                    }
                });
            } else {
                Log.d("VerID", "Skipped frame");
            }
        });

        CameraX.bindToLifecycle(this, preview, imageAnalysis);
    }

    /**
     * Indicates how to transform an image of the given size to fit to the fragment view.
     * @param size Image size
     * @return Transformation matrix
     * @since 1.0.0
     */
    public Matrix imageScaleTransformAtImageSize(Size size) throws Exception {
        if (getView() == null) {
            throw new Exception("View not loaded");
        }
        float width = (float)getView().getWidth();
        float height = (float)getView().getHeight();
        float viewAspectRatio = width / height;
        float imageAspectRatio = (float)size.width / (float)size.height;
        RectF rect = new RectF();
        if (imageAspectRatio > viewAspectRatio) {
            rect.bottom = size.height;
            float w = size.height * viewAspectRatio;
            rect.left = (float)size.width / 2 - w / 2;
            rect.right = (float)size.width / 2 + w / 2;
        } else {
            rect.right = size.width;
            float h = size.width / viewAspectRatio;
            rect.top = (float)size.height / 2 - h / 2;
            rect.bottom = (float)size.height / 2 + h / 2;
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
            instructionTextView.setVisibility(labelText != null ? View.VISIBLE : View.GONE);

            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(instructionTextView.getLayoutParams());
            params.topMargin = (int) (ovalBounds.top - instructionTextView.getHeight() - getResources().getDisplayMetrics().density * 16f);
            instructionTextView.setLayoutParams(params);
            setTextViewColour(colour, textColour);
            Double angle = null;
            Double distance = null;
            if (faceAngle != null && offsetAngleFromBearing != null && showArrow) {
                angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
            }
            detectedFaceView.setFaceRect(ovalBounds, cutoutBounds, colour, backgroundColour, angle, distance);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clearCameraOverlay() {
        instructionTextView.setVisibility(View.GONE);
        detectedFaceView.setFaceRect(null, null, getOvalColourFromFaceDetectionStatus(FaceDetectionStatus.STARTED, null), backgroundColour, null, null);
    }

    @Override
    public void clearCameraPreview() {

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
        return imageQueue.take();
    }

    @Override
    public int getOrientationOfCamera() {
        return 0;
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

    private String getTranslatedString(String original, Object ...args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        } else {
            return String.format(original, args);
        }
    }
}
