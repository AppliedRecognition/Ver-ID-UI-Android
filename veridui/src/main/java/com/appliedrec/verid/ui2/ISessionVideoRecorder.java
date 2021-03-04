package com.appliedrec.verid.ui2;

import android.util.Size;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.lifecycle.DefaultLifecycleObserver;

import java.io.File;
import java.util.Optional;

/**
 * Interface for recording session videos
 * @since 2.0.0
 */
@Keep
public interface ISessionVideoRecorder extends DefaultLifecycleObserver {

    /**
     * Get surface
     * @return Surface on which to render video
     * @since 2.0.0
     */
    @Keep
    Optional<Surface> getSurface();

    /**
     * Set up the video recorder
     * @param videoSize Size of the raw video frames
     * @param rotationDegrees Rotation of the video in degrees
     * @since 2.0.0
     */
    @Keep
    void setup(Size videoSize, int rotationDegrees);

    /**
     * Start recording video
     * @since 2.0.0
     */
    @Keep
    void start();

    /**
     * Stop recording video
     * @since 2.0.0
     */
    @Keep
    void stop();

    /**
     * Get a file in which to save the video
     * @return File in which to save the video
     * @since 2.0.0
     */
    @Keep
    Optional<File> getVideoFile();
}
