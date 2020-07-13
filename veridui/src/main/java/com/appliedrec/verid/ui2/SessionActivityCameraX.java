package com.appliedrec.verid.ui2;

import android.Manifest;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.appliedrec.verid.ui2.databinding.ActivitySessionCameraxBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SessionActivityCameraX extends AbstractSessionActivity<VerIDSessionFragmentCameraX> {

    public static final String EXTRA_SESSION_ID = "com.appliedrec.verid.EXTRA_SESSION_ID";
    private ThreadPoolExecutor imageProcessingExecutor;
    private VerIDSessionFragmentCameraX sessionFragment;
    private ActivitySessionCameraxBinding viewBinding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivitySessionCameraxBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        sessionFragment = (VerIDSessionFragmentCameraX) getSupportFragmentManager().findFragmentById(R.id.session_fragment);
        imageProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Ver-ID image processing");
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
        if (imageProcessingExecutor != null) {
            imageProcessingExecutor.shutdown();
            imageProcessingExecutor = null;
        }
    }

    @Override
    protected Optional<VerIDSessionFragmentCameraX> getSessionFragment() {
        return Optional.ofNullable(sessionFragment);
    }

    @Override
    protected Optional<LinearLayout> getFaceImagesView() {
        return Optional.empty();
    }

    @Override
    protected void startCamera() {
        ListenableFuture<ProcessCameraProvider> processCameraFuture = ProcessCameraProvider.getInstance(this);
        processCameraFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = processCameraFuture.get();
                Preview cameraPreview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                int lensFacing = CameraSelector.LENS_FACING_FRONT;
                if (getCameraLocation() == CameraLocation.BACK) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                }
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();
                cameraProvider.unbindAll();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                imageAnalysis.setAnalyzer(imageProcessingExecutor, getImageAnalyzer());
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageAnalysis);
                int rotation = camera.getCameraInfo().getSensorRotationDegrees(cameraPreview.getTargetRotation());
                getImageAnalyzer().setExifOrientation(getExifOrientation(rotation));
                if (sessionFragment != null) {
                    sessionFragment.getViewFinder().ifPresent(viewFinder -> {
                        viewFinder.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
                        cameraPreview.setSurfaceProvider(viewFinder.createSurfaceProvider());
                    });
                }
            } catch (Exception e) {
                getImageAnalyzer().fail(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
