package com.appliedrec.verid.ui2;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseSessionView extends ConstraintLayout implements ISessionView, TextureView.SurfaceTextureListener, View.OnLayoutChangeListener {

    private final HashSet<SessionViewListener> listeners = new HashSet<>();
    private final Object listenerLock = new Object();
    private final AtomicReference<Size> viewSizeRef = new AtomicReference<>();
    private final AtomicReference<FaceExtents> defaultFaceExtents = new AtomicReference<>();
    private final AtomicBoolean isSurfaceAvailable = new AtomicBoolean(false);
    private final Matrix cameraPreviewMatrix = new Matrix();
    private VerIDSessionSettings sessionSettings;
    private boolean isCameraPreviewMirrored = true;
    private AtomicReference<FaceBounds> faceBounds;
    private android.util.Size previewSize = null;

    public BaseSessionView(@NonNull Context context) {
        this(context, null);
    }

    public BaseSessionView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        defaultFaceExtents.set(new LivenessDetectionSessionSettings().getExpectedFaceExtents());
        faceBounds = new AtomicReference<>(new FaceBounds(getViewSize(), defaultFaceExtents.get()));
    }

    @Override
    public void addListener(SessionViewListener listener) {
        synchronized (listenerLock) {
            listeners.add(listener);
        }
        if (isSurfaceAvailable.get()) {
            listener.onPreviewSurfaceCreated(new Surface(getTextureView().getSurfaceTexture()));
        }
    }

    @Override
    public void removeListener(SessionViewListener listener) {
        synchronized (listenerLock) {
            listeners.remove(listener);
        }
    }

    protected abstract Size getViewSize();

    protected abstract TextureView getTextureView();

    @Keep
    public void setDefaultFaceExtents(@NonNull FaceExtents faceExtents) {
        defaultFaceExtents.set(faceExtents);
        faceBounds.set(new FaceBounds(getViewSize(), faceExtents));
    }

    @Override
    public void setSessionSettings(VerIDSessionSettings sessionSettings) {
        this.sessionSettings = sessionSettings;
    }

    public VerIDSessionSettings getSessionSettings() {
        return sessionSettings;
    }

    @Override
    public void setCameraPreviewMirrored(boolean mirrored) {
        this.isCameraPreviewMirrored = mirrored;
    }

    public boolean isCameraPreviewMirrored() {
        return this.isCameraPreviewMirrored;
    }

    /**
     * Get preview class – used to determine camera preview sizes
     * @return {@code SurfaceTexture.class}
     * @since 2.0.0
     */
    @Override
    public Class<?> getPreviewClass() {
        return SurfaceTexture.class;
    }

    @Keep
    public FaceExtents getDefaultFaceExtents() {
        return defaultFaceExtents.get();
    }

    @Override
    public AtomicReference<FaceBounds> getFaceBounds() {
        return faceBounds;
    }

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
        Size viewSize = getViewSize();
        viewSizeRef.set(viewSize);
        if (viewSize == null) {
            viewSize = new Size(0, 0);
        }
        faceBounds.set(new FaceBounds(viewSize, getDefaultFaceExtents()));
    }

    //region Surface texture listener

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        isSurfaceAvailable.set(true);
        synchronized (listenerLock) {
            Surface surface = new Surface(surfaceTexture);
            if (previewSize != null) {
                surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            }
            for (SessionViewListener listener : listeners) {
                listener.onPreviewSurfaceCreated(surface);
            }
        }
        onViewSizeUpdate();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        surface.setDefaultBufferSize(width, height);
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

    @Keep
    @Override
    public void setPreviewSize(int width, int height, int sensorOrientation) {
        synchronized (listenerLock) {
            previewSize = new android.util.Size(width, height);
        }
        cameraPreviewMatrix.set(CameraPreviewHelper.getInstance().getViewTransformMatrix(width, height, getWidth(), getHeight(), sensorOrientation, getDisplayRotation()));
        if (getTextureView() != null) {
            if (getTextureView().getSurfaceTexture() != null) {
                getTextureView().getSurfaceTexture().setDefaultBufferSize(width, height);
            }
            getTextureView().setTransform(cameraPreviewMatrix);
        }
    }

    protected Matrix getCameraPreviewMatrix() {
        return cameraPreviewMatrix;
    }

    protected final int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int)(density * dp);
    }

    /**
     * @return Colour of the background around the detected face overlaid on top of the camera view finder
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
    public int getOverlayBackgroundColor() {
        return 0;
    }

    /**
     * @param overlayBackgroundColor Colour of the background around the detected face overlaid on top of the camera view finder
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @Deprecated
    public void setOverlayBackgroundColor(@ColorInt int overlayBackgroundColor) {}

    /**
     * @return Colour of the face oval
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
    public int getOvalColor() {
        return 0;
    }

    /**
     * @param ovalColor Colour of the face oval
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @Deprecated
    public void setOvalColor(@ColorInt int ovalColor) {}

    /**
     * @return Colour of the face oval when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
    public int getOvalColorHighlighted() {
        return 0;
    }

    /**
     * @param ovalColorHighlighted Colour of the face oval when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @Deprecated
    public void setOvalColorHighlighted(@ColorInt int ovalColorHighlighted) {}

    /**
     * @return Colour of the text that displays prompts
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
    public int getTextColor() {
        return 0;
    }

    /**
     * @param textColor Colour of the text that displays prompts
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @Deprecated
    public void setTextColor(@ColorInt int textColor) {}

    /**
     * @return Colour of the prompt text when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
    public int getTextColorHighlighted() {
        return 0;
    }

    /**
     * @param textColorHighlighted Colour of the prompt text when highlighted (e.g., face is aligned according to instructions)
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @Deprecated
    public void setTextColorHighlighted(@ColorInt int textColorHighlighted) {}

    //endregion

    //region Appearance

    /**
     * Override to change the way the colour of the oval around the detected face is determined from face detection status
     * @param faceDetectionStatus Face detection status on which to base the oval colour
     * @return Colour of the face oval stroke
     * @since 2.0.0
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
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
     * @deprecated Deprecated in 2.11.0
     */
    @Keep
    @ColorInt
    @Deprecated
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
        int rotation = 0;
        if (getDisplay() != null) {
            rotation = getDisplay().getRotation();
        } else {
            rotation = ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        }
        switch (rotation) {
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
}
