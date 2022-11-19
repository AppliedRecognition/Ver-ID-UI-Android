package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;

import java.util.function.Function;

/**
 * Ver-ID session delegate
 * @since 2.0.0
 */
@Keep
public interface VerIDSessionDelegate extends VerIDSessionInViewDelegate {

    /**
     * Called when session is canceled by the user
     * @param session Session that was canceled
     * @since 2.0.0
     */
    @Keep
    default void onSessionCanceled(@NonNull IVerIDSession<?> session) {
    }

    /**
     * Called to see whether the session should display the session result to the user
     * @param session Session
     * @param result Result to be displayed
     * @return {@literal true} to let the session display its result to the user or {@literal false} to finish the session without displaying the result to the user
     * @since 2.0.0
     */
    @Keep
    default boolean shouldSessionDisplayResult(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        return false;
    }

    @Keep
    @NonNull
    default <V extends View & ISessionView> Function<Context, V> createSessionViewFactory(@NonNull IVerIDSession<?> session) {
        return context -> (V) new SessionView(context);
    }

    @Keep
    @NonNull
    default <A extends Activity & ISessionActivity> Class<A> getSessionActivityClass(@NonNull IVerIDSession<?> session) {
        return (Class<A>) SessionActivity.class;
    }

    @Keep
    @NonNull
    default <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        if (result.getError().isPresent()) {
            return (Class<A>) SessionFailureActivity.class;
        } else {
            return (Class<A>) SessionSuccessActivity.class;
        }
    }

    @Keep
    @NonNull
    default SessionFailureDialogFactory createSessionFailureDialogFactory(@NonNull IVerIDSession<?> session) {
        return new DefaultSessionFailureDialogFactory();
    }

    @Keep
    default boolean shouldRetrySessionAfterFailure(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionException exception) {
        return false;
    }
}
