package com.appliedrec.verid.sample;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SessionResultHeadingFragment extends Fragment {

    private static final String ARG_TEXT = "text";

    public static SessionResultHeadingFragment newInstance(String text) {

        Bundle args = new Bundle();
        args.putString(ARG_TEXT, text);

        SessionResultHeadingFragment fragment = new SessionResultHeadingFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        TextView textView = (TextView) inflater.inflate(R.layout.fragment_session_result_heading, container, false);
        if (getArguments() != null) {
            textView.setText(getArguments().getString(ARG_TEXT));
        }
        return textView;
    }
}
