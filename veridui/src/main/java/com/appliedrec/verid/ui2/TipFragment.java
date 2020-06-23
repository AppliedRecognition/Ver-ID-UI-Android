package com.appliedrec.verid.ui2;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.ui2.databinding.FragmentGuideBinding;

/**
 * Created by jakub on 23/08/2017.
 */
public class TipFragment extends Fragment {

    private IStringTranslator translator;

    public static TipFragment newInstance(int tipIndex) {
        Bundle args = new Bundle();
        args.putInt("tipIndex", tipIndex);
        TipFragment tipFragment = new TipFragment();
        tipFragment.setArguments(args);
        return tipFragment;
    }

    @SuppressWarnings("WeakerAccess")
    public static View createView(LayoutInflater inflater, ViewGroup container, int tipIndex, IStringTranslator translator) {
        int imgSrc;
        String tipText;
        if (translator == null) {
            translator = new TranslatedStrings(inflater.getContext(), null);
        }
        switch (tipIndex) {
            case 1:
                imgSrc = R.mipmap.head_with_glasses;
                tipText = translator.getTranslatedString("If you can, take off your glasses.");
                break;
            case 2:
                imgSrc = R.mipmap.busy_background;
                tipText = translator.getTranslatedString("Avoid standing in front of busy backgrounds.");
                break;
            default:
                imgSrc = R.mipmap.tip_sharp_light;
                tipText = translator.getTranslatedString("Avoid standing in a light that throws sharp shadows like in sharp sunlight or directly under a lamp.");
        }
        return TipFragment.createView(inflater, container, imgSrc, tipText);
    }

    public static View createView(LayoutInflater inflater, ViewGroup container, @DrawableRes int imgSrc, String text) {
        FragmentGuideBinding fragmentGuideBinding = FragmentGuideBinding.inflate(inflater, container, false);
        fragmentGuideBinding.hero.removeView(fragmentGuideBinding.textureView);
        View lineView = new CrossOutView(inflater.getContext());
        FrameLayout.LayoutParams lineLayoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentGuideBinding.hero.addView(lineView, 1, lineLayoutParams);
        fragmentGuideBinding.text.setText(text);
        fragmentGuideBinding.imageView.setImageResource(imgSrc);
        fragmentGuideBinding.buttonLeft.setVisibility(View.GONE);
        fragmentGuideBinding.buttonRight.setVisibility(View.GONE);
        return fragmentGuideBinding.getRoot();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return getView();
        }
        final int tipIndex;
        if (getArguments() != null) {
            tipIndex = getArguments().getInt("tipIndex", 0);
        } else {
            tipIndex = 0;
        }
        return TipFragment.createView(inflater, container, tipIndex, translator);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof IStringTranslator) {
            translator = (IStringTranslator) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        translator = null;
    }
}
