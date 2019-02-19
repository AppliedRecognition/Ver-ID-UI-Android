package com.appliedrec.verid.ui;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.support.annotation.Nullable;

import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.IImageProviderService;
import com.appliedrec.verid.core.SessionResult;
import com.appliedrec.verid.core.Size;

public interface IVerIDSessionFragment extends IImageProviderService {

    void startCamera();
    Matrix imageScaleTransformAtImageSize(Size size);
    void didProduceSessionResultFromFaceDetectionResult(SessionResult sessionResult, FaceDetectionResult faceDetectionResult);
    void drawCameraOverlay(Bearing bearing, @Nullable String text, boolean isHighlighted, RectF ovalBounds, @Nullable RectF cutoutBounds, @Nullable EulerAngle faceAngle, boolean showArrow, @Nullable EulerAngle offsetAngleFromBearing);
}
