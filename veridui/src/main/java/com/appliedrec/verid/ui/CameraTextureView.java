package com.appliedrec.verid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.ViewGroup;

import java.io.IOException;

public class CameraTextureView extends TextureView implements TextureView.SurfaceTextureListener, ICameraPreviewView {

    private Camera camera;
    private boolean textureCreated = false;
    private int width = 0;
    private int height = 0;
    private CameraPreviewViewListener listener;

    public CameraTextureView(Context context) {
        super(context);
        init();
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setSurfaceTextureListener(this);
    }

    private void startPreview() {
        if (camera != null && textureCreated && getSurfaceTexture() != null && width > 0 && height > 0) {
            try {
                camera.setPreviewTexture(getSurfaceTexture());
                camera.startPreview();
                if (listener != null) {
                    listener.onCameraPreviewStarted(camera);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
        startPreview();
    }

    public void setListener(CameraPreviewViewListener listener) {
        this.listener = listener;
    }

    public void setFixedSize(int width, int height) {
        this.width = width;
        this.height = height;
        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.width = width;
        lp.height = height;
        setLayoutParams(lp);
        startPreview();
    }

    public int getFixedWidth() {
        return this.width;
    }

    public int getFixedHeight() {
        return this.height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(width, height);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        textureCreated = surfaceTexture != null;
        startPreview();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        textureCreated = false;
        if (camera != null) {
            camera.stopPreview();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }
}
