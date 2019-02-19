package com.appliedrec.verid.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
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

    public static TipFragment newInstance(int tipIndex) {
        Bundle args = new Bundle();
        args.putInt("tipIndex", tipIndex);
        TipFragment tipFragment = new TipFragment();
        tipFragment.setArguments(args);
        return tipFragment;
    }

    public static View createView(LayoutInflater inflater, ViewGroup container, int tipIndex) {
        View view = inflater.inflate(R.layout.fragment_guide, container, false);
        ((ViewGroup)view.findViewById(R.id.hero)).removeView(view.findViewById(R.id.textureView));
        View lineView = new CrossOutView(view.getContext());
        FrameLayout.LayoutParams lineLayoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ((FrameLayout)view.findViewById(R.id.hero)).addView(lineView, 1, lineLayoutParams);
        int imgSrc;
        int tipTextSrc;
        switch (tipIndex) {
            case 1:
                imgSrc = R.mipmap.head_with_glasses;
                tipTextSrc = R.string.tip2;
                break;
            case 2:
                imgSrc = R.mipmap.busy_background;
                tipTextSrc = R.string.tip3;
                break;
            default:
                imgSrc = R.mipmap.tip_sharp_light;
                tipTextSrc = R.string.tip1;
        }
        ((TextView) view.findViewById(R.id.text)).setText(tipTextSrc);
        ((ImageView) view.findViewById(R.id.imageView)).setImageResource(imgSrc);
        view.findViewById(R.id.buttonLeft).setVisibility(View.GONE);
        view.findViewById(R.id.buttonRight).setVisibility(View.GONE);
        return view;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return getView();
        }
        final int tipIndex;
        if (getArguments() != null) {
            tipIndex = getArguments().getInt("tipIndex", 0);
        } else {
            tipIndex = 0;
        }
        return TipFragment.createView(inflater, container, tipIndex);
    }
}
