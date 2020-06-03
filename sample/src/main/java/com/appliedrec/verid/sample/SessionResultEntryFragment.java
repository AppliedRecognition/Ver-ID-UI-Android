package com.appliedrec.verid.sample;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SessionResultEntryFragment extends Fragment {

    private static final String ARG_KEY = "key";
    private static final String ARG_VALUE = "value";

    public static SessionResultEntryFragment newInstance(String key, String value) {

        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putString(ARG_VALUE, value);

        SessionResultEntryFragment fragment = new SessionResultEntryFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout view = (LinearLayout) inflater.inflate(R.layout.result_list_item, container, false);
        Bundle args = getArguments();
        if (args != null) {
            TextView keyTextView = view.findViewById(R.id.key);
            TextView valueTextView = view.findViewById(R.id.value);
            keyTextView.setText(args.getString(ARG_KEY));
            valueTextView.setText(args.getString(ARG_VALUE));
        }
        return view;
    }
}
