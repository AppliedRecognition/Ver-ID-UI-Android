package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

public abstract class NumberPreference extends DialogPreference {

    NumberPicker numberPicker;
    private int value;

    public NumberPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
    }

    public NumberPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumberPreference(Context context) {
        this(context, null, 0);
    }

    @Override
    protected View onCreateDialogView() {
        numberPicker = new NumberPicker(getContext());
        numberPicker.setMinValue(getMinValue());
        numberPicker.setMaxValue(getMaxValue());
        numberPicker.setValue(value);
        return numberPicker;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            numberPicker.clearFocus();
            setValue(numberPicker.getValue());
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
            notifyChanged();
        }
    }

    protected abstract int getDefaultValue();

    protected abstract int getMinValue();

    protected abstract int getMaxValue();
}
