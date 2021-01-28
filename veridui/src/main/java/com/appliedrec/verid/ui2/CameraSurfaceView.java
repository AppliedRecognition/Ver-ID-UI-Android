package com.appliedrec.verid.ui2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

import androidx.annotation.IntRange;
import androidx.annotation.UiThread;

public class CameraSurfaceView extends SurfaceView {

    private float aspectRatio = 0;

    public CameraSurfaceView(Context context) {
        super(context);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @UiThread
    public void setAspectRatio(@IntRange(from = 1) int width, @IntRange(from = 1) int height) {
        if (width <= 0 || height <= 0) {
            throw new RuntimeException("Size of surface view must not be negative");
        }
        aspectRatio = (float)width / (float)height;
        getHolder().setFixedSize(width, height);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (aspectRatio == 0) {
            setMeasuredDimension(width, height);
        } else {
            int newWidth, newHeight;
            float actualRatio = width > height ? aspectRatio : 1f / aspectRatio;
            if (width < height * actualRatio) {
                newHeight = height;
                newWidth = (int)((float)height * actualRatio);
            } else {
                newWidth = width;
                newHeight = (int)((float)width / actualRatio);
            }
            setMeasuredDimension(newWidth, newHeight);
        }
    }
}
