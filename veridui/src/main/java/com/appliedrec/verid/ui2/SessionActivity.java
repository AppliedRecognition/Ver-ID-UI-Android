package com.appliedrec.verid.ui2;

import android.Manifest;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.ui2.databinding.ActivitySessionBinding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public class SessionActivity extends BaseSessionActivity<ActivitySessionBinding,CameraSurfaceView> implements SurfaceHolder.Callback {

    @Override
    protected ActivitySessionBinding inflateLayout() {
        return ActivitySessionBinding.inflate(getLayoutInflater());
    }

    @Override
    protected Class<?> getPreviewClass() {
        return SurfaceHolder.class;
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(surfaceView -> surfaceView.getHolder().addCallback(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(surfaceView -> surfaceView.getHolder().removeCallback(this));
    }

    @Override
    protected Optional<LinearLayout> getFaceImagesView() {
        return getViewBinding().map(viewBinding -> viewBinding.faceImages);
    }

    //region Surface callback

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        getCameraWrapper().ifPresent(cameraWrapper -> cameraWrapper.setPreviewSurface(surfaceHolder.getSurface()));
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
        getCameraWrapper().ifPresent(cameraWrapper -> {
            cameraWrapper.stop();
            setCameraWrapper(null);
        });
    }

    //endregion


    @Override
    public void onPreviewSize(int width, int height, int sensorOrientation) {
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
                Size viewSize = new Size((int)(scale * w), (int)(scale * h));
                ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(viewFinder.getLayoutParams());
                layoutParams.width = viewSize.width;
                layoutParams.height = viewSize.height;
                layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                viewFinder.setLayoutParams(layoutParams);

                viewFinder.setFixedSize(viewSize.width, viewSize.height);
            });
        });
    }
}