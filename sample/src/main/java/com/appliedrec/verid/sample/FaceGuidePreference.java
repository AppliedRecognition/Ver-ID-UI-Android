package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.preference.DialogPreference;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import com.appliedrec.verid.ui.DetectedFaceView;

public abstract class FaceGuidePreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener, ViewTreeObserver.OnGlobalLayoutListener {

    private SeekBar seekBar;
    private DetectedFaceView detectedFaceView;
    private int value;

    public FaceGuidePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    public FaceGuidePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FaceGuidePreference(Context context) {
        this(context, null, 0);
    }

    @Override
    protected View onCreateDialogView() {
        ConstraintLayout constraintLayout = createView();
        seekBar = constraintLayout.findViewById(R.id.seekBar);
        seekBar.setProgress(value);
        seekBar.setOnSeekBarChangeListener(this);
        detectedFaceView = constraintLayout.findViewById(R.id.detectedFaceView);
        detectedFaceView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        return constraintLayout;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        detectedFaceView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
        if (positiveResult) {
            seekBar.clearFocus();
            setValue(seekBar.getProgress());
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, getDefaultValue());
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setValue(restorePersistedValue ? getPersistedInt(value) : (int) defaultValue);
    }

    public void setValue(int value) {
        if (shouldPersist()) {
            persistInt(value);
        }
        if (value != this.value) {
            this.value = value;
            onProgressChanged(seekBar, value, false);
            notifyChanged();
        }
    }

    protected abstract ConstraintLayout createView();

    protected abstract int getDefaultValue();

    protected abstract RectF createFaceRect(float progress, int viewWidth, int viewHeight);

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        if (seekBar == null || detectedFaceView == null) {
            return;
        }
        float progress = (float)seekBar.getProgress()/ (float)seekBar.getMax();
        RectF faceRect = createFaceRect(progress, detectedFaceView.getWidth(), detectedFaceView.getHeight());
        detectedFaceView.setFaceRect(faceRect, null, getContext().getResources().getColor(R.color.verid_green), 0x80000000, null, null);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onGlobalLayout() {
        onProgressChanged(seekBar, value, false);
    }
}
