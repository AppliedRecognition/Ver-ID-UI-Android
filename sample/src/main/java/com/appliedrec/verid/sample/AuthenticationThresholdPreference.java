package com.appliedrec.verid.sample;

import android.content.Context;
import android.util.AttributeSet;

public class AuthenticationThresholdPreference extends NumberPreference {

    public AuthenticationThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AuthenticationThresholdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AuthenticationThresholdPreference(Context context) {
        super(context);
    }

    @Override
    protected int getDefaultValue() {
        return 40;
    }

    @Override
    protected int getMinValue() {
        return 25;
    }

    @Override
    protected int getMaxValue() {
        return 55;
    }
}
