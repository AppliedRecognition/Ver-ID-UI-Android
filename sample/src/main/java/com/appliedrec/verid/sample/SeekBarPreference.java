package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

public class SeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private SeekBar seekBar;
    private int value;

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.seekbar_preference);
    }

    public SeekBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SeekBarPreference(Context context) {
        this(context, null, 0);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        seekBar = view.findViewById(R.id.seekBar);
        seekBar.setProgress(value);
        seekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setValue(restorePersistedValue ? getPersistedInt(value) : (int) defaultValue);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
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

    //region Seek bar listener

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            setValue(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    //endregion
}
