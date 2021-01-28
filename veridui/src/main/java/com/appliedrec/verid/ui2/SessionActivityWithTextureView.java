package com.appliedrec.verid.ui2;

import android.Manifest;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

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

                RectF viewRect = new RectF(0,0, textureView.getWidth(), textureView.getHeight());

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
                final PointF scale;
                float faceViewScale;
                if (viewAspectRatio < imageAspectRatio) {
                    scale = new PointF((viewRect.height() / viewRect.width()) * ((float) height / (float) width), 1f);
                    faceViewScale = viewRect.height()/h;
                } else {
                    scale = new PointF(1f, (viewRect.width() / viewRect.height()) * ((float) width / (float) height));
                    faceViewScale = viewRect.width()/w;
                }
                if (rotationDegrees % 180 != 0) {
                    float multiplier = viewAspectRatio < imageAspectRatio ? w/h : h/w;
                    scale.x *= multiplier;
                    scale.y *= multiplier;
                }

                FrameLayout.LayoutParams detectedFaceViewLayoutParams = new FrameLayout.LayoutParams(detectedFaceView.getLayoutParams());
                detectedFaceViewLayoutParams.width = (int)(w * faceViewScale);
                detectedFaceViewLayoutParams.height = (int)(h * faceViewScale);
                detectedFaceViewLayoutParams.gravity = Gravity.CENTER;
                detectedFaceView.setLayoutParams(detectedFaceViewLayoutParams);

                Matrix matrix = new Matrix();
                matrix.setScale(scale.x, scale.y, viewRect.centerX(), viewRect.centerY());
                if (rotationDegrees != 0) {
                    matrix.postRotate(0 - rotationDegrees, viewRect.centerX(), viewRect.centerY());
                }
                textureView.setTransform(matrix);
            });
        });
    }
}
