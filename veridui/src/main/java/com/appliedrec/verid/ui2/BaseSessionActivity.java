package com.appliedrec.verid.ui2;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.viewbinding.ViewBinding;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseSessionActivity<T extends ViewBinding, U extends View> extends AbstractSessionActivity<AbstractSessionFragment<U>> implements CameraWrapper.Listener {

    private T viewBinding;
    private AbstractSessionFragment<U> sessionFragment;
    private final AtomicReference<ISessionVideoRecorder> sessionVideoRecorder = new AtomicReference<>();
    private CameraWrapper cameraWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = inflateLayout();
        setContentView(viewBinding.getRoot());
        sessionFragment = (AbstractSessionFragment<U>) getSupportFragmentManager().findFragmentById(R.id.session_fragment);
//        // Uncomment the following line to plot the face landmarks in the camera preview
//        Objects.requireNonNull(sessionFragment).setPlotFaceLandmarks(true);
        cameraWrapper = new CameraWrapper(this, getCameraLocation(), getImageAnalyzer(), getSessionVideoRecorder().orElse(null), getPreviewClass());
        cameraWrapper.setListener(this);
        drawFaces();
    }

    protected abstract T inflateLayout();

    protected abstract Class<?> getPreviewClass();

    protected final Optional<T> getViewBinding() {
        return Optional.ofNullable(viewBinding);
    }

    protected final Optional<CameraWrapper> getCameraWrapper() {
        return Optional.ofNullable(cameraWrapper);
    }

    protected final void setCameraWrapper(CameraWrapper cameraWrapper) {
        this.cameraWrapper = cameraWrapper;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
        sessionFragment = null;
        cameraWrapper = null;
        getSessionVideoRecorder().ifPresent(getLifecycle()::removeObserver);
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

    protected Optional<AbstractSessionFragment<U>> getSessionFragment() {
        return Optional.ofNullable(sessionFragment);
    }

    @SuppressLint("MissingPermission")
    @Override
    @MainThread
    protected void startCamera() {
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(viewFinder -> {
            int width = viewFinder.getWidth();
            int height = viewFinder.getHeight();
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
}
