package com.appliedrec.verid.ui;

import java.io.File;

/**
 * Video recorder interface
 * @since 1.17.0
 */
@SuppressWarnings("WeakerAccess")
public interface IVideoRecorder {

    /**
     * Get the file that holds the video recording
     * @return File
     * @since 1.17.0
     */
    File getVideoFile();
}
