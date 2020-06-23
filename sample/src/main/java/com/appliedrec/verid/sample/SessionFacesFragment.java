package com.appliedrec.verid.sample;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.VerIDSessionResult;

public class SessionFacesFragment extends Fragment {

    private static final String ARG_RESULT = "result";
    private final float height = 100;

    public static SessionFacesFragment newInstance(VerIDSessionResult result) {

        Bundle args = new Bundle();
        args.putParcelable(ARG_RESULT, result);

        SessionFacesFragment fragment = new SessionFacesFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("CheckResult")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout view = (LinearLayout) inflater.inflate(R.layout.faces_list_item, container, false);
        Bundle args = getArguments();
        if (args != null) {
            VerIDSessionResult result = args.getParcelable(ARG_RESULT);
            if (result != null && getContext() != null) {
                new Thread(() -> {
                    for (FaceCapture face : result.getFaceCaptures()) {
                        Bitmap bitmap = face.getFaceImage();
                        float height = this.height * getResources().getDisplayMetrics().density;
                        float scale = height / (float)bitmap.getHeight();
                        bitmap = Bitmap.createScaledBitmap(bitmap, (int)((float)bitmap.getWidth()*scale), (int)((float)bitmap.getHeight() * scale), true);
                        ImageView imageView = new ImageView(getContext());
                        imageView.setImageBitmap(bitmap);
                        imageView.setScaleType(ImageView.ScaleType.CENTER);
                        view.post(() -> view.addView(imageView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)));
                    }
                }).start();
            }
        }
        return view;
    }
}
