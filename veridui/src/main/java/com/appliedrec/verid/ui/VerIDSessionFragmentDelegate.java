package com.appliedrec.verid.ui;

import android.graphics.YuvImage;

public interface VerIDSessionFragmentDelegate {
    void veridSessionFragmentDidFailWithError(IVerIDSessionFragment fragment, Exception error);
    void veridSessionFragmentDidCancel(IVerIDSessionFragment fragment);
    void veridSessionFragmentDidCaptureImage(YuvImage image, int exifOrientation);
}
