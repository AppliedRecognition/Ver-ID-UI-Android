package com.appliedrec.verid.ui2;

import android.util.Size;
import android.view.Surface;

import androidx.lifecycle.DefaultLifecycleObserver;

import java.io.File;
import java.util.Optional;

public interface ISessionVideoRecorder extends DefaultLifecycleObserver {

    Optional<Surface> getSurface();

    void setup(Size videoSize, int rotationDegrees);

    void start();

    void stop();

    Optional<File> getVideoFile();
}
