package com.appliedrec.verid.ui2;

import android.graphics.drawable.Drawable;
import android.view.Surface;

import androidx.annotation.Keep;

import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceExtents;

import java.util.Iterator;
import java.util.List;

/**
 * Interface for views that render the camera preview and face detection overlay
 * @since 2.0.0
 */
@Keep
public interface ISessionView extends Iterator<FaceBounds> {

    /**
     * Session view listener
     * @since 2.0.0
     */
    @Keep
    interface SessionViewListener {

        /**
         * Called when camera preview surface is created
         * @param surface Surface used to render camera preview
         * @since 2.0.0
         */
        @Keep
        void onPreviewSurfaceCreated(Surface surface);

        /**
         * Called when camera preview surface is destroyed
         * @since 2.0.0
         */
        @Keep
        void onPreviewSurfaceDestroyed();
    }

    /**
     * Set default face extents
     * @param defaultFaceExtents Face extents (proportion of the view taken up by the face oval when no face is detected)
     * @since 2.0.0
     */
    @Keep
    void setDefaultFaceExtents(FaceExtents defaultFaceExtents);

    /**
     * Get preview class (used to determine the available camera preview sizes)
     * @return Preview class
     * @since 2.0.0
     */
    @Keep
    Class<?> getPreviewClass();

    /**
     * Add a listener
     *
     * Call {@link #removeListener(SessionViewListener)} when you no longer need to listen for session view events
     * @param listener Session view listener
     * @since 2.0.0
     */
    @Keep
    void addListener(SessionViewListener listener);

    /**
     * Remove a listener
     * @param listener Listener added using {@link #addListener(SessionViewListener)}
     * @since 2.0.0
     */
    @Keep
    void removeListener(SessionViewListener listener);

    /**
     * Set face detection result used to render camera preview overlay
     * @param faceDetectionResult Face detection result or {@literal null} to clear the face oval overlay
     * @param prompt Prompt to be displayed above the detected face or {@literal null} to hide the prompt
     * @since 2.0.0
     */
    @Keep
    void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt);

    /**
     * Draw captured face images, e.g., faces captured during a registration session
     * @param faceImages Face image drawables
     * @since 2.0.0
     */
    @Keep
    void drawFaces(List<? extends Drawable> faceImages);

    /**
     * Get the height of the captured face images
     * @return Desired height of the captured face images (used to scale the images)
     * @since 2.0.0
     */
    @Keep
    int getCapturedFaceImageHeight();

    /**
     * Get the display rotation
     * @return Display rotation in degrees
     * @since 2.0.0
     */
    @Keep
    int getDisplayRotation();

    /**
     * Set the size of the camera preview
     * @param width Width of the images delivered by the camera preview
     * @param height Height of the images delivered by the camera preview
     * @param sensorOrientation Camera sensor orientation
     * @since 2.0.0
     */
    @Keep
    void setPreviewSize(int width, int height, int sensorOrientation);
}
