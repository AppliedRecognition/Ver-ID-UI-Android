package com.appliedrec.verid.ui2;

import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.IImageFlowable;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import io.reactivex.rxjava3.functions.Consumer;

public interface ISessionActivity extends Consumer<FaceCapture>, Iterable<FaceBounds> {

    void setSessionSettings(VerIDSessionSettings settings, CameraLocation cameraLocation);

    void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt);

    IImageFlowable getImageFlowable();

    default void setVideoRecorder(ISessionVideoRecorder videoRecorder) {
    }

    default void setUseMLKitForFaceDetection(boolean useMLKit) {
    }
}
