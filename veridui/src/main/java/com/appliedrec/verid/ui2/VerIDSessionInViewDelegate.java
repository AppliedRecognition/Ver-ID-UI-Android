package com.appliedrec.verid.ui2;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.SessionFunctions;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.function.Function;

/**
 * Ver-ID session delegate to use with VerIDSessionInView
 */
public interface VerIDSessionInViewDelegate {

    /**
     * Called when session finishes
     * @param session Session that finished
     * @param result Session result
     * @since 2.0.0
     */
    @Keep
    void onSessionFinished(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result);

    /**
     * Called to see whether to use speech to communicate the session prompts to the user
     * @param session Session
     * @return {@literal true} to speak the session prompts
     * @since 2.0.0
     */
    @Keep
    default boolean shouldSessionSpeakPrompts(@NonNull IVerIDSession<?> session) {
        return false;
    }

    /**
     * Called by the session to see which camera lens to use to capture the session faces
     * @param session Session
     * @return {@link CameraLocation#BACK} to use the back camera or {@link CameraLocation#FRONT} (default) to use the front-facing (selfie) camera
     * @since 2.0.0
     */
    @Keep
    @NonNull
    default CameraLocation getSessionCameraLocation(@NonNull IVerIDSession<?> session) {
        return CameraLocation.FRONT;
    }

    /**
     * @param session Session which should be recorded
     * @return {@literal true} to record session video
     * @since 2.0.0
     */
    @Keep
    default boolean shouldSessionRecordVideo(@NonNull IVerIDSession<?> session) {
        return false;
    }

    /**
     * @return Function that creates image iterator from an instance of {@link VerID}
     * @since 2.0.0
     */
    @Keep
    @NonNull
    default Function<Context, IImageIterator> createImageIteratorFactory(@NonNull IVerIDSession<?> session) {
        return VerIDImageIterator::new;
    }

    /**
     * @param verID Instance of {@link VerID} to use in session functions
     * @param sessionSettings Session settings to use in session functions
     * @return Functions that control the liveness detection logic of the session
     * @since 2.0.0
     */
    @Keep
    @NonNull
    default SessionFunctions createSessionFunctions(@NonNull IVerIDSession<?> session, @NonNull VerID verID, @NonNull VerIDSessionSettings sessionSettings) {
        return new SessionFunctions(verID, sessionSettings);
    }

    /**
     * @return Minimum image area (width x height) to capture for face detection and recognition (in pixels).
     * @since 2.5.0
     */
    @Keep
    default int getCapturedImageMinimumArea() {
        return 640 * 480;
    }
}
