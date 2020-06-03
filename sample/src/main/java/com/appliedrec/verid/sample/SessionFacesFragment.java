package com.appliedrec.verid.sample;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.AsyncTask;
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

import com.appliedrec.verid.core.DetectedFace;

import java.io.IOException;
import java.io.InputStream;

public class SessionFacesFragment extends Fragment {

    private static final String ARG_FACES = "faces";
    private final float height = 100;

    public static SessionFacesFragment newInstance(DetectedFace[] faces) {

        Bundle args = new Bundle();
        args.putParcelableArray(ARG_FACES, faces);

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
            DetectedFace[] faces = (DetectedFace[]) args.getParcelableArray(ARG_FACES);
            if (faces != null && getContext() != null) {
                AsyncTask.execute(() -> {
                    for (DetectedFace face : faces) {
                        if (face.getImageUri() != null) {
                            try (InputStream inputStream = getContext().getContentResolver().openInputStream(face.getImageUri())) {
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                if (bitmap != null) {
                                    Rect cropRect = new Rect();
                                    face.getFace().getBounds().round(cropRect);
                                    cropRect.intersect(new Rect(0,0,bitmap.getWidth(),bitmap.getHeight()));
                                    bitmap = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
                                    float height = this.height * getResources().getDisplayMetrics().density;
                                    float scale = height / (float)bitmap.getHeight();
                                    bitmap = Bitmap.createScaledBitmap(bitmap, (int)((float)bitmap.getWidth()*scale), (int)((float)bitmap.getHeight() * scale), true);
                                    ImageView imageView = new ImageView(getContext());
                                    imageView.setImageBitmap(bitmap);
                                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                                    new Handler(Looper.getMainLooper()).post(() -> view.addView(imageView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)));
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }
        return view;
    }
}
