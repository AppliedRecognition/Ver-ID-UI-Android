package com.appliedrec.verid.ui2;

import android.app.Activity;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;

import com.appliedrec.verid.core2.session.VerIDSessionException;

/**
 * Factory interface for creating a session failure dialog
 * @since 2.0.0
 */
@Keep
public interface SessionFailureDialogFactory {

    /**
     * @since 2.0.0
     */
    enum OnDismissAction {
        /**
         * Retry the session
         */
        RETRY,
        /**
         * Cancel the session
         */
        CANCEL,
        /**
         * Show tips (the session will resume after the user finishes the tips activity)
         */
        SHOW_TIPS
    }

    /**
     * Make a dialog that will be displayed when a Ver-ID session fails but the session's maximum retry count hasn't been reached.
     * @param activity Session activity in which the dialog will be shown
     * @param onDismissListener Listener that must be called when the dialog is dismissed
     * @param exception Session exception that triggered the session to display the dialog
     * @param stringTranslator Translator instance or {@literal null} to use the current locale for string translations
     * @param <T> Activity
     * @return {@code AlertDialog} or {@literal null} if the session should be let to fail instead of showing the dialog
     * @since 2.0.0
     */
    @Keep
    @Nullable
    <T extends Activity & ISessionActivity> AlertDialog makeDialog(@NonNull T activity, @NonNull Consumer<OnDismissAction> onDismissListener, @NonNull VerIDSessionException exception, @Nullable IStringTranslator stringTranslator);
}
