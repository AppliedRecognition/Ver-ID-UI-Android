package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.Keep;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.SessionFunctions;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.function.Function;

/**
 * Ver-ID session delegate
 * @since 2.0.0
 */
@Keep
public interface VerIDSessionDelegate {

    /**
     * Called when session finishes
     * @param session Session that finished
     * @param result Session result
     * @since 2.0.0
     */
    @Keep
    void onSessionFinished(VerIDSession session, VerIDSessionResult result);

    /**
     * Called when session is canceled by the user
     * @param session Session that was canceled
     * @since 2.0.0
     */
    @Keep
    default void onSessionCanceled(VerIDSession session) {
    }

    /**
     * Called to see whether the session should display the session result to the user
     * @param session Session
     * @param result Result to be displayed
     * @return {@literal true} to let the session display its result to the user or {@literal false} to finish the session without displaying the result to the user
     * @since 2.0.0
     */
    @Keep
    default boolean shouldSessionDisplayResult(VerIDSession session, VerIDSessionResult result) {
        return false;
    }

    /**
     * Called to see whether to use speech to communicate the session prompts to the user
     * @param session Session
     * @return {@literal true} to speak the session prompts
     * @since 2.0.0
     */
    @Keep
    default boolean shouldSessionSpeakPrompts(VerIDSession session) {
        return false;
    }

    /**
     * Called by the session to see which camera lens to use to capture the session faces
     * @param session Session
     * @return {@link CameraLocation#BACK} to use the back camera or {@link CameraLocation#FRONT} (default) to use the front-facing (selfie) camera
     * @since 2.0.0
     */
    @Keep
    default CameraLocation getSessionCameraLocation(VerIDSession session) {
        return CameraLocation.FRONT;
    }

    @Keep
    default boolean shouldSessionRecordVideo(VerIDSession session) {
        return false;
    }

    @Keep
    default Function<VerID, IImageIterator> createImageIteratorFactory() {
        return VerIDImageIterator::new;
    }

    @Keep
    default SessionFailureDialogFactory createSessionFailureDialogFactory() {
        return new DefaultSessionFailureDialogFactory();
    }

    @Keep
    default <V extends View & ISessionView> Function<Context, V> createSessionViewFactory() {
        return context -> (V) new SessionView(context);
    }

    @Keep
    default SessionFunctions createSessionFunctions(VerID verID, VerIDSessionSettings sessionSettings) {
        return new SessionFunctions(verID, sessionSettings);
    }

    @Keep
    default <A extends Activity & ISessionActivity> Class<A> getSessionActivityClass() {
        return (Class<A>) SessionActivity.class;
    }

    @Keep
    default <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(VerIDSessionResult result) {
        if (result.getError().isPresent()) {
            return (Class<A>) SessionSuccessActivity.class;
        } else {
            return (Class<A>) SessionFailureActivity.class;
        }
    }
}
