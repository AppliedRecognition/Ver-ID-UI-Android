package com.appliedrec.verid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;

import java.io.IOException;

@SuppressWarnings("deprecation")
public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback, ICameraPreviewView {

	private int mWidth = 0;
	private int mHeight = 0;
	private Camera mCamera;
	private Boolean mSurfaceCreated = false;
    private CameraPreviewViewListener mListener;

	public CameraSurfaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		getHolder().addCallback(this);
		setZOrderMediaOverlay(true);
	}

	@Override
    public void setListener(CameraPreviewViewListener listener) {
        mListener = listener;
    }

	public void setFixedSize(int width, int height) {
		mWidth = width;
		mHeight = height;
		getHolder().setFixedSize(mWidth, mHeight);
		LayoutParams lp = getLayoutParams();
		lp.width = mWidth;
		lp.height = mHeight;
		setLayoutParams(lp);
		startPreview();
	}

	public void setCamera(Camera camera) {
		mCamera = camera;
		startPreview();
	}
	
	private void startPreview() {
		if (mCamera != null && mSurfaceCreated && getHolder() != null && mWidth > 0 && mHeight > 0) {
			try {
				mCamera.setPreviewDisplay(getHolder());
				mCamera.startPreview();
                if (mListener != null) {
                    mListener.onCameraPreviewStarted(mCamera);
                }
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(mWidth, mHeight);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceCreated = holder != null;
		startPreview();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// TODO Auto-generated method stub
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mSurfaceCreated = false;
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
}