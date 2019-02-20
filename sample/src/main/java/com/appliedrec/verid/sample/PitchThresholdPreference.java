package com.appliedrec.verid.sample;

import android.content.Context;
import android.util.AttributeSet;

import com.appliedrec.verid.core.SessionSettings;

public class PitchThresholdPreference extends NumberPreference {

    public PitchThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PitchThresholdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PitchThresholdPreference(Context context) {
        super(context);
    }

    @Override
    protected int getDefaultValue() {
        return (int) new SessionSettings().getPitchThreshold();
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
