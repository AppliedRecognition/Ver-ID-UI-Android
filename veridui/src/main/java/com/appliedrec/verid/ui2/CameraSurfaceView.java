package com.appliedrec.verid.ui2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

import androidx.constraintlayout.widget.ConstraintLayout;

public class CameraSurfaceView extends SurfaceView {

    private int width;
    private int height;

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

    public void setFixedSize(int width, int height) {
        if (getHolder() != null) {
            getHolder().setFixedSize(width, height);
        }
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(getLayoutParams());
        layoutParams.width = width;
        layoutParams.height = height;
        layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        setLayoutParams(layoutParams);
        this.width = width;
        this.height = height;
    }

//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
////        int width = resolveSize(getSuggestedMinimumWidth(), this.width);
////        int height = resolveSize(getSuggestedMinimumHeight(), this.height);
//        setMeasuredDimension(width, height);
//    }
}
