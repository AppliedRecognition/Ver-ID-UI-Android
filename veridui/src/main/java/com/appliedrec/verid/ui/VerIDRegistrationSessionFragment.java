package com.appliedrec.verid.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.appliedrec.verid.core.DetectedFace;
import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;

public class VerIDRegistrationSessionFragment extends VerIDSessionFragment {

    LinearLayout detectedFacesView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        inflater.inflate(R.layout.detected_faces_view, getViewOverlays(), true);
        detectedFacesView = getViewOverlays().findViewById(R.id.detectedFacesLayout);
        RelativeLayout.LayoutParams facesViewLayoutParams = new RelativeLayout.LayoutParams(detectedFacesView.getLayoutParams());
        facesViewLayoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        facesViewLayoutParams.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        facesViewLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        facesViewLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        detectedFacesView.setLayoutParams(facesViewLayoutParams);
        facesViewLayoutParams.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, getResources().getDisplayMetrics());
        return view;
    }

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, EulerAngle offsetAngleFromBearing) {
        super.drawFaceFromResult(faceDetectionResult, sessionResult, defaultFaceBounds, offsetAngleFromBearing);
        if (getDelegate() == null) {
            return;
        }
        final RegistrationSessionSettings sessionSettings = (RegistrationSessionSettings)getDelegate().getSessionSettings();
        if (detectedFacesView.getChildCount() < sessionSettings.getNumberOfResultsToCollect()) {
            detectedFacesView.removeAllViews();
            for (int i=0; i<sessionSettings.getNumberOfResultsToCollect(); i++) {
                ImageView imageView = new ImageView(getContext());
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 51, getResources().getDisplayMetrics()), (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics()));
                if (i+1 != sessionSettings.getNumberOfResultsToCollect()) {
                    layoutParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                }
                imageView.setAlpha(0.5f);
                imageView.setImageDrawable(getResources().getDrawable(R.drawable.rounded_corner_white));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                detectedFacesView.addView(imageView, layoutParams);
            }
        }
        if (sessionResult.getError() != null || faceDetectionResult.getStatus() != FaceDetectionStatus.FACE_ALIGNED) {
            return;
        }
        if (detectedFacesView == null || sessionSettings == null || getContext() == null) {
            return;
        }
        final DetectedFace[] attachments = sessionResult.getAttachments();
        final Point faceViewSize;
        if (detectedFacesView.getChildCount() > 0) {
            faceViewSize = new Point(detectedFacesView.getChildAt(0).getWidth(), detectedFacesView.getChildAt(0).getHeight());
        } else {
            faceViewSize = null;
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {

            }
        });
        for (int i=0; i<sessionSettings.getNumberOfResultsToCollect(); i++) {
            final ImageView imageView = (ImageView) detectedFacesView.getChildAt(i);
            if (imageView == null || imageView.getAlpha() == 1.0f) {
                continue;
            }
            if (i<attachments.length) {
                final Uri imageUri = attachments[i].getImageUri();
                if (imageUri != null) {
                    final Face face = attachments[i].getFace();
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            Bitmap bitmap = BitmapFactory.decodeFile(imageUri.getPath());
                            if (bitmap != null) {
                                Rect rect = new Rect();
                                face.getBounds().round(rect);
                                rect.bottom = Math.min(rect.bottom, bitmap.getHeight());
                                rect.top = Math.max(rect.top, 0);
                                rect.right = Math.min(rect.right, bitmap.getWidth());
                                rect.left = Math.max(rect.left, 0);
                                bitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
                                if (sessionSettings.getFacingOfCameraLens() == VerIDSessionSettings.LensFacing.FRONT) {
                                    Matrix matrix = new Matrix();
                                    matrix.setScale(-1, 1);
                                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                                }
                                if (faceViewSize != null && faceViewSize.x > 0 && faceViewSize.y > 0) {
                                    double viewAspectRatio = (double)faceViewSize.x/(double)faceViewSize.y;
                                    double imageAspectRatio = (double)bitmap.getWidth()/(double)bitmap.getHeight();
                                    int width;
                                    int height;
                                    if (viewAspectRatio > imageAspectRatio) {
                                        width = faceViewSize.x;
                                        height = (int)((double)faceViewSize.x / imageAspectRatio);
                                    } else {
                                        height = faceViewSize.y;
                                        width = (int)((double)faceViewSize.y * imageAspectRatio);
                                    }
                                    bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                                }
                                final RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                                runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isAdded() && !isRemoving()) {
                                            int cornerRadius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                                            drawable.setCornerRadius(cornerRadius);
                                            imageView.setImageDrawable(drawable);
                                            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                            imageView.setAlpha(1.0f);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            } else {
                imageView.setAlpha(0.5f);
                imageView.setImageDrawable(getResources().getDrawable(R.drawable.rounded_corner_white));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }
    }

    @Override
    public void clearCameraOverlay() {
        super.clearCameraOverlay();
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && !isRemoving()) {
                    detectedFacesView.removeAllViews();
                }
            }
        });
    }
}
