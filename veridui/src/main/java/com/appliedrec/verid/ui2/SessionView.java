package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.appliedrec.verid.core2.EulerAngle;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link ISessionView}
 * @since 2.0.0
 */
@Keep
public class SessionView extends FrameLayout implements ISessionView, TextureView.SurfaceTextureListener, View.OnLayoutChangeListener {

    private TextureView textureView;
    private DetectedFaceView detectedFaceView;
    private TextView instructionTextView;
    private LinearLayout faceImagesView;
    private boolean plotFaceLandmarks = false;
    private final Matrix faceBoundsMatrix = new Matrix();
    private @ColorInt int overlayBackgroundColor = 0x80000000;
    private @ColorInt int ovalColor = 0xFFFFFFFF;
    private @ColorInt int ovalColorHighlighted = 0xFF36AF00;
    private @ColorInt int textColor = 0xFF000000;
    private @ColorInt int textColorHighlighted = 0xFFFFFFFF;
    private final AtomicBoolean isSurfaceAvailable = new AtomicBoolean(false);
    private final AtomicReference<FaceExtents> defaultFaceExtents = new AtomicReference<>();
    private final AtomicReference<Size> viewSizeRef = new AtomicReference<>();
    private final HashSet<SessionViewListener> listeners = new HashSet<>();
    private final Object listenerLock = new Object();

    /**
     * Constructor
     * @param context Context
     * @since 2.0.0
     */
    @Keep
    public SessionView(@NonNull Context context) {
        super(context);
        init();
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
        init();
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
        super(context, attrs, defStyleAttr);
        init();
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
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /**
     * Get preview class – used to determine camera preview sizes
     * @return {@code SurfaceTexture.class}
     * @since 2.0.0
     */
    @Keep
    @Override
    public Class<?> getPreviewClass() {
        return SurfaceTexture.class;
    }

    private void init() {
        defaultFaceExtents.set(new LivenessDetectionSessionSettings().getExpectedFaceExtents());
        textureView = new TextureView(getContext());
        textureView.setSurfaceTextureListener(this);
        LayoutParams textureViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textureViewLayoutParams.gravity = Gravity.CENTER;
        addView(textureView, textureViewLayoutParams);

        detectedFaceView = new DetectedFaceView(getContext());
        LayoutParams detectedFaceViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        detectedFaceViewLayoutParams.gravity = Gravity.CENTER;
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
        instructionTextViewLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        instructionTextViewLayoutParams.setMarginStart(dpToPx(16));
        instructionTextViewLayoutParams.setMarginEnd(dpToPx(16));
        instructionTextViewLayoutParams.topMargin = dpToPx(32);
        addView(instructionTextView, instructionTextViewLayoutParams);

        faceImagesView = new LinearLayout(getContext());
        faceImagesView.setOrientation(LinearLayout.HORIZONTAL);
        faceImagesView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        LayoutParams faceImagesViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        faceImagesViewLayoutParams.bottomMargin = dpToPx(32);
        faceImagesViewLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        addView(faceImagesView, faceImagesViewLayoutParams);
    }

    @Keep
    @Override
    public void addListener(SessionViewListener listener) {
        synchronized (listenerLock) {
            listeners.add(listener);
        }
        if (isSurfaceAvailable.get()) {
            listener.onPreviewSurfaceCreated(new Surface(textureView.getSurfaceTexture()));
        }
    }

    @Keep
    @Override
    public void removeListener(SessionViewListener listener) {
        synchronized (listenerLock) {
            listeners.remove(listener);
        }
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

    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int)(density * dp);
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
    public void setDefaultFaceExtents(@NonNull FaceExtents faceExtents) {
        defaultFaceExtents.set(faceExtents);
    }

    @Keep
    public FaceExtents getDefaultFaceExtents() {
        return defaultFaceExtents.get();
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

                ((FrameLayout.LayoutParams)getInstructionTextView().getLayoutParams()).topMargin = Math.max(0, (int) (ovalBounds.top - getInstructionTextView().getHeight() - getResources().getDisplayMetrics().density * 16f));
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

    @Keep
    @Override
    public void setPreviewSize(int width, int height, int sensorOrientation) {
        Matrix matrix = CameraPreviewHelper.getViewTransformMatrix(width, height, getTextureView().getWidth(), getTextureView().getHeight(), sensorOrientation, getDisplayRotation());
        getTextureView().setTransform(matrix);
    }

    //region Appearance

    /**
     * Override to change the way the colour of the oval around the detected face is determined from face detection status
     * @param faceDetectionStatus Face detection status on which to base the oval colour
     * @return Colour of the face oval stroke
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    protected int getOvalColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus) {
        switch (faceDetectionStatus) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return getOvalColorHighlighted();
            default:
                return getOvalColor();
        }
    }

    /**
     * Override to change the way the colour of the session prompt text is determined from face detection status
     * @param faceDetectionStatus Face detection status on which to base the text colour
     * @return Colour of the session prompt text
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    protected int getTextColourFromFaceDetectionStatus(FaceDetectionStatus faceDetectionStatus) {
        switch (faceDetectionStatus) {
            case FACE_FIXED:
            case FACE_ALIGNED:
                return getTextColorHighlighted();
            default:
                return getTextColor();
        }
    }

    /**
     * Get display rotation
     * @return Display rotation in degrees
     * @since 2.0.0
     */
    @Keep
    @Override
    @IntRange(from = 0, to = 359)
    public int getDisplayRotation() {
        switch (getDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                return 0;
            case Surface.ROTATION_90:
                return  90;
            case Surface.ROTATION_180:
                return  180;
            case Surface.ROTATION_270:
                return 270;
        }
    }

    /**
     * @return Colour of the background around the detected face overlaid on top of the camera view finder
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    public int getOverlayBackgroundColor() {
        return overlayBackgroundColor;
    }

    /**
     * @param overlayBackgroundColor Colour of the background around the detected face overlaid on top of the camera view finder
     * @since 2.0.0
     */
    @Keep
    public void setOverlayBackgroundColor(@ColorInt int overlayBackgroundColor) {
        this.overlayBackgroundColor = overlayBackgroundColor;
    }

    /**
     * @return Colour of the face oval
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    public int getOvalColor() {
        return ovalColor;
    }

    /**
     * @param ovalColor Colour of the face oval
     * @since 2.0.0
     */
    @Keep
    public void setOvalColor(@ColorInt int ovalColor) {
        this.ovalColor = ovalColor;
    }

    /**
     * @return Colour of the face oval when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    public int getOvalColorHighlighted() {
        return ovalColorHighlighted;
    }

    /**
     * @param ovalColorHighlighted Colour of the face oval when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    @Keep
    public void setOvalColorHighlighted(@ColorInt int ovalColorHighlighted) {
        this.ovalColorHighlighted = ovalColorHighlighted;
    }

    /**
     * @return Colour of the text that displays prompts
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    public int getTextColor() {
        return textColor;
    }

    /**
     * @param textColor Colour of the text that displays prompts
     * @since 2.0.0
     */
    @Keep
    public void setTextColor(@ColorInt int textColor) {
        this.textColor = textColor;
    }

    /**
     * @return Colour of the prompt text when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    @Keep
    @ColorInt
    public int getTextColorHighlighted() {
        return textColorHighlighted;
    }

    /**
     * @param textColorHighlighted Colour of the prompt text when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     */
    @Keep
    public void setTextColorHighlighted(@ColorInt int textColorHighlighted) {
        this.textColorHighlighted = textColorHighlighted;
    }

    //endregion

    //region Surface texture listener

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        isSurfaceAvailable.set(true);
        synchronized (listenerLock) {
            Surface surface = new Surface(surfaceTexture);
            for (SessionViewListener listener : listeners) {
                listener.onPreviewSurfaceCreated(surface);
            }
        }
        onViewSizeUpdate();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        isSurfaceAvailable.set(false);
        synchronized (listenerLock) {
            for (SessionViewListener listener : listeners) {
                listener.onPreviewSurfaceDestroyed();
            }
            listeners.clear();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    //endregion

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public FaceBounds next() {
        Size viewSize = viewSizeRef.get();
        if (viewSize == null) {
            viewSize = new Size(0, 0);
        }
        return new FaceBounds(viewSize, getDefaultFaceExtents());
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        onViewSizeUpdate();
    }

    private void onViewSizeUpdate() {
        final Size viewSize = new Size(getWidth(), getHeight());
        viewSizeRef.set(viewSize);
    }
}
