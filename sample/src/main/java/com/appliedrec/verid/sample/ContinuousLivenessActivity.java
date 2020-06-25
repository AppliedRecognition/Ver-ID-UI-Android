package com.appliedrec.verid.sample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.Observer;

import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.IFaceTracking;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivityContinuousLivenessBinding;
import com.appliedrec.verid.ui2.SessionPrompts;
import com.appliedrec.verid.ui2.TranslatedStrings;
import com.appliedrec.verid.ui2.VerIDImageAnalyzer;
import com.appliedrec.verid.ui2.VerIDSessionFragment;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ContinuousLivenessActivity extends AppCompatActivity implements IVerIDLoadObserver {

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 0;
    ActivityContinuousLivenessBinding viewBinding;
    private final VerIDImageAnalyzer imageAnalyzer = new VerIDImageAnalyzer();
    private ThreadPoolExecutor imageProcessingExecutor;
    private Session<LivenessDetectionSessionSettings> session;
    private Disposable faceDetectionDisposable;
    private final LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
    private VerIDSessionFragment sessionFragment;
    private VerID verID;
    private SessionPrompts sessionPrompts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityContinuousLivenessBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        sessionPrompts  = new SessionPrompts(new TranslatedStrings(this, null));
        imageProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Ver-ID image processing");
            return thread;
        });
        viewBinding.retryButton.setOnClickListener(view -> startSession());
        viewBinding.idle.setVisibility(View.VISIBLE);
        viewBinding.sessionResult.setVisibility(View.VISIBLE);
        sessionFragment = (VerIDSessionFragment)getSupportFragmentManager().findFragmentById(R.id.sessionFragment);
        session.getFaceDetectionLiveData().observe(this, faceDetectionResult -> {
            sessionFragment.accept(faceDetectionResult, sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null));
        });
        session.getSessionResultLiveData().observe(this, this::onSessionResult);
        runFaceDetectionUntil(hasFace -> hasFace, this::startSession);
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
        sessionPrompts = null;
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        faceDetectionDisposable = null;
        if (session != null) {
            session.cancel();
            session = null;
        }
        if (imageProcessingExecutor != null) {
            imageProcessingExecutor.shutdown();
            imageProcessingExecutor = null;
        }
        sessionFragment = null;
        verID = null;
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

    private Optional<VerIDSessionFragment> getSessionFragment() {
        return Optional.ofNullable(sessionFragment);
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
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();
                cameraProvider.unbindAll();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                imageAnalysis.setAnalyzer(imageProcessingExecutor, imageAnalyzer);
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageAnalysis);
                imageAnalyzer.setExifOrientation(getExifOrientationFromCamera(camera, cameraPreview));
                getSessionFragment().flatMap(VerIDSessionFragment::getViewFinder).ifPresent(viewFinder -> {
                    viewFinder.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
                    cameraPreview.setSurfaceProvider(viewFinder.createSurfaceProvider());
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private @VerIDImageAnalyzer.ExifOrientation int getExifOrientationFromCamera(Camera camera, Preview cameraPreview) {
        int rotationDegrees = camera.getCameraInfo().getSensorRotationDegrees(cameraPreview.getTargetRotation());
        int exifOrientation;
        switch (rotationDegrees) {
            case 90:
                exifOrientation = ExifInterface.ORIENTATION_TRANSVERSE;
                break;
            case 180:
                exifOrientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
                break;
            case 270:
                exifOrientation = ExifInterface.ORIENTATION_TRANSPOSE;
                break;
            default:
                exifOrientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
        }
        return exifOrientation;
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        verID = verid;
        session = new Session.Builder<>(verid, sessionSettings, imageAnalyzer).bindToLifecycle(this.getLifecycle()).build();
    }

    @Override
    public void onVerIDUnloaded() {
        verID = null;
    }

    private void runFaceDetectionUntil(Predicate<Boolean> predicate, Action onComplete) {
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        IFaceTracking faceTracking = verID.getFaceDetection().startFaceTracking();
        faceDetectionDisposable = Flowable.create(imageAnalyzer, BackpressureStrategy.LATEST)
                .map(image -> {
                    Face face = faceTracking.trackFaceInImage(image);
                    return face != null;
                })
                .takeUntil(predicate)
                .ignoreElements()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        onComplete,
                        error -> {

                        }
                );
    }

    private void startSession() {
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        faceDetectionDisposable = null;
        if (viewBinding == null) {
            return;
        }
        viewBinding.sessionResult.setVisibility(View.GONE);
        if (session != null) {
            session.start();
        }
    }

    private void onSessionResult(VerIDSessionResult sessionResult) {
        getSessionFragment().ifPresent(sessionFragment -> {
            sessionFragment.accept(null, null);
        });
        if (viewBinding == null) {
            return;
        }
        viewBinding.sessionResult.setVisibility(View.VISIBLE);
        viewBinding.idle.setVisibility(View.GONE);
        viewBinding.success.setVisibility(sessionResult.getError().isPresent() ? View.GONE : View.VISIBLE);
        viewBinding.failure.setVisibility(sessionResult.getError().isPresent() ? View.VISIBLE : View.GONE);
        runFaceDetectionUntil(hasFace -> !hasFace, () -> {
            if (viewBinding == null) {
                return;
            }
            viewBinding.idle.setVisibility(View.VISIBLE);
            viewBinding.success.setVisibility(View.GONE);
            viewBinding.failure.setVisibility(View.GONE);
            runFaceDetectionUntil(hasFace -> hasFace, this::startSession);
        });
    }
}