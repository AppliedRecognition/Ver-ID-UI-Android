package com.appliedrec.verid.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

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

    public static View createView(LayoutInflater inflater, ViewGroup container, int tipIndex, IStringTranslator translator) {
        View view = inflater.inflate(R.layout.fragment_guide, container, false);
        ((ViewGroup)view.findViewById(R.id.hero)).removeView(view.findViewById(R.id.textureView));
        View lineView = new CrossOutView(view.getContext());
        FrameLayout.LayoutParams lineLayoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ((FrameLayout)view.findViewById(R.id.hero)).addView(lineView, 1, lineLayoutParams);
        int imgSrc;
        String tipText;
        if (translator == null) {
            translator = new TranslatedStrings(view.getContext(), null);
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
        ((TextView) view.findViewById(R.id.text)).setText(tipText);
        ((ImageView) view.findViewById(R.id.imageView)).setImageResource(imgSrc);
        view.findViewById(R.id.buttonLeft).setVisibility(View.GONE);
        view.findViewById(R.id.buttonRight).setVisibility(View.GONE);
        return view;
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
