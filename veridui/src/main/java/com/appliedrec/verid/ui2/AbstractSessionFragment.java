package com.appliedrec.verid.ui2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core2.EulerAngle;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.ui2.databinding.FragmentVeridSessionBinding;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Fragment that displays a preview of the camera feed overlaid with an oval around detected face
 * @since 1.0.0
 */
public abstract class AbstractSessionFragment<Preview extends View> extends Fragment implements BiConsumer<FaceDetectionResult, String> {

    private FragmentVeridSessionBinding viewBinding;
    private final Matrix faceBoundsMatrix = new Matrix();
    private @ColorInt int overlayBackgroundColor = 0x80000000;
    private @ColorInt int ovalColor = 0xFFFFFFFF;
    private @ColorInt int ovalColorHighlighted = 0xFF36AF00;
    private @ColorInt int textColor = 0xFF000000;
    private @ColorInt int textColorHighlighted = 0xFFFFFFFF;
    private boolean plotFaceLandmarks = false;
    private Preview viewFinder;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewBinding = FragmentVeridSessionBinding.inflate(inflater, container, false);
        viewFinder = createPreviewView();
        ConstraintLayout.LayoutParams viewFinderLayoutParams = new ConstraintLayout.LayoutParams(0, 0);
        viewFinderLayoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        viewFinderLayoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        viewFinderLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        viewFinderLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        viewBinding.getRoot().addView(viewFinder, 0, viewFinderLayoutParams);
        return viewBinding.getRoot();
    }

    @Override
    public void onInflate(@NonNull Context context, @NonNull AttributeSet attrs, @Nullable Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
        TypedArray attributes = context.obtainStyledAttributes(attrs,R.styleable.VerIDSessionFragment);
        overlayBackgroundColor = attributes.getColor(R.styleable.VerIDSessionFragment_overlay_background_color, overlayBackgroundColor);
        ovalColor = attributes.getColor(R.styleable.VerIDSessionFragment_oval_color, ovalColor);
        textColor = attributes.getColor(R.styleable.VerIDSessionFragment_text_color, textColor);
        ovalColorHighlighted = attributes.getColor(R.styleable.VerIDSessionFragment_oval_color_highlighted, ovalColorHighlighted);
        textColorHighlighted = attributes.getColor(R.styleable.VerIDSessionFragment_text_color_highlighted, textColorHighlighted);
        attributes.recycle();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
        viewFinder = null;
    }

    protected abstract Preview createPreviewView();

    /**
     * @return Camera view finder view
     * @since 2.0.0
     */
    @UiThread
    public Optional<Preview> getViewFinder() {
        return Optional.ofNullable(viewFinder);
    }

    //region Appearance

    /**
     * @return Colour of the background around the detected face overlaid on top of the camera view finder
     * @since 2.0.0
     */
    public int getOverlayBackgroundColor() {
        return overlayBackgroundColor;
    }

    /**
     * @param overlayBackgroundColor Colour of the background around the detected face overlaid on top of the camera view finder
     * @since 2.0.0
     */
    public void setOverlayBackgroundColor(int overlayBackgroundColor) {
        this.overlayBackgroundColor = overlayBackgroundColor;
    }

    /**
     * @return Colour of the face oval
     * @since 2.0.0
     */
    public int getOvalColor() {
        return ovalColor;
    }

    /**
     * @param ovalColor Colour of the face oval
     * @since 2.0.0
     */
    public void setOvalColor(int ovalColor) {
        this.ovalColor = ovalColor;
    }

    /**
     * @return Colour of the face oval when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    public int getOvalColorHighlighted() {
        return ovalColorHighlighted;
    }

    /**
     * @param ovalColorHighlighted Colour of the face oval when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    public void setOvalColorHighlighted(int ovalColorHighlighted) {
        this.ovalColorHighlighted = ovalColorHighlighted;
    }

    /**
     * @return Colour of the text that displays prompts
     * @since 2.0.0
     */
    public int getTextColor() {
        return textColor;
    }

    /**
     * @param textColor Colour of the text that displays prompts
     * @since 2.0.0
     */
    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    /**
     * @return Colour of the prompt text when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    public int getTextColorHighlighted() {
        return textColorHighlighted;
    }

    /**
     * @param textColorHighlighted Colour of the prompt text when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    public void setTextColorHighlighted(int textColorHighlighted) {
        this.textColorHighlighted = textColorHighlighted;
    }

    /**
     * @return {@literal true} if the overlay should include 68 face landmarks (default {@literal false})
     * @since 2.0.0
     */
    public boolean shouldPlotFaceLandmarks() {
        return plotFaceLandmarks;
    }

    /**
     * @param plotFaceLandmarks Set to {@literal true} to render 68 face landmarks in the face overlay
     * @since 2.0.0
     */
    public void setPlotFaceLandmarks(boolean plotFaceLandmarks) {
        this.plotFaceLandmarks = plotFaceLandmarks;
    }

    //endregion

    /**
     * @param faceDetectionResult Face detection result to use for rendering the face oval
     * @param s Prompt text
     * @since 2.0.0
     */
    @UiThread
    @Override
    public void accept(FaceDetectionResult faceDetectionResult, String s) {
        if (viewBinding == null) {
            return;
        }
        if (faceDetectionResult == null) {
            viewBinding.detectedFaceView.setFaceRect(null, null, Color.WHITE, getOverlayBackgroundColor(), 0.0, 0.0);
            viewBinding.detectedFaceView.setFaceLandmarks(null);
            viewBinding.instructionTextview.setText(null);
            viewBinding.instructionTextview.setVisibility(View.GONE);
            return;
        }
        viewBinding.instructionTextview.setText(s);
        viewBinding.instructionTextview.setVisibility(s != null ? View.VISIBLE : View.GONE);
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        RectF defaultFaceBounds = faceDetectionResult.getDefaultFaceBounds().translatedToImageSize(faceDetectionResult.getImageSize());
        switch (faceDetectionResult.getStatus()) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                cutoutBounds = new RectF(faceDetectionResult.getFaceBounds());
                faceAngle = null;
                break;
            case FACE_MISALIGNED:
                ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                cutoutBounds = new RectF(faceDetectionResult.getFaceBounds());
                faceAngle = faceDetectionResult.getFaceAngle();
                break;
            case FACE_TURNED_TOO_FAR:
                ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                cutoutBounds = new RectF(ovalBounds);
                faceAngle = null;
                break;
            default:
                ovalBounds = defaultFaceBounds;
                cutoutBounds = new RectF(faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds);
                faceAngle = null;
        }
        try {
            float scale = Math.max((float)viewBinding.detectedFaceView.getWidth() / (float)faceDetectionResult.getImageSize().width, (float)viewBinding.detectedFaceView.getHeight() / (float)faceDetectionResult.getImageSize().height);
            faceBoundsMatrix.reset();
            faceBoundsMatrix.setScale(scale, scale);
            faceBoundsMatrix.postTranslate((float)viewBinding.detectedFaceView.getWidth() / 2f - (float)faceDetectionResult.getImageSize().width * scale / 2f, (float)viewBinding.detectedFaceView.getHeight() / 2f - (float)faceDetectionResult.getImageSize().height * scale / 2f);

            faceBoundsMatrix.mapRect(ovalBounds);
            faceBoundsMatrix.mapRect(cutoutBounds);
            int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
            int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
            viewBinding.instructionTextview.setTextColor(textColour);
            viewBinding.instructionTextview.setBackgroundColor(colour);

            ((ConstraintLayout.LayoutParams)viewBinding.instructionTextview.getLayoutParams()).topMargin = (int) (ovalBounds.top - viewBinding.instructionTextview.getHeight() - getResources().getDisplayMetrics().density * 16f);
            setTextViewColour(colour, textColour);
            Double angle = null;
            Double distance = null;
            EulerAngle offsetAngleFromBearing = faceDetectionResult.getOffsetAngleFromBearing();
            if (faceAngle != null && offsetAngleFromBearing != null) {
                angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
                distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
            }
            viewBinding.detectedFaceView.setFaceRect(ovalBounds, cutoutBounds, colour, getOverlayBackgroundColor(), angle, distance);
            if (plotFaceLandmarks && faceDetectionResult.getFaceLandmarks() != null && faceDetectionResult.getFaceLandmarks().length > 0) {
                float[] landmarks = new float[faceDetectionResult.getFaceLandmarks().length*2];
                int i=0;
                for (PointF pt : faceDetectionResult.getFaceLandmarks()) {
                    landmarks[i++] = pt.x;
                    landmarks[i++] = pt.y;
                }
                faceBoundsMatrix.mapPoints(landmarks);
                PointF[] pointLandmarks = new PointF[faceDetectionResult.getFaceLandmarks().length];
                for (i=0; i<pointLandmarks.length; i++) {
                    pointLandmarks[i] = new PointF(landmarks[i*2], landmarks[i*2+1]);
                }
                viewBinding.detectedFaceView.setFaceLandmarks(pointLandmarks);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //region Private methods

    private int getOvalColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus) {
        switch (faceDetectionStatus) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return getOvalColorHighlighted();
            default:
                return getOvalColor();
        }
    }

    private int getTextColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus) {
        switch (faceDetectionStatus) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return getTextColorHighlighted();
            default:
                return getTextColor();
        }
    }

    @UiThread
    private void setTextViewColour(int background, int text) {
        if (viewBinding == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float[] corners = new float[8];
        Arrays.fill(corners, 10 * density);
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(corners, null, null));
        shapeDrawable.setPadding((int)(8f * density), (int)(4f * density), (int)(8f * density), (int)(4f * density));
        shapeDrawable.getPaint().setColor(background);
        viewBinding.instructionTextview.setBackground(shapeDrawable);
        viewBinding.instructionTextview.setTextColor(text);
    }

    //endregion
}
