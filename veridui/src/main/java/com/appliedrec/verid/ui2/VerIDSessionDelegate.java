package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.Keep;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.SessionFunctions;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

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
    default void onSessionCanceled(IVerIDSession<?> session) {
    }

    /**
     * Called to see whether the session should display the session result to the user
     * @param session Session
     * @param result Result to be displayed
     * @return {@literal true} to let the session display its result to the user or {@literal false} to finish the session without displaying the result to the user
     * @since 2.0.0
     */
    @Keep
    default boolean shouldSessionDisplayResult(IVerIDSession<?> session, VerIDSessionResult result) {
        return false;
    }

    @Keep
    default <V extends View & ISessionView> Function<Context, V> createSessionViewFactory(IVerIDSession<?> session) {
        return context -> (V) new SessionView(context);
    }

    @Keep
    default <A extends Activity & ISessionActivity> Class<A> getSessionActivityClass(IVerIDSession<?> session) {
        return (Class<A>) SessionActivity.class;
    }

    @Keep
    default <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(IVerIDSession<?> session, VerIDSessionResult result) {
        if (result.getError().isPresent()) {
            return (Class<A>) SessionSuccessActivity.class;
        } else {
            return (Class<A>) SessionFailureActivity.class;
        }
    }

    @Keep
    default SessionFailureDialogFactory createSessionFailureDialogFactory(IVerIDSession<?> session) {
        return new DefaultSessionFailureDialogFactory();
    }

    @Keep
    default boolean shouldRetrySessionAfterFailure(IVerIDSession<?> session, VerIDSessionException exception) {
        return false;
    }
}
