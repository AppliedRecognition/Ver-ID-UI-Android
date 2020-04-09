package com.appliedrec.verid.ui;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceCapture;
import com.appliedrec.verid.core.RecognizableFace;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerIDSessionSettings;

import java.util.Objects;

public class VerIDRegistrationSessionFragment extends VerIDSessionFragment<RegistrationSessionSettings, RecognizableFace> {

    private LinearLayout detectedFacesView;
    private int addedFaceCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup view = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        inflater.inflate(R.layout.detected_faces_view, view, true);
        detectedFacesView = Objects.requireNonNull(view).findViewById(R.id.detectedFacesLayout);
        ConstraintLayout.LayoutParams facesViewLayoutParams = new ConstraintLayout.LayoutParams(detectedFacesView.getLayoutParams());
        facesViewLayoutParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
        facesViewLayoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT;
        facesViewLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        facesViewLayoutParams.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
        facesViewLayoutParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        detectedFacesView.setLayoutParams(facesViewLayoutParams);

        for (int i=0; i<getNumberOfResultsToCollect(); i++) {
            ImageView imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 51, getResources().getDisplayMetrics()), (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics()));
            layoutParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            imageView.setAlpha(0.5f);
            imageView.setImageDrawable(getResources().getDrawable(R.drawable.rounded_corner_white));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            detectedFacesView.addView(imageView, layoutParams);
        }
        return view;
    }

    @Override
    public void onFaceCapture(FaceCapture<Face> faceCapture) {
        super.onFaceCapture(faceCapture);
        try {
            if (detectedFacesView == null || getContext() == null) {
                return;
            }
            final Point faceViewSize;
            if (detectedFacesView.getChildCount() > 0) {
                faceViewSize = new Point(detectedFacesView.getChildAt(0).getWidth(), detectedFacesView.getChildAt(0).getHeight());
            } else {
                faceViewSize = null;
            }
            final ImageView imageView = (ImageView) detectedFacesView.getChildAt(addedFaceCount++);
            if (imageView == null) {
                return;
            }
            final Face face = faceCapture.getFace();
            AsyncTask.execute(() -> {
                Bitmap bitmap = faceCapture.getImage();
                Rect rect = new Rect();
                face.getBounds().round(rect);
                rect.bottom = Math.min(rect.bottom, bitmap.getHeight());
                rect.top = Math.max(rect.top, 0);
                rect.right = Math.min(rect.right, bitmap.getWidth());
                rect.left = Math.max(rect.left, 0);
                bitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
                if (getRequestedFacingOfLens() == VerIDSessionSettings.LensFacing.FRONT) {
                    Matrix matrix = new Matrix();
                    matrix.setScale(-1, 1);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                }
                if (faceViewSize != null && faceViewSize.x > 0 && faceViewSize.y > 0) {
                    double viewAspectRatio = (double) faceViewSize.x / (double) faceViewSize.y;
                    double imageAspectRatio = (double) bitmap.getWidth() / (double) bitmap.getHeight();
                    int width;
                    int height;
                    if (viewAspectRatio > imageAspectRatio) {
                        width = faceViewSize.x;
                        height = (int) ((double) faceViewSize.x / imageAspectRatio);
                    } else {
                        height = faceViewSize.y;
                        width = (int) ((double) faceViewSize.y * imageAspectRatio);
                    }
                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                }
                final RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                runOnUIThread(() -> {
                    if (isAdded() && !isRemoving()) {
                        int cornerRadius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                        drawable.setCornerRadius(cornerRadius);
                        imageView.setImageDrawable(drawable);
                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        imageView.setAlpha(1.0f);
                    }
                });
            });
        } catch (Exception ignore) {}
    }

    @Override
    public void hideCameraOverlay() {
        super.hideCameraOverlay();
        runOnUIThread(() -> {
            if (isAdded() && !isRemoving()) {
                detectedFacesView.removeAllViews();
            }
        });
    }

    private void runOnUIThread(Runnable runnable) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(runnable);
        }
    }
}
