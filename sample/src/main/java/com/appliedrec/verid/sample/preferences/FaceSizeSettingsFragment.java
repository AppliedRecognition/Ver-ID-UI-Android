package com.appliedrec.verid.sample.preferences;

import android.content.Context;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.sample.R;
import com.appliedrec.verid.ui2.DetectedFaceView;

public class FaceSizeSettingsFragment extends Fragment implements SeekBar.OnSeekBarChangeListener, ViewTreeObserver.OnGlobalLayoutListener {

    public interface Listener {
        void onFaceSizeFractionChanged(float value, boolean isLandscape);
    }

    public static final String ARG_IS_LANDSCAPE = "landscape";
    public static final String ARG_INITIAL_VALUE = "value";
    private SeekBar seekBar;
    private DetectedFaceView detectedFaceView;
    private boolean isLandscape;
    private Listener listener;

    public static FaceSizeSettingsFragment newInstance(boolean isLandscape, float initialValue) {

        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_LANDSCAPE, isLandscape);
        args.putFloat(ARG_INITIAL_VALUE, initialValue);

        FaceSizeSettingsFragment fragment = new FaceSizeSettingsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        isLandscape = getArguments() != null && getArguments().getBoolean(ARG_IS_LANDSCAPE, false);
        if (context instanceof Listener) {
            listener = (Listener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        @LayoutRes int fragment;
        if (isLandscape) {
            fragment = R.layout.fragment_face_guide_landscape;
        } else {
            fragment = R.layout.fragment_face_guide_portrait;
        }
        View view = inflater.inflate(fragment, container, false);
        seekBar = view.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(this);
        if (getArguments() != null) {
            LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
            int value = (int)(getArguments().getFloat(ARG_INITIAL_VALUE, isLandscape ? livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight() : livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth())*seekBar.getMax());
            seekBar.setProgress(value);
        }
        detectedFaceView = view.findViewById(R.id.detectedFaceView);
        detectedFaceView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        seekBar.setOnSeekBarChangeListener(null);
        detectedFaceView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        if (seekBar == null || detectedFaceView == null || getContext() == null) {
            return;
        }
        float progress = (float)seekBar.getProgress()/ (float)seekBar.getMax();
        RectF faceRect = createFaceRect(progress, detectedFaceView.getWidth(), detectedFaceView.getHeight());
        detectedFaceView.setFaceRect(faceRect, null, requireContext().getResources().getColor(R.color.verid_green), 0x80000000, null, null);
        if (listener != null) {
            listener.onFaceSizeFractionChanged(progress, isLandscape);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onGlobalLayout() {
        onProgressChanged(seekBar, seekBar.getProgress(), false);
    }

    private RectF createFaceRect(float progress, int viewWidth, int viewHeight) {
        float height;
        float width;
        if (isLandscape) {
            height = (float) viewHeight * progress;
            width = height / 5f * 4f;
        } else {
            width = (float) viewWidth * progress;
            height = width / 4f * 5f;
        }
        float x = (float) viewWidth / 2f - width / 2f;
        float y = (float) viewHeight / 2f - height / 2f;
        return new RectF(x, y, x + width, y + height);
    }
}
