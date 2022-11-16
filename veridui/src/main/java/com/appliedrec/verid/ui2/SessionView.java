package com.appliedrec.verid.ui2;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.appliedrec.verid.core2.EulerAngle;
import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.ImageUtils;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.core2.session.VerIDSessionResult;

import java.util.Arrays;
import java.util.List;

import io.github.sceneview.SceneView;

/**
 * Default implementation of {@link ISessionView}
 * @since 2.0.0
 */
@Keep
public class SessionView extends BaseSessionView {

    private TextureView textureView;
    private TextView instructionTextView;
    private FaceOvalView faceOvalView;
    private ImageView faceImageView;
    private SceneView headView;
    private OvalMaskView ovalMaskView;
    private MaskedFrameLayout faceViewsContainer;
    private boolean plotFaceLandmarks = false;
    private Long nextAvailableViewChangeTime;
    private Long latestMisalignTime;
    private SpringAnimation[] faceImageViewAnimations = new SpringAnimation[0];
    private boolean isFinishing = false;
    private int faceCaptureCount = 0;

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
        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(this);
        textureView.setId(View.generateViewId());
        LayoutParams textureViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addView(textureView, textureViewLayoutParams);

        ovalMaskView = new OvalMaskView(context);
        ovalMaskView.setId(View.generateViewId());
        LayoutParams ovalMaskViewLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addView(ovalMaskView, ovalMaskViewLayoutParams);

        faceViewsContainer = new MaskedFrameLayout(context);
        faceViewsContainer.setId(View.generateViewId());
        addView(faceViewsContainer, createLayoutParamsWithViewCenteredInParent(200, 250));

        int padding = dpToPx(4);
        instructionTextView = new TextView(getContext());
        instructionTextView.setId(View.generateViewId());
        instructionTextView.setPadding(padding, padding, padding, padding);
        instructionTextView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        instructionTextView.setTextAppearance(R.style.TextAppearance_AppCompat_Headline);
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

        faceImageView = new ImageView(getContext());
        faceImageView.setId(View.generateViewId());
        faceViewsContainer.addView(faceImageView);

        faceOvalView = new FaceOvalView(getContext());
        faceOvalView.setId(View.generateViewId());
        faceViewsContainer.addView(faceOvalView);

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            new Handler(Looper.getMainLooper()).post(() -> {
//                headView = HeadViewSetup.createHeadView(getContext(), Objects.requireNonNull(ViewTreeLifecycleOwner.get(this)));
//                headView.setId(View.generateViewId());
//                faceViewsContainer.addView(headView);
//            });
//        }
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


    private LayoutParams createLayoutParamsWithViewCenteredInParent(int width, int height) {
        LayoutParams layoutParams = new LayoutParams(width, height);
        layoutParams.leftToLeft = LayoutParams.PARENT_ID;
        layoutParams.topToTop = LayoutParams.PARENT_ID;
        layoutParams.rightToRight = LayoutParams.PARENT_ID;
        layoutParams.bottomToBottom = LayoutParams.PARENT_ID;
        return layoutParams;
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
    protected TextView getInstructionTextView() {
        return instructionTextView;
    }

    protected FaceOvalView getFaceOvalView() {
        return faceOvalView;
    }

    @Keep
    @Override
    public void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt) {
        post(() -> {
            if (faceDetectionResult == null) {
                return;
            }
            if (isFinishing || faceCaptureCount >= getSessionSettings().getFaceCaptureCount()) {
                return;
            }
            if (faceDetectionResult.getStatus() != FaceDetectionStatus.FACE_ALIGNED && nextAvailableViewChangeTime != null && nextAvailableViewChangeTime > System.currentTimeMillis()) {
                return;
            }
            getInstructionTextView().setText(prompt);
            getInstructionTextView().setVisibility(prompt != null ? View.VISIBLE : View.GONE);

            textureView.setTransform(getCameraViewMatrixFromFaceDetectionResult(faceDetectionResult));
            maskCameraPreviewFromFaceDetectionResult(faceDetectionResult);
            drawArrowFromFaceDetectionResult(faceDetectionResult);

            // Update face oval size
            updateFaceOvalSizeFromFaceDetectionResult(faceDetectionResult);

            switch (faceDetectionResult.getStatus()) {
                case FACE_ALIGNED:
                    faceCaptureCount ++;
                    latestMisalignTime = null;
                    if (headView != null) {
                        headView.setVisibility(View.GONE);
                    }
                    if (faceDetectionResult.getFace().isPresent()) {
                        try {
                            Bitmap image = faceDetectionResult.getImage().provideBitmap();
                            Face face = faceDetectionResult.getFace().get();
                            Bitmap faceImage = ImageUtils.cropImageToFace(image, face);
                            if (isCameraPreviewMirrored()) {
                                Matrix matrix = new Matrix();
                                matrix.setScale(-1, 1);
                                faceImage = Bitmap.createBitmap(faceImage, 0, 0, faceImage.getWidth(), faceImage.getHeight(), matrix, false);
                            }
                            faceImageView.setImageBitmap(faceImage);
                            faceImageView.setVisibility(View.VISIBLE);
                            textureView.setVisibility(View.INVISIBLE);
                            for (SpringAnimation animation : faceImageViewAnimations) {
                                animation.cancel();
                            }
                            SpringForce springForce = new SpringForce(0).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
                            DynamicAnimation.ViewProperty[] animatedProperties = new DynamicAnimation.ViewProperty[2];
                            animatedProperties[0] = DynamicAnimation.SCALE_X;
                            animatedProperties[1] = DynamicAnimation.SCALE_Y;
                            int i=0;
                            faceImageViewAnimations = new SpringAnimation[2];
                            for (DynamicAnimation.ViewProperty property : animatedProperties) {
                                faceImageViewAnimations[i++] = new SpringAnimation(faceImageView, property).setSpring(springForce).setStartValue(0.9f);
                            }
                            faceImageViewAnimations[0].addEndListener((DynamicAnimation animation, boolean canceled, float value, float velocity) -> {
                                if (faceCaptureCount < getSessionSettings().getFaceCaptureCount()) {
                                    faceImageView.setVisibility(View.INVISIBLE);
                                    textureView.setVisibility(View.VISIBLE);
                                } else {
                                    getInstructionTextView().setVisibility(View.GONE);
                                    showAnimatedProgressOnImageViewUsingFace(face);
                                }
                                faceImageViewAnimations = new SpringAnimation[0];
                            });
                            for (SpringAnimation animation : faceImageViewAnimations) {
                                animation.animateToFinalPosition(1f);
                            }
                            nextAvailableViewChangeTime = System.currentTimeMillis() + 1000;
                        } catch (VerIDCoreException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case FACE_FIXED:
                    latestMisalignTime = null;
                    faceOvalView.setVisibility(View.INVISIBLE);
                    if (headView != null) {
                        headView.setVisibility(View.INVISIBLE);
                    }
                    break;
                case FACE_MISALIGNED:
                    faceOvalView.setVisibility(View.VISIBLE);
                    if (headView != null) {
                        if (shouldDisplayCGHeadGuidance() && latestMisalignTime != null && latestMisalignTime + 2000 < System.currentTimeMillis() && faceDetectionResult.getFaceAngle().isPresent() && faceDetectionResult.getRequestedAngle().isPresent()) {
                            long turnDuration = 1000;
                            headView.setVisibility(View.VISIBLE);
                            textureView.setVisibility(View.INVISIBLE);
                            nextAvailableViewChangeTime = System.currentTimeMillis() + turnDuration;
                            HeadViewSetup.animateHead(headView, faceDetectionResult.getFaceAngle().get(), faceDetectionResult.getRequestedAngle().get(), turnDuration, () -> {
                                headView.setVisibility(View.INVISIBLE);
                                faceOvalView.setVisibility(View.VISIBLE);
                                textureView.setVisibility(View.VISIBLE);
                                latestMisalignTime = null;
                            });
                        } else {
                            headView.setVisibility(View.INVISIBLE);
                        }
                    }
                    if (latestMisalignTime == null) {
                        latestMisalignTime = System.currentTimeMillis();
                    }
                    break;
                default:
                    latestMisalignTime = null;
                    if (headView != null) {
                        headView.setVisibility(View.INVISIBLE);
                    }
                    if (faceDetectionResult.getFaceBounds().isPresent()) {
                        faceOvalView.setVisibility(View.VISIBLE);
                        faceOvalView.setStrokeVisible(true);
                    } else {
                        faceOvalView.setVisibility(View.INVISIBLE);
                    }
            }







            // OLD
//            if (faceDetectionResult == null) {
//                getDetectedFaceView().setFaceRect(null, null, Color.WHITE, getOverlayBackgroundColor(), 0.0, 0.0);
//                getDetectedFaceView().setFaceLandmarks(null);
//                getInstructionTextView().setText(null);
//                getInstructionTextView().setVisibility(View.GONE);
//                return;
//            }
//            getInstructionTextView().setText(prompt);
//            getInstructionTextView().setVisibility(prompt != null ? View.VISIBLE : View.GONE);
//            RectF ovalBounds;
//            @Nullable RectF cutoutBounds;
//            @Nullable EulerAngle faceAngle;
//            RectF defaultFaceBounds = faceDetectionResult.getDefaultFaceBounds().translatedToImageSize(faceDetectionResult.getImageSize());
//            switch (faceDetectionResult.getStatus()) {
//                case FACE_FIXED:
//                case FACE_ALIGNED:
//                case FACE_TURNED_TOO_FAR:
//                    ovalBounds = faceDetectionResult.getFaceBounds().orElse(defaultFaceBounds);
//                    cutoutBounds = new RectF(ovalBounds);
//                    faceAngle = null;
//                    break;
//                case FACE_MISALIGNED:
//                    ovalBounds = faceDetectionResult.getFaceBounds().orElse(defaultFaceBounds);
//                    cutoutBounds = new RectF(ovalBounds);
//                    faceAngle = faceDetectionResult.getFaceAngle().orElse(null);
//                    break;
//                default:
//                    ovalBounds = defaultFaceBounds;
//                    cutoutBounds = new RectF(faceDetectionResult.getFaceBounds().orElse(defaultFaceBounds));
//                    faceAngle = null;
//            }
//            try {
//                float scale = Math.max((float)getDetectedFaceView().getWidth() / (float)faceDetectionResult.getImageSize().width, (float)getDetectedFaceView().getHeight() / (float)faceDetectionResult.getImageSize().height);
//                faceBoundsMatrix.reset();
//                faceBoundsMatrix.setScale(scale, scale);
//                faceBoundsMatrix.postTranslate((float)getDetectedFaceView().getWidth() / 2f - (float)faceDetectionResult.getImageSize().width * scale / 2f, (float)getDetectedFaceView().getHeight() / 2f - (float)faceDetectionResult.getImageSize().height * scale / 2f);
//
//                faceBoundsMatrix.mapRect(ovalBounds);
//                faceBoundsMatrix.mapRect(cutoutBounds);
//                int colour = getOvalColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
//                int textColour = getTextColourFromFaceDetectionStatus(faceDetectionResult.getStatus());
//                getInstructionTextView().setTextColor(textColour);
//                getInstructionTextView().setBackgroundColor(colour);
//
//                ((LayoutParams)getInstructionTextView().getLayoutParams()).topMargin = Math.max(0, (int) (ovalBounds.top - getInstructionTextView().getHeight() - getResources().getDisplayMetrics().density * 16f));
//                setTextViewColour(colour, textColour);
//                Double angle = null;
//                Double distance = null;
//                EulerAngle offsetAngleFromBearing = faceDetectionResult.getOffsetAngleFromBearing();
//                if (faceAngle != null && offsetAngleFromBearing != null) {
//                    angle = Math.atan2(offsetAngleFromBearing.getPitch(), offsetAngleFromBearing.getYaw());
//                    distance = Math.hypot(offsetAngleFromBearing.getYaw(), 0 - offsetAngleFromBearing.getPitch()) * 2;
//                }
//                getDetectedFaceView().setFaceRect(ovalBounds, cutoutBounds, colour, getOverlayBackgroundColor(), angle, distance);
//                if (shouldPlotFaceLandmarks() && faceDetectionResult.getFaceLandmarks().map(landmarks -> landmarks.length).orElse(0) > 0) {
//                    float[] landmarks = new float[faceDetectionResult.getFaceLandmarks().get().length*2];
//                    int i=0;
//                    for (PointF pt : faceDetectionResult.getFaceLandmarks().get()) {
//                        landmarks[i++] = pt.x;
//                        landmarks[i++] = pt.y;
//                    }
//                    faceBoundsMatrix.mapPoints(landmarks);
//                    PointF[] pointLandmarks = new PointF[faceDetectionResult.getFaceLandmarks().get().length];
//                    for (i=0; i<pointLandmarks.length; i++) {
//                        pointLandmarks[i] = new PointF(landmarks[i*2], landmarks[i*2+1]);
//                    }
//                    getDetectedFaceView().setFaceLandmarks(pointLandmarks);
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        });
    }

    private void showAnimatedProgressOnImageViewUsingFace(Face face) {
        ProgressBar progressBar = new ProgressBar(getContext());
        faceViewsContainer.addView(progressBar, createLayoutParamsWithViewCenteredInParent(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
//        FaceProcessingIndicatorView processingIndicatorView = new FaceProcessingIndicatorView(getContext());
//        faceViewsContainer.addView(processingIndicatorView, createLayoutParamsWithViewCenteredInParent(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
//        if (isCameraPreviewMirrored()) {
//            face = face.flipped(new Size(faceViewsContainer.getWidth(), faceViewsContainer.getHeight()));
//        }
//        processingIndicatorView.setFace(face);
    }

    private boolean shouldDisplayCGHeadGuidance() {
        return true;
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
//        getFaceImagesView().removeAllViews();
//        int margin = dpToPx(8);
//        int height = getCapturedFaceImageHeight();
//        for (Drawable drawable : faceImages) {
//            ImageView imageView = new ImageView(getContext());
//            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
//            imageView.setImageDrawable(drawable);
//            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
//            layoutParams.leftMargin = margin;
//            layoutParams.rightMargin = margin;
//            getFaceImagesView().addView(imageView, layoutParams);
//        }
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

    @Override
    public void willFinishWithResult(VerIDSessionResult result, Runnable completionCallback) {
        isFinishing = true;
        instructionTextView.setVisibility(View.GONE);
        if (!result.getError().isPresent() && result.getFaceCaptures().length > 0) {
            faceOvalView.setVisibility(View.GONE);
            faceImageView.setVisibility(View.VISIBLE);
            onCompletionAnimateView(faceImageView, completionCallback);
        } else {
            faceImageView.setVisibility(View.GONE);
            faceOvalView.setVisibility(View.VISIBLE);
            onCompletionAnimateView(faceOvalView, completionCallback);
        }
    }

    private void onCompletionAnimateView(View view, Runnable completionCallback) {
        for (SpringAnimation animation : faceImageViewAnimations) {
            animation.cancel();
        }
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, 0.0f);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, 0.0f);
        AnimatorSet scaleAnimator = new AnimatorSet();
        scaleAnimator.setDuration(1000);
        scaleAnimator.setInterpolator(new DecelerateInterpolator());
        scaleAnimator.playTogether(scaleXAnimator, scaleYAnimator);
        scaleAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                completionCallback.run();
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                completionCallback.run();
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {

            }
        });
        scaleAnimator.start();

//        SpringForce springForce = new SpringForce(0).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
//        DynamicAnimation.ViewProperty[] animatedProperties = new DynamicAnimation.ViewProperty[2];
//        animatedProperties[0] = DynamicAnimation.SCALE_X;
//        animatedProperties[1] = DynamicAnimation.SCALE_Y;
//        int i=0;
//        faceImageViewAnimations = new SpringAnimation[2];
//        for (DynamicAnimation.ViewProperty property : animatedProperties) {
//            faceImageViewAnimations[i++] = new SpringAnimation(view, property).setSpring(springForce).setStartValue(1.0f);
//        }
//        faceImageViewAnimations[0].addEndListener((DynamicAnimation animation, boolean canceled, float value, float velocity) -> {
//            faceImageViewAnimations = new SpringAnimation[0];
//            completionCallback.run();
//        });
//        for (SpringAnimation animation : faceImageViewAnimations) {
//            animation.animateToFinalPosition(0.01f);
//        }
    }

    private void updateFaceOvalSizeFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        Rect faceOvalBounds = new Rect();
        faceDetectionResult.getDefaultFaceBounds().translatedToImageSize(getViewSize()).round(faceOvalBounds);
        LayoutParams faceOvalLayoutParams = new LayoutParams(faceViewsContainer.getLayoutParams());
        faceOvalLayoutParams.width = faceOvalBounds.width();
        faceOvalLayoutParams.height = faceOvalBounds.height();
        faceViewsContainer.setLayoutParams(faceOvalLayoutParams);
    }

    private void drawArrowFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        EulerAngle offsetAngle = faceDetectionResult.getOffsetAngleFromBearing();
        if (faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_MISALIGNED && offsetAngle != null) {
            float angle = (float)Math.atan2(offsetAngle.getPitch(), offsetAngle.getYaw());
            float distance = (float)Math.hypot(offsetAngle.getYaw(), offsetAngle.getPitch()) * 2f;
            faceOvalView.setVisibility(View.VISIBLE);
            faceOvalView.setStrokeVisible(false);
            faceOvalView.drawArrow(angle, distance);
        } else {
            faceOvalView.setVisibility(View.GONE);
            faceOvalView.removeArrow();
        }
    }

    private RectF getDefaultFaceRectFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        return faceDetectionResult.getDefaultFaceBounds().translatedToImageSize(getViewSize());
    }

    private Matrix getCameraViewMatrixFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        Matrix matrix = getCameraPreviewMatrix();
        switch (faceDetectionResult.getStatus()) {
            case FACE_FIXED:
            case FACE_ALIGNED:
            case FACE_MISALIGNED:
                RectF faceBounds = getFaceBoundsFromFaceDetectionResult(faceDetectionResult);
                if (faceBounds == null) {
                    return matrix;
                }
                RectF faceRect = getDefaultFaceRectFromFaceDetectionResult(faceDetectionResult);
                matrix.setRectToRect(faceBounds, faceRect, Matrix.ScaleToFit.CENTER);
                break;
        }
        return matrix;
    }

    private RectF getFaceBoundsFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        if (!faceDetectionResult.getFaceBounds().isPresent()) {
            return null;
        }
        Size imageSize = faceDetectionResult.getImageSize();
        Size viewSize = getViewSize();
        float scale = Math.max(viewSize.width / imageSize.width, viewSize.height / imageSize.height);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((float) viewSize.width / 2f - (float) imageSize.width * scale / 2f, (float) viewSize.height / 2f - (float) imageSize.height * scale / 2f);
        RectF bounds = new RectF(faceDetectionResult.getFaceBounds().get());
        matrix.mapRect(bounds);
        return bounds;
    }

    private void maskCameraPreviewFromFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        RectF defaultFaceBounds = getDefaultFaceRectFromFaceDetectionResult(faceDetectionResult);
        switch (faceDetectionResult.getStatus()) {
            case FACE_FIXED:
            case FACE_MISALIGNED:
            case FACE_ALIGNED:
                maskCameraPreviewWithOvalInBounds(defaultFaceBounds);
                break;
            default:
                RectF bounds = getFaceBoundsFromFaceDetectionResult(faceDetectionResult);
                if (bounds != null) {
                    maskCameraPreviewWithOvalInBounds(bounds);
                } else {
                    maskCameraPreviewWithOvalInBounds(defaultFaceBounds);
                }
        }
    }

    private void maskCameraPreviewWithOvalInBounds(RectF bounds) {
        this.ovalMaskView.setMaskRect(bounds);
    }
}
