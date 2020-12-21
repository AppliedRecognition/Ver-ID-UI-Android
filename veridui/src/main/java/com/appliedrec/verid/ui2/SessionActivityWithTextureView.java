package com.appliedrec.verid.ui2;

import android.Manifest;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
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
        return true;
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

                ConstraintLayout fragmentView = (ConstraintLayout)getSessionFragment().get().getView();

                ConstraintSet constraintSet = new ConstraintSet();
                constraintSet.clone(fragmentView);
                constraintSet.constrainWidth(textureView.getId(), scaledWidth);
                constraintSet.constrainHeight(textureView.getId(), scaledHeight);
                constraintSet.setTranslation(textureView.getId(), viewRect.width()/2f-scaledWidth/2f, viewRect.height()/2f-scaledHeight/2f);
                constraintSet.setDimensionRatio(textureView.getId(), String.format("%d:%d",scaledWidth, scaledHeight));
                constraintSet.centerVertically(textureView.getId(), ConstraintSet.PARENT_ID);
                constraintSet.centerHorizontally(textureView.getId(), ConstraintSet.PARENT_ID);
                constraintSet.applyTo(fragmentView);

                ConstraintSet faceConstraintSet = new ConstraintSet();
                faceConstraintSet.clone(fragmentView);
                faceConstraintSet.constrainWidth(detectedFaceView.getId(), scaledWidth);
                faceConstraintSet.constrainHeight(detectedFaceView.getId(), scaledHeight);
                faceConstraintSet.setTranslation(detectedFaceView.getId(), viewRect.width()/2f-scaledWidth/2f, viewRect.height()/2f-scaledHeight/2f);
                faceConstraintSet.centerVertically(detectedFaceView.getId(), ConstraintSet.PARENT_ID);
                faceConstraintSet.centerHorizontally(detectedFaceView.getId(), ConstraintSet.PARENT_ID);
                faceConstraintSet.applyTo(fragmentView);

                if (rotationDegrees != 0) {
                    Matrix matrix = new Matrix();
                    RectF textureRect = new RectF(0, 0, scaledWidth, scaledHeight);
                    float centerX = textureRect.centerX();
                    float centerY = textureRect.centerY();
                    if (rotationDegrees % 180 != 0) {
                        matrix.setScale((float) height / (float) width, (float) width / (float) height, centerX, centerY);
                    }
                    matrix.postRotate(0 - rotationDegrees, centerX, centerY);
                    textureView.setTransform(matrix);
                }
            });
        });
    }
}
