package com.appliedrec.verid.sample;

import android.os.Bundle;
import android.view.View;
import android.widget.NumberPicker;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

public class NumberPreferenceDialog extends PreferenceDialogFragmentCompat {

    NumberPicker numberPicker;

    public static NumberPreferenceDialog newInstance(String key, int minValue, int maxValue) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putInt("minValue", minValue);
        args.putInt("maxValue", maxValue);
        NumberPreferenceDialog fragment = new NumberPreferenceDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        numberPicker = view.findViewById(R.id.number_picker);
        DialogPreference preference = getPreference();
        if (preference instanceof NumberPreference) {
            int value = ((NumberPreference) preference).getValue();
            numberPicker.setMinValue(getArguments().getInt("minValue"));
            numberPicker.setMaxValue(getArguments().getInt("maxValue"));
            numberPicker.setValue(value);
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (!positiveResult) {
            return;
        }
        DialogPreference preference = getPreference();
        if (preference instanceof NumberPreference) {
            ((NumberPreference) preference).setValue(numberPicker.getValue());
        }
    }
}
