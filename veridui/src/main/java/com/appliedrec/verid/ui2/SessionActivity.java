package com.appliedrec.verid.ui2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.EulerAngle;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.core2.session.IImageFlowable;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.ui2.databinding.ActivitySessionBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SessionActivity extends AppCompatActivity implements ISessionActivity {

    public static final String EXTRA_SESSION_ID = "com.appliedrec.verid.EXTRA_SESSION_ID";
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private ThreadPoolExecutor imageProcessingExecutor;
    private ExecutorService backgroundExecutor;
    private final Matrix faceBoundsMatrix = new Matrix();
    private VerIDSessionSettings sessionSettings;
    private CameraLens cameraLens = CameraLens.FACING_FRONT;
    private PreviewView viewFinder;
    private ActivitySessionBinding viewBinding;
    private ArrayList<Bitmap> faceImages = new ArrayList<>();
    private final VerIDImageAnalyzer imageAnalyzer = new VerIDImageAnalyzer();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivitySessionBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        viewFinder = viewBinding.viewFinder;
        if (savedInstanceState != null) {
            faceImages = savedInstanceState.getParcelableArrayList("faceImages");
            if (faceImages == null) {
                faceImages = new ArrayList<>();
            }
        }
        imageProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Ver-ID image processing");
            return thread;
        });
        backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Ver-ID session background");
            return thread;
        });
        drawFaces();
        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
        viewFinder = null;
        faceImages.clear();
        if (imageProcessingExecutor != null) {
            imageProcessingExecutor.shutdown();
            imageProcessingExecutor = null;
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
            backgroundExecutor = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("faceImages", faceImages);
    }

    public void setSessionSettings(VerIDSessionSettings sessionSettings, CameraLens cameraLens) {
        this.sessionSettings = sessionSettings;
        this.cameraLens = cameraLens;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (hasCameraPermission()) {
                startCamera();
            } else {
                Toast.makeText(this, "Failed to obtain camera permission", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> processCameraFuture = ProcessCameraProvider.getInstance(this);
        processCameraFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = processCameraFuture.get();
                Preview cameraPreview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                int lensFacing = CameraSelector.LENS_FACING_FRONT;
                if (cameraLens == CameraLens.FACING_BACK) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                }
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();
                cameraProvider.unbindAll();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                imageAnalysis.setAnalyzer(imageProcessingExecutor, imageAnalyzer);
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageAnalysis);
                imageAnalyzer.setExifOrientation(getExifOrientationFromCamera(camera, cameraPreview));
                viewFinder.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
                cameraPreview.setSurfaceProvider(viewFinder.createSurfaceProvider());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public IImageFlowable getImageFlowable() {
        return imageAnalyzer;
    }

    public void setFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        runOnUiThread(() -> displayFaceDetectionResult(faceDetectionResult));
    }

    public void setLabelText(String labelText) {
        runOnUiThread(() -> {
            if (viewBinding == null) {
                return;
            }
            viewBinding.instructionTextview.setText(labelText);
            viewBinding.instructionTextview.setVisibility(labelText != null ? View.VISIBLE : View.GONE);
        });
    }

    private @VerIDImageAnalyzer.ExifOrientation int getExifOrientationFromCamera(Camera camera, Preview cameraPreview) {
        int rotationDegrees = camera.getCameraInfo().getSensorRotationDegrees(cameraPreview.getTargetRotation());
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
        if (cameraLens == CameraLens.FACING_FRONT) {
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    exifOrientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    exifOrientation = ExifInterface.ORIENTATION_TRANSVERSE;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    exifOrientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    exifOrientation = ExifInterface.ORIENTATION_TRANSPOSE;
                    break;
            }
        }
        return exifOrientation;
    }

    private void displayFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        int backgroundColour = 0x80000000;
        if (faceDetectionResult == null) {
            viewBinding.detectedFaceView.setFaceRect(null, null, Color.WHITE, backgroundColour, 0.0, 0.0);
            setLabelText(null);
            return;
        }
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        RectF defaultFaceBounds = sessionSettings.getDefaultFaceBounds(faceDetectionResult.getImageSize());
//        if (sessionSettings != null && sessionResult.getAttachments().length >= sessionSettings.getNumberOfResultsToCollect()) {
//            ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
//            cutoutBounds = new RectF(ovalBounds);
//            faceAngle = null;
//        } else {
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
//        }
        try {
            float scale = Math.max((float)viewFinder.getWidth() / (float)faceDetectionResult.getImageSize().width, (float)viewFinder.getHeight() / (float)faceDetectionResult.getImageSize().height);
            faceBoundsMatrix.reset();
            faceBoundsMatrix.setScale(scale, scale);
            faceBoundsMatrix.postTranslate((float)viewFinder.getWidth() / 2f - (float)faceDetectionResult.getImageSize().width * scale / 2f, (float)viewFinder.getHeight() / 2f - (float)faceDetectionResult.getImageSize().height * scale / 2f);

            faceBoundsMatrix.mapRect(ovalBounds);
            faceBoundsMatrix.mapRect(cutoutBounds);
            int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
            int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
            viewBinding.instructionTextview.setTextColor(textColour);
            viewBinding.instructionTextview.setBackgroundColor(colour);

            ((ConstraintLayout.LayoutParams)viewBinding.instructionTextview.getLayoutParams()).topMargin = (int) (ovalBounds.top - viewBinding.instructionTextview.getHeight() - getResources().getDisplayMetrics().density * 16f);
            setTextViewColour(colour, textColour);
            Double angle = null;
            Double distance = null;
            EulerAngle offsetAngleFromBearing = faceDetectionResult.getOffsetAngleFromBearing();
            if (faceAngle != null && offsetAngleFromBearing != null) {
                angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
            }
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
            e.printStackTrace();
        }
    }

    /**
     * Get the colour of the oval drawn around the face and of the background of the instruction text label. The colour should reflect the supplied state of the face detection.
     * @param faceDetectionStatus Face detection status
     * @return Integer representing a colour in ARGB space
     * @since 1.6.0
     */
    public int getOvalColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus) {
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
    public int getTextColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus) {
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
        viewBinding.instructionTextview.setBackground(shapeDrawable);
        viewBinding.instructionTextview.setTextColor(text);
    }

    @Override
    public void accept(FaceCapture faceCapture) {
        if (sessionSettings instanceof RegistrationSessionSettings) {
            runOnUiThread(() -> {
                float screenDensity = getResources().getDisplayMetrics().density;
                executeInBackground(() -> {
                    float targetHeightDp = 96f;
                    float scale = targetHeightDp / (float) faceCapture.getFaceImage().getHeight() * screenDensity;
                    Bitmap bitmap = Bitmap.createScaledBitmap(faceCapture.getFaceImage(), Math.round((float) faceCapture.getFaceImage().getWidth() * scale), Math.round((float) faceCapture.getFaceImage().getHeight() * scale), true);
                    if (cameraLens == CameraLens.FACING_FRONT) {
                        Matrix matrix = new Matrix();
                        matrix.setScale(-1, 1);
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    }
                    faceImages.add(bitmap);
                    runOnUiThread(this::drawFaces);
                });
            });
        }
    }

    @DrawableRes
    private int placeholderImageForBearing(Bearing bearing) {
        switch (bearing) {
            case STRAIGHT:
                return R.mipmap.head_straight;
            case LEFT:
                return R.mipmap.head_left;
            case RIGHT:
                return R.mipmap.head_right;
            case UP:
                return R.mipmap.head_up;
            case DOWN:
                return R.mipmap.head_down;
            case LEFT_UP:
                return R.mipmap.head_left_up;
            case RIGHT_UP:
                return R.mipmap.head_right_up;
            case LEFT_DOWN:
                return R.mipmap.head_left_down;
            case RIGHT_DOWN:
                return R.mipmap.head_right_down;
            default:
                return R.mipmap.head_straight;
        }
    }

    @MainThread
    private void drawFaces() {
        if (isDestroyed()) {
            return;
        }
        viewBinding.faceImages.removeAllViews();
        if (sessionSettings instanceof RegistrationSessionSettings) {
            float screenDensity = getResources().getDisplayMetrics().density;
            int height = Math.round(screenDensity * 96f);
            int margin = Math.round(screenDensity * 8f);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            Bearing[] bearingsToRegister = ((RegistrationSessionSettings) sessionSettings).getBearingsToRegister();
            executeInBackground(() -> {
                ArrayList<RoundedBitmapDrawable> bitmapDrawables = new ArrayList<>();
                for (int i = 0; i<sessionSettings.getNumberOfFacesToCapture(); i++) {
                    RoundedBitmapDrawable drawable;
                    if (i < faceImages.size()) {
                        Bitmap bitmap = faceImages.get(i);
                        drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                    } else {
                        int bearingIndex = i % bearingsToRegister.length;
                        Bearing bearing = bearingsToRegister[bearingIndex];
                        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), placeholderImageForBearing(bearing));
                        if (cameraLens == CameraLens.FACING_FRONT) {
                            Matrix matrix = new Matrix();
                            matrix.setScale(-1, 1);
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                        }
                        drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                    }
                    drawable.setCornerRadius(height / 6f);
                    bitmapDrawables.add(drawable);
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    for (RoundedBitmapDrawable drawable : bitmapDrawables) {
                        ImageView imageView = new ImageView(this);
                        imageView.setScaleType(ImageView.ScaleType.CENTER);
                        imageView.setImageDrawable(drawable);
                        layoutParams.leftMargin = margin;
                        layoutParams.rightMargin = margin;
                        viewBinding.faceImages.addView(imageView, layoutParams);
                    }
                });
            });
        }
    }

    private void executeInBackground(Runnable runnable) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(runnable);
        }
    }
}
