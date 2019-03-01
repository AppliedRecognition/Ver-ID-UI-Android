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

/**
 * Interface that works with {@link VerIDSessionActivity}.
 * @since 1.0.0
 */
public interface IVerIDSessionFragment extends IImageProviderService {

    /**
     * Indicates that the fragment should start a camera.
     * @since 1.0.0
     */
    void startCamera();

    /**
     * Indicates how to transform an image of the given size to fit to the fragment view.
     * @param size Image size
     * @return Transformation matrix
     * @since 1.0.0
     */
    Matrix imageScaleTransformAtImageSize(Size size);

    /**
     * Called when the session produces an interim session result from a face detection result.
     * <p>You can use this method to, for example, display the resulting face to the user. See {@link VerIDRegistrationSessionFragment#didProduceSessionResultFromFaceDetectionResult(SessionResult, FaceDetectionResult) registration fragment} for a sample implementation.</p>
     * @param sessionResult The interim session result
     * @param faceDetectionResult Face detection result
     * @since 1.0.0
     */
    void didProduceSessionResultFromFaceDetectionResult(SessionResult sessionResult, FaceDetectionResult faceDetectionResult);

    /**
     * Indicates that the fragment should draw a camera overlay view.
     * @param bearing The requested bearing
     * @param text Text that should be displayed to the user
     * @param isHighlighted Whether the guide should be highlighted – this is {@literal true} when the face is aligned as requested
     * @param ovalBounds Bounds of the face oval – these may be a "template" if no actual face was detected
     * @param cutoutBounds Bounds of the face oval "cutout" – where a face was detected
     * @param faceAngle Angle of the detected face
     * @param showArrow {@literal true} to show an arrow indicating where the user should move to fulfill the liveness detection prompt
     * @param offsetAngleFromBearing Angle representing the difference between the detected and requested face angles
     * @since 1.0.0
     */
    void drawCameraOverlay(Bearing bearing, @Nullable String text, boolean isHighlighted, RectF ovalBounds, @Nullable RectF cutoutBounds, @Nullable EulerAngle faceAngle, boolean showArrow, @Nullable EulerAngle offsetAngleFromBearing);

    /**
     * Indicates that fragment should clear the camera overlay view.
     * @since 1.0.0
     */
    void clearCameraOverlay();
}
