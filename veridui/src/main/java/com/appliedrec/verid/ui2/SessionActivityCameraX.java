package com.appliedrec.verid.ui2;

import android.Manifest;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.AllocationAdapter;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
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
//                Preview previewAnalysis = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
//                previewAnalysis.setSurfaceProvider(imageProcessingExecutor, new Preview.SurfaceProvider() {
//                    @Override
//                    public void onSurfaceRequested(@NonNull SurfaceRequest request) {
//                        RenderScript rs = RenderScript.create(SessionActivityCameraX.this);
//                        Allocation allocation = Allocation.createTyped(rs, Type.createXY(rs, Element.YUV(rs), 640, 480), Allocation.USAGE_IO_INPUT);
//                        request.provideSurface(allocation.getSurface(), imageProcessingExecutor, result -> {
//                            System.out.println(result.getResultCode()+"");
//                        });
//                    }
//                });
                int lensFacing = CameraSelector.LENS_FACING_FRONT;
                if (getCameraLocation() == CameraLocation.BACK) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                }
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();
                cameraProvider.unbindAll();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                imageAnalysis.setAnalyzer(imageProcessingExecutor, getImageAnalyzer());
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageAnalysis);
                int rotation = camera.getCameraInfo().getSensorRotationDegrees(cameraPreview.getTargetRotation());
                getImageAnalyzer().setExifOrientation(getExifOrientation(rotation));
                getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(viewFinder -> {
                    viewFinder.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
                    cameraPreview.setSurfaceProvider(viewFinder.createSurfaceProvider());
                });
            } catch (Exception e) {
                getImageAnalyzer().fail(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
