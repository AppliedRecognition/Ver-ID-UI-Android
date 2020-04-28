package com.appliedrec.verid.ui;

import android.graphics.RectF;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.VerIDSessionResult;

public interface IVerIDSessionFragment2 extends IVerIDSessionFragment {
    /**
     * Called when the session produces an interim session result from a face detection result.
     * <p>You can use this method to, for example, display the resulting face to the user.</p>
     * @param faceDetectionResult Face detection result
     * @param sessionResult The interim session result
     * @param defaultFaceBounds Bounds of the region where the session expects a face
     * @param offsetAngleFromBearing Angle as offset from the requested bearing – can be used to draw an arrow indicating which direction the user should move
     * @param labelText Text to display to the user
     * @since 1.21.0
     */
    void drawFaceFromResult(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult, RectF defaultFaceBounds, @Nullable EulerAngle offsetAngleFromBearing, String labelText);
}
