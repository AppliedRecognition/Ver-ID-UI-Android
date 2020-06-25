package com.appliedrec.verid.ui2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.exifinterface.media.ExifInterface;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.IImageFlowable;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.ui2.databinding.ActivitySessionBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
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
    private VerIDSessionSettings sessionSettings;
    private CameraLens cameraLens = CameraLens.FACING_FRONT;
    private VerIDSessionFragment sessionFragment;
    private ActivitySessionBinding viewBinding;
    private ArrayList<Bitmap> faceImages = new ArrayList<>();
    private final VerIDImageAnalyzer imageAnalyzer = new VerIDImageAnalyzer();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivitySessionBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        sessionFragment = (VerIDSessionFragment) getSupportFragmentManager().findFragmentById(R.id.session_fragment);
        faceImages = new ArrayList<>();
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
        sessionFragment = null;
        sessionSettings = null;
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
                if (sessionFragment != null) {
                    sessionFragment.getViewFinder().ifPresent(viewFinder -> {
                        viewFinder.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
                        cameraPreview.setSurfaceProvider(viewFinder.createSurfaceProvider());
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public IImageFlowable getImageFlowable() {
        return imageAnalyzer;
    }

    public void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt) {
        runOnUiThread(() -> {
            if (sessionFragment != null) {
                sessionFragment.accept(faceDetectionResult, prompt);
            }
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
        if (viewBinding == null) {
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
                    if (viewBinding == null) {
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
