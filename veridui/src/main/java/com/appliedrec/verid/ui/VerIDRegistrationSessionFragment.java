package com.appliedrec.verid.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.SessionResult;
import com.appliedrec.verid.core.SessionSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class VerIDRegistrationSessionFragment extends VerIDSessionFragment {

    LinearLayout detectedFacesView;
    Bearing requestedBearing;
    private SessionSettings sessionSettings;

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
        addDetectedFaceViews();
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        sessionSettings = getDelegate().getSessionSettings();
        addDetectedFaceViews();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        detectedFacesView.removeAllViews();
    }

    private void addDetectedFaceViews() {
        if (detectedFacesView == null || sessionSettings == null || getContext() == null) {
            return;
        }
        for (int i=0; i<sessionSettings.getNumberOfResultsToCollect(); i++) {
            ImageView imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 51, getResources().getDisplayMetrics()), (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics()));
            if (i+1 != sessionSettings.getNumberOfResultsToCollect()) {
                layoutParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            }
            detectedFacesView.addView(imageView, layoutParams);
        }
    }

    @Override
    public void drawFaceFromResult(FaceDetectionResult faceDetectionResult, SessionResult sessionResult, RectF defaultFaceBounds, EulerAngle offsetAngleFromBearing) {
        super.drawFaceFromResult(faceDetectionResult, sessionResult, defaultFaceBounds, offsetAngleFromBearing);
        if (!(sessionResult.getError() == null  && (requestedBearing == null || requestedBearing != faceDetectionResult.getRequestedBearing() || faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_ALIGNED))) {
            return;
        }
        requestedBearing = faceDetectionResult.getRequestedBearing();
        ArrayList<Bitmap> images = new ArrayList<>();
        HashMap<Face, Uri> faceImages = sessionResult.getFaceImages(requestedBearing);
        Iterator<Map.Entry<Face, Uri>> iterator = faceImages.entrySet().iterator();
        Point faceViewSize = null;
        if (detectedFacesView.getChildCount() > 0) {
            faceViewSize = new Point(detectedFacesView.getChildAt(0).getWidth(), detectedFacesView.getChildAt(0).getHeight());
        }
        while (iterator.hasNext()) {
            Map.Entry<Face, Uri> entry = iterator.next();
            Bitmap bitmap = BitmapFactory.decodeFile(entry.getValue().getPath());
            if (bitmap != null) {
                Rect rect = new Rect();
                entry.getKey().getBounds().round(rect);
                rect.bottom = Math.min(rect.bottom, bitmap.getHeight());
                rect.top = Math.max(rect.top, 0);
                rect.right = Math.min(rect.right, bitmap.getWidth());
                rect.left = Math.max(rect.left, 0);
                bitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
                if (faceViewSize != null) {
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
                images.add(bitmap);
            }
        }
        SessionSettings settings = getDelegate().getSessionSettings();
        for (int i=0; i<settings.getNumberOfResultsToCollect(); i++) {
            ImageView imageView = (ImageView) detectedFacesView.getChildAt(i);
            if (imageView == null) {
                return;
            }
            if (i<images.size()) {
                imageView.setAlpha(1.0f);
                Bitmap image;
                //TODO: Handle front camera
//                if (settings.cameraLensFacing == VerIDSessionSettings.CameraLensFacing.FRONT ^ getSettings().cameraMirroring) {
                    Matrix matrix = new Matrix();
                    matrix.setScale(-1, 1);
                    image = Bitmap.createBitmap(images.get(i), 0, 0, images.get(i).getWidth(), images.get(i).getHeight(), matrix, false);
//                } else {
//                    image = images.get(i);
//                }
                RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), image);
                int cornerRadius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
                drawable.setCornerRadius(cornerRadius);
                imageView.setImageDrawable(drawable);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                imageView.setAlpha(0.5f);
                imageView.setImageDrawable(getResources().getDrawable(R.drawable.rounded_corner_white));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }
    }
}
