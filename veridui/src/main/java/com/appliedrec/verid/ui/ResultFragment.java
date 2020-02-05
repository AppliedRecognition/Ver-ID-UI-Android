package com.appliedrec.verid.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.VerIDSessionResult;

public class ResultFragment extends Fragment implements IResultFragment {

    private ResultFragmentListener resultFragmentListener;
    private VerIDSessionResult sessionResult;
    private IStringTranslator stringTranslator;

    public static ResultFragment newInstance(VerIDSessionResult result, String text) {
        ResultFragment fragment = new ResultFragment();
        Bundle args = new Bundle();
        args.putParcelable(VerIDSessionActivity.EXTRA_RESULT, result);
        if (text != null) {
            args.putString(Intent.EXTRA_TEXT, text);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            sessionResult = args.getParcelable(VerIDSessionActivity.EXTRA_RESULT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guide, container, false);
        final ImageView imageView = view.findViewById(R.id.imageView);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(imageView.getLayoutParams());
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.CENTER;
        imageView.setLayoutParams(layoutParams);
        Button doneButton = view.findViewById(R.id.buttonRight);
        doneButton.setText(getTranslatedString("Done"));
        doneButton.setOnClickListener(v -> {
            if (resultFragmentListener != null) {
                resultFragmentListener.onResultFragmentDismissed(ResultFragment.this);
            }
        });
        Bundle args = getArguments();
        if (args != null) {
            String text = args.getString(Intent.EXTRA_TEXT);
            if (text != null) {
                ((TextView) view.findViewById(R.id.text)).setText(text);
            }
        }
        if (sessionResult != null) {
            Button tipsButton = view.findViewById(R.id.buttonLeft);
            if (sessionResult.getError() == null) {
                tipsButton.setVisibility(View.GONE);
                Uri[] imageUris = sessionResult.getImageUris(Bearing.STRAIGHT);
                if (imageUris.length > 0) {
                    Uri imageUri = imageUris[0];
                    imageView.setImageURI(imageUri);
                }
            } else {
                imageView.setImageResource(R.mipmap.face_failure);
                tipsButton.setText(getTranslatedString("Tips"));
                tipsButton.setVisibility(View.VISIBLE);
                tipsButton.setOnClickListener(v -> {
                    Intent tipsIntent = new Intent(getContext(), TipsActivity.class);
                    tipsIntent.putExtras(getActivity().getIntent());
                    startActivity(tipsIntent);
                });
            }
        }
        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ResultFragmentListener) {
            resultFragmentListener = (ResultFragmentListener)context;
        }
        if (context instanceof IStringTranslator) {
            stringTranslator = (IStringTranslator) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        resultFragmentListener = null;
        stringTranslator = null;
    }

    public VerIDSessionResult getSessionResult() {
        return sessionResult;
    }

    private String getTranslatedString(String original, Object ...args) {
        if (stringTranslator != null) {
            return stringTranslator.getTranslatedString(original, args);
        } else {
            return String.format(original, args);
        }
    }
}
