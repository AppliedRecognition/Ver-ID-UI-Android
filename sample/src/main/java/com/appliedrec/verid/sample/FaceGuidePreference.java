package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;

public abstract class FaceGuidePreference extends DialogPreference {

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
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return getDefaultValue();
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setValue(restorePersistedValue ? getPersistedInt(value) : (int) defaultValue);
    }

    public void setValue(int value) {
        setSummary(getSummaryFromValue(value));
        if (shouldPersist()) {
            persistInt(value);
        }
        if (value != this.value) {
            this.value = value;
            notifyChanged();
        }
    }

    public int getValue() {
        return value;
    }

    protected abstract int getDefaultValue();

    protected abstract RectF createFaceRect(float progress, int viewWidth, int viewHeight);

    protected abstract String getSummaryFromValue(int value);
}