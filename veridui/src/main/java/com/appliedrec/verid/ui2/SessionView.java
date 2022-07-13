package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.appliedrec.verid.core2.EulerAngle;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.session.FaceDetectionResult;

import java.util.Arrays;
import java.util.List;

/**
 * Default implementation of {@link ISessionView}
 * @since 2.0.0
 */
@Keep
public class SessionView extends BaseSessionView {

    private TextureView textureView;
    private DetectedFaceView detectedFaceView;
    private TextView instructionTextView;
    private LinearLayout faceImagesView;
    private boolean plotFaceLandmarks = false;
    private final Matrix faceBoundsMatrix = new Matrix();

    /**
     * Constructor
     * @param context Context
     * @since 2.0.0
     */
    @Keep
    public SessionView(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Constructor
     * @param context Context
     * @param attrs Attribute set
     * @since 2.0.0
     */
    @Keep
    public SessionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        textureView = new TextureView(getContext());
        textureView.setSurfaceTextureListener(this);
        LayoutParams textureViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
//        textureViewLayoutParams = Gravity.CENTER;
        addView(textureView, textureViewLayoutParams);

        detectedFaceView = new DetectedFaceView(getContext());
        LayoutParams detectedFaceViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
//        detectedFaceViewLayoutParams.gravity = Gravity.CENTER;
        addView(detectedFaceView, detectedFaceViewLayoutParams);

        int padding = dpToPx(4);
        instructionTextView = new TextView(getContext());
        instructionTextView.setPadding(padding, padding, padding, padding);
        instructionTextView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        instructionTextView.setTextAppearance(getContext(), R.style.TextAppearance_AppCompat_Headline);
        instructionTextView.setTextColor(getResources().getColor(android.R.color.black));
        instructionTextView.setBackgroundResource(R.drawable.rounded_corner_white);
        instructionTextView.setVisibility(GONE);
        LayoutParams instructionTextViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionTextViewLayoutParams.leftToLeft = LayoutParams.PARENT_ID;
        instructionTextViewLayoutParams.rightToRight = LayoutParams.PARENT_ID;
        instructionTextViewLayoutParams.topToTop = LayoutParams.PARENT_ID;
        instructionTextViewLayoutParams.setMarginStart(dpToPx(16));
        instructionTextViewLayoutParams.setMarginEnd(dpToPx(16));
        instructionTextViewLayoutParams.topMargin = dpToPx(32);
        addView(instructionTextView, instructionTextViewLayoutParams);

        faceImagesView = new LinearLayout(getContext());
        faceImagesView.setOrientation(LinearLayout.HORIZONTAL);
        faceImagesView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        LayoutParams faceImagesViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        faceImagesViewLayoutParams.bottomMargin = dpToPx(32);
        faceImagesViewLayoutParams.leftToLeft = LayoutParams.PARENT_ID;
        faceImagesViewLayoutParams.rightToRight = LayoutParams.PARENT_ID;
        faceImagesViewLayoutParams.bottomToBottom = LayoutParams.PARENT_ID;
        addView(faceImagesView, faceImagesViewLayoutParams);
    }

    /**
     * Constructor
     * @param context Context
     * @param attrs Attribute set
     * @param defStyleAttr Style
     * @since 2.0.0
     */
    @Keep
    public SessionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs);
    }

    /**
     * Constructor
     * @param context Context
     * @param attrs Attribute set
     * @param defStyleAttr Style
     * @param defStyleRes Style resource
     * @since 2.0.0
     */
    @Keep
    public SessionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs);
    }

    /**
     * @return {@literal true} if the overlay should include 68 face landmarks (default {@literal false})
     * @since 2.0.0
     */
    @Keep
    public boolean shouldPlotFaceLandmarks() {
        return plotFaceLandmarks;
    }

    /**
     * @param plotFaceLandmarks Set to {@literal true} to render 68 face landmarks in the face overlay
     * @since 2.0.0
     */
    @Keep
    public void shouldPlotFaceLandmarks(boolean plotFaceLandmarks) {
        this.plotFaceLandmarks = plotFaceLandmarks;
    }

    @Keep
    protected TextureView getTextureView() {
        return textureView;
    }

    @Keep
    protected DetectedFaceView getDetectedFaceView() {
        return detectedFaceView;
    }

    @Keep
    protected TextView getInstructionTextView() {
        return instructionTextView;
    }

    @Keep
    protected LinearLayout getFaceImagesView() {
        return faceImagesView;
    }

    @Keep
    @Override
    public void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt) {
        post(() -> {
            if (faceDetectionResult == null) {
                getDetectedFaceView().setFaceRect(null, null, Color.WHITE, getOverlayBackgroundColor(), 0.0, 0.0);
                getDetectedFaceView().setFaceLandmarks(null);
                getInstructionTextView().setText(null);
                getInstructionTextView().setVisibility(View.GONE);
                return;
            }
            getInstructionTextView().setText(prompt);
            getInstructionTextView().setVisibility(prompt != null ? View.VISIBLE : View.GONE);
            RectF ovalBounds;
            @Nullable RectF cutoutBounds;
            @Nullable EulerAngle faceAngle;
            RectF defaultFaceBounds = faceDetectionResult.getDefaultFaceBounds().translatedToImageSize(faceDetectionResult.getImageSize());
            switch (faceDetectionResult.getStatus()) {
                case FACE_FIXED:
                case FACE_ALIGNED:
                case FACE_TURNED_TOO_FAR:
                    ovalBounds = faceDetectionResult.getFaceBounds().orElse(defaultFaceBounds);
                    cutoutBounds = new RectF(ovalBounds);
                    faceAngle = null;
                    break;
                case FACE_MISALIGNED:
                    ovalBounds = faceDetectionResult.getFaceBounds().orElse(defaultFaceBounds);
                    cutoutBounds = new RectF(ovalBounds);
                    faceAngle = faceDetectionResult.getFaceAngle().orElse(null);
                    break;
                default:
                    ovalBounds = defaultFaceBounds;
                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds().orElse(defaultFaceBounds));
                    faceAngle = null;
            }
            try {
                float scale = Math.max((float)getDetectedFaceView().getWidth() / (float)faceDetectionResult.getImageSize().width, (float)getDetectedFaceView().getHeight() / (float)faceDetectionResult.getImageSize().height);
                faceBoundsMatrix.reset();
                faceBoundsMatrix.setScale(scale, scale);
                faceBoundsMatrix.postTranslate((float)getDetectedFaceView().getWidth() / 2f - (float)faceDetectionResult.getImageSize().width * scale / 2f, (float)getDetectedFaceView().getHeight() / 2f - (float)faceDetectionResult.getImageSize().height * scale / 2f);

                faceBoundsMatrix.mapRect(ovalBounds);
                faceBoundsMatrix.mapRect(cutoutBounds);
                int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
                int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
                getInstructionTextView().setTextColor(textColour);
                getInstructionTextView().setBackgroundColor(colour);

                ((LayoutParams)getInstructionTextView().getLayoutParams()).topMargin = Math.max(0, (int) (ovalBounds.top - getInstructionTextView().getHeight() - getResources().getDisplayMetrics().density * 16f));
                setTextViewColour(colour, textColour);
                Double angle = null;
                Double distance = null;
                EulerAngle offsetAngleFromBearing = faceDetectionResult.getOffsetAngleFromBearing();
                if (faceAngle != null && offsetAngleFromBearing != null) {
                    angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                    distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
                }
                getDetectedFaceView().setFaceRect(ovalBounds, cutoutBounds, colour, getOverlayBackgroundColor(), angle, distance);
                if (shouldPlotFaceLandmarks() && faceDetectionResult.getFaceLandmarks().map(landmarks -> landmarks.length).orElse(0) > 0) {
                    float[] landmarks = new float[faceDetectionResult.getFaceLandmarks().get().length*2];
                    int i=0;
                    for (PointF pt : faceDetectionResult.getFaceLandmarks().get()) {
                        landmarks[i++] = pt.x;
                        landmarks[i++] = pt.y;
                    }
                    faceBoundsMatrix.mapPoints(landmarks);
                    PointF[] pointLandmarks = new PointF[faceDetectionResult.getFaceLandmarks().get().length];
                    for (i=0; i<pointLandmarks.length; i++) {
                        pointLandmarks[i] = new PointF(landmarks[i*2], landmarks[i*2+1]);
                    }
                    getDetectedFaceView().setFaceLandmarks(pointLandmarks);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @UiThread
    private void setTextViewColour(int background, int text) {
        float density = getResources().getDisplayMetrics().density;
        float[] corners = new float[8];
        Arrays.fill(corners, 10 * density);
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(corners, null, null));
        shapeDrawable.setPadding((int)(8f * density), (int)(4f * density), (int)(8f * density), (int)(4f * density));
        shapeDrawable.getPaint().setColor(background);
        getInstructionTextView().setBackground(shapeDrawable);
        getInstructionTextView().setTextColor(text);
    }

    @Keep
    @Override
    @UiThread
    public void drawFaces(List<? extends Drawable> faceImages) {
        getFaceImagesView().removeAllViews();
        int margin = dpToPx(8);
        int height = getCapturedFaceImageHeight();
        for (Drawable drawable : faceImages) {
            ImageView imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageDrawable(drawable);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            layoutParams.leftMargin = margin;
            layoutParams.rightMargin = margin;
            getFaceImagesView().addView(imageView, layoutParams);
        }
    }

    @Keep
    @Override
    public int getCapturedFaceImageHeight() {
        return dpToPx(96);
    }

    @Override
    protected Size getViewSize() {
        return new Size(getWidth(), getHeight());
    }
}
