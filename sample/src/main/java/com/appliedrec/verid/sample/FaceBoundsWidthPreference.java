package com.appliedrec.verid.sample;

import android.content.Context;
import android.util.AttributeSet;

public class FaceBoundsWidthPreference extends NumberPreference {

    public FaceBoundsWidthPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FaceBoundsWidthPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FaceBoundsWidthPreference(Context context) {
        super(context);
    }

    @Override
    protected int getDefaultValue() {
        return 65;
    }

    @Override
    protected int getMinValue() {
        return 10;
    }

    @Override
    protected int getMaxValue() {
        return 120;
    }
}
