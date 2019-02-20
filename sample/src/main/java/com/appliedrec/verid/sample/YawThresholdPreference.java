package com.appliedrec.verid.sample;

import android.content.Context;
import android.util.AttributeSet;

import com.appliedrec.verid.core.SessionSettings;

public class YawThresholdPreference extends NumberPreference {

    public YawThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public YawThresholdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public YawThresholdPreference(Context context) {
        super(context);
    }

    @Override
    protected int getDefaultValue() {
        return (int) new SessionSettings().getYawThreshold();
    }

    @Override
    protected int getMinValue() {
        return 8;
    }

    @Override
    protected int getMaxValue() {
        return 24;
    }
}
