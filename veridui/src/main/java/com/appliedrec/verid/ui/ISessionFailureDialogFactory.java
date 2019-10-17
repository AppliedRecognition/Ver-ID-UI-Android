package com.appliedrec.verid.ui;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;

import com.appliedrec.verid.core.VerIDSessionSettings;

/**
 * Factory interface that creates a dialog on session failure
 * @since 1.0.0
 */
public interface ISessionFailureDialogFactory {
    /**
     * Make an instance of dialog to show when a session fails.
     * @param activity Activity that will be presenting the dialog
     * @param message Message in the dialog
     * @param listener Dialog listener
     * @param sessionSettings Session settings
     * @return Alert dialog
     * @since 1.0.0
     */
    AlertDialog makeDialog(Activity activity, String message, SessionFailureDialogListener listener, VerIDSessionSettings sessionSettings);
}
