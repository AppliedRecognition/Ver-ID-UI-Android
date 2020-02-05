package com.appliedrec.verid.sample;

import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.appliedrec.verid.ui.DetectedFaceView;

import java.util.Objects;

public class FaceGuidePreferenceFragment extends PreferenceDialogFragmentCompat implements SeekBar.OnSeekBarChangeListener, ViewTreeObserver.OnGlobalLayoutListener {

    private SeekBar seekBar;
    private DetectedFaceView detectedFaceView;

    public static FaceGuidePreferenceFragment newInstance(String key) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        FaceGuidePreferenceFragment fragment = new FaceGuidePreferenceFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        seekBar = view.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setProgress(((FaceGuidePreference)getPreference()).getValue());
        detectedFaceView = view.findViewById(R.id.detectedFaceView);
        detectedFaceView.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (detectedFaceView != null) {
            detectedFaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
        if (!positiveResult) {
            return;
        }
        DialogPreference preference = getPreference();
        if (preference instanceof FaceGuidePreference) {
            ((FaceGuidePreference) preference).setValue(seekBar.getProgress());
        }
    }

    @Override
    public void onGlobalLayout() {
        onProgressChanged(seekBar, seekBar.getProgress(), false);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        if (seekBar == null || detectedFaceView == null) {
            return;
        }
        float progress = (float)seekBar.getProgress()/ (float)seekBar.getMax();
        RectF faceRect = ((FaceGuidePreference) getPreference()).createFaceRect(progress, detectedFaceView.getWidth(), detectedFaceView.getHeight());
        detectedFaceView.setFaceRect(faceRect, null, Objects.requireNonNull(getContext()).getResources().getColor(R.color.verid_green), 0x80000000, null, null);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
