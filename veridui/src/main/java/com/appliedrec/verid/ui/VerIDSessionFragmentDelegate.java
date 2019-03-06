package com.appliedrec.verid.ui;

import android.graphics.YuvImage;

import com.appliedrec.verid.core.SessionSettings;

public interface VerIDSessionFragmentDelegate {
    SessionSettings getSessionSettings();
    void veridSessionFragmentDidFailWithError(IVerIDSessionFragment fragment, Exception error);
    void veridSessionFragmentDidCancel(IVerIDSessionFragment fragment);
    void veridSessionFragmentDidCaptureImage(YuvImage image, int exifOrientation);
}
