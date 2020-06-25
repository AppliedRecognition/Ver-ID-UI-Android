package com.appliedrec.verid.ui2;

import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.IImageFlowable;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import io.reactivex.rxjava3.functions.Consumer;

public interface ISessionActivity extends Consumer<FaceCapture> {

    void setSessionSettings(VerIDSessionSettings settings, CameraLens cameraLens);

    void setFaceDetectionResult(FaceDetectionResult faceDetectionResult, String prompt);

    IImageFlowable getImageFlowable();
}
