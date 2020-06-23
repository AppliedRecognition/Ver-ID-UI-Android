package com.appliedrec.verid.ui2;

import com.appliedrec.verid.core2.session.VerIDSessionResult;

/**
 * Ver-ID session delegate
 * @since 2.0.0
 */
public interface VerIDSessionDelegate {

    /**
     * Called when session finishes
     * @param session Session that finished
     * @param result Session result
     * @since 2.0.0
     */
    void sessionDidFinishWithResult(AbstractVerIDSession<?,?,?> session, VerIDSessionResult result);

    /**
     * Called when session is canceled by the user
     * @param session Session that was canceled
     * @since 2.0.0
     */
    default void sessionWasCanceled(AbstractVerIDSession<?,?,?> session) {
    }

    /**
     * Called to see whether the session should display the session result to the user
     * @param session Session
     * @param result Result to be displayed
     * @return {@literal true} to let the session display its result to the user or {@literal false} to finish the session without displaying the result to the user
     * @since 2.0.0
     */
    default boolean shouldSessionShowResult(AbstractVerIDSession<?, ?, ?> session, VerIDSessionResult result) {
        return false;
    }

    /**
     * Called to see whether to use speech to communicate the session prompts to the user
     * @param session Session
     * @return {@literal true} to speak the session prompts
     * @since 2.0.0
     */
    default boolean shouldSpeakPromptsInSession(AbstractVerIDSession<?, ?, ?> session) {
        return false;
    }

    /**
     * Called by the session to see which camera lens to use to capture the session faces
     * @param session Session
     * @return {@link CameraLens#FACING_BACK} to use the back camera or {@link CameraLens#FACING_FRONT} (default) to use the front-facing (selfie) camera
     * @since 2.0.0
     */
    default CameraLens getCameraLensForSession(AbstractVerIDSession<?, ?, ?> session) {
        return CameraLens.FACING_FRONT;
    }
}
