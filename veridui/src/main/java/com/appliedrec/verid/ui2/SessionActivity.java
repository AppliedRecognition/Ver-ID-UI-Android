package com.appliedrec.verid.ui2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.MainThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.appliedrec.verid.ui2.databinding.ActivitySessionBinding;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class SessionActivity extends AbstractSessionActivity<VerIDSessionFragment> implements SurfaceHolder.Callback, CameraWrapper.Listener {

    private ActivitySessionBinding viewBinding;
    private VerIDSessionFragment sessionFragment;
    private final AtomicReference<ISessionVideoRecorder> sessionVideoRecorder = new AtomicReference<>();
    private CameraWrapper cameraWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivitySessionBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        sessionFragment = (VerIDSessionFragment) getSupportFragmentManager().findFragmentById(R.id.session_fragment);
//        // Uncomment the following line to plot the face landmarks in the camera preview
//        Objects.requireNonNull(sessionFragment).setPlotFaceLandmarks(true);
//        // Uncomment to use MLKit for face detection
//        getImageAnalyzer().setUseMLKit(true);
        cameraWrapper = new CameraWrapper(this, getCameraLocation(), getImageAnalyzer(), getSessionVideoRecorder().orElse(null));
        cameraWrapper.setListener(this);
        drawFaces();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
        sessionFragment = null;
        cameraWrapper = null;
        getSessionVideoRecorder().ifPresent(videoRecorder -> {
            getLifecycle().removeObserver(videoRecorder);
        });
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(surfaceView -> {
            surfaceView.getHolder().addCallback(this);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(surfaceView -> {
            surfaceView.getHolder().removeCallback(this);
        });
    }

    //region Video recording

    @Override
    public void setVideoRecorder(ISessionVideoRecorder videoRecorder) {
        this.sessionVideoRecorder.set(videoRecorder);
        this.getLifecycle().addObserver(videoRecorder);
    }

    //endregion

    protected Optional<ISessionVideoRecorder> getSessionVideoRecorder() {
        return Optional.ofNullable(sessionVideoRecorder.get());
    }

    protected Optional<VerIDSessionFragment> getSessionFragment() {
        return Optional.ofNullable(sessionFragment);
    }

    @Override
    protected Optional<LinearLayout> getFaceImagesView() {
        return Optional.ofNullable(viewBinding).map(views -> views.faceImages);
    }

    @SuppressLint("MissingPermission")
    @Override
    @MainThread
    protected void startCamera() {
        getSessionFragment().ifPresent(fragment -> {
            if (fragment.getView() == null) {
                return;
            }
            int width = fragment.getView().getWidth();
            int height = fragment.getView().getHeight();
            int displayRotation = getDisplayRotation();

            cameraWrapper.start(width, height, displayRotation);
        });
    }

    @MainThread
    protected int getDisplayRotation() {
        if (viewBinding == null) {
            return 0;
        }
        switch (viewBinding.getRoot().getDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                return 0;
            case Surface.ROTATION_90:
                return  90;
            case Surface.ROTATION_180:
                return  180;
            case Surface.ROTATION_270:
                return 270;
        }
    }

    //region Surface callback

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (cameraWrapper != null) {
            cameraWrapper.setPreviewSurfaceHolder(surfaceHolder);
//            cameraWrapper.setPreviewSurface(surfaceHolder.getSurface(), SurfaceHolder.class);
        }
        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (cameraWrapper != null) {
            cameraWrapper.stop();
            cameraWrapper = null;
        }
    }

    @Override
    public void onPreviewSize(int width, int height, int sensorOrientation) {
        runOnUiThread(() -> {
            if (viewBinding == null) {
                return;
            }
            getSessionFragment().ifPresent(sessionFragment -> {
                View fragmentView = sessionFragment.getView();
                if (fragmentView == null) {
                    return;
                }
                float fragmentWidth = fragmentView.getWidth();
                float fragmentHeight = fragmentView.getHeight();
                sessionFragment.getViewFinder().ifPresent(viewFinder -> {
                    int rotationDegrees = getDisplayRotation();
                    float w, h;
                    if ((sensorOrientation - rotationDegrees) % 180 == 0) {
                        w = width;
                        h = height;
                    } else {
                        w = height;
                        h = width;
                    }
                    float viewAspectRatio = fragmentWidth/fragmentHeight;
                    float imageAspectRatio = w/h;
                    final float scale;
                    if (viewAspectRatio > imageAspectRatio) {
                        scale = fragmentWidth/w;
                    } else {
                        scale = fragmentHeight/h;
                    }
                    ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(viewFinder.getLayoutParams());
                    layoutParams.width = (int) (scale * w);
                    layoutParams.height = (int) (scale * h);
                    layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                    layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                    layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                    layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                    viewFinder.setLayoutParams(layoutParams);
                });
            });
        });
    }

    //endregion
}