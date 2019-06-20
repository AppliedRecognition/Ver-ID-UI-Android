package com.appliedrec.verid.ui;

import android.hardware.Camera;
import android.view.ViewGroup;

public interface ICameraPreviewView {

    interface CameraPreviewViewListener {
        void onCameraPreviewStarted(Camera camera);
        void onCameraReleased(Camera camera);
    }

    int getId();
    void setId(int id);
    void setLayoutParams(ViewGroup.LayoutParams layoutParams);
    void setListener(CameraPreviewViewListener listener);
    void setCamera(Camera camera);
    void setFixedSize(int width, int height);
}
