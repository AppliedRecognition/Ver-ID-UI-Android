package com.appliedrec.verid.ui2;

import android.Manifest;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.appliedrec.verid.ui2.databinding.ActivitySessionWithTextureViewBinding;

import java.util.Optional;

public class SessionActivityWithTextureView extends BaseSessionActivity<ActivitySessionWithTextureViewBinding,TextureView> implements TextureView.SurfaceTextureListener {

    @Override
    protected ActivitySessionWithTextureViewBinding inflateLayout() {
        return ActivitySessionWithTextureViewBinding.inflate(getLayoutInflater());
    }

    @Override
    protected Class<?> getPreviewClass() {
        return SurfaceTexture.class;
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(textureView -> textureView.setSurfaceTextureListener(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(textureView -> textureView.setSurfaceTextureListener(null));
    }

    @Override
    protected Optional<LinearLayout> getFaceImagesView() {
        return getViewBinding().map(viewBinding -> viewBinding.faceImages);
    }

    //region SurfaceTextureListener

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        getCameraWrapper().ifPresent(cameraWrapper -> cameraWrapper.setPreviewSurface(new Surface(surfaceTexture)));
        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        getCameraWrapper().ifPresent(cameraWrapper -> {
            cameraWrapper.stop();
            setCameraWrapper(null);
        });
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    //endregion

    @Override
    public void onPreviewSize(int width, int height, int sensorOrientation) {
        getSessionFragment().flatMap(AbstractSessionFragment::getDetectedFaceView).ifPresent(detectedFaceView -> {
            getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(textureView -> {
                textureView.getSurfaceTexture().setDefaultBufferSize(width, height);
                RectF viewRect = new RectF(0,0, detectedFaceView.getWidth(), detectedFaceView.getHeight());
                float rotationDegrees = 0;
                try {
                    rotationDegrees = (float)getDisplayRotation();
                } catch (Exception ignored) {

                }
                float w, h;
                if ((sensorOrientation - rotationDegrees) % 180 == 0) {
                    w = width;
                    h = height;
                } else {
                    w = height;
                    h = width;
                }
                float viewAspectRatio = viewRect.width()/viewRect.height();
                float imageAspectRatio = w/h;
                final float scale;
                if (viewAspectRatio > imageAspectRatio) {
                    scale = viewRect.width()/w;
                } else {
                    scale = viewRect.height()/h;
                }
                int scaledWidth = (int) (scale * w);
                int scaledHeight = (int) (scale * h);
                ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(textureView.getLayoutParams());
                Matrix matrix = new Matrix();
                layoutParams.width = scaledWidth;
                layoutParams.height = scaledHeight;
                layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                textureView.setLayoutParams(layoutParams);

                RectF textureRect = new RectF(0, 0, scaledWidth, scaledHeight);
                float centerX = textureRect.centerX();
                float centerY = textureRect.centerY();
                if (rotationDegrees != 0) {
                    if (rotationDegrees % 180 != 0) {
                        matrix.setScale((float) height / (float) width, (float) width / (float) height, centerX, centerY);
                    }
                    matrix.postRotate(0 - rotationDegrees, centerX, centerY);
                }
                textureView.setTransform(matrix);
            });
        });
    }
}
