package com.appliedrec.verid.ui2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

import androidx.annotation.IntRange;
import androidx.annotation.UiThread;

public class CameraSurfaceView extends SurfaceView {

    private int fixedWidth = -1;
    private int fixedHeight = -1;

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
    public void setFixedSize(@IntRange(from = 10) int width, @IntRange(from = 10) int height) {
        fixedWidth = width;
        fixedHeight = height;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (fixedWidth <= 0 || fixedHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        setMeasuredDimension(fixedWidth, fixedHeight);
    }
}
