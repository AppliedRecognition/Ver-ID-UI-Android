package com.appliedrec.verid.ui;

import android.app.Activity;
import android.support.v7.app.AlertDialog;

import com.appliedrec.verid.core.SessionSettings;

public interface ISessionFailureDialogFactory {
    AlertDialog makeDialog(Activity activity, String message, SessionFailureDialogListener listener, SessionSettings sessionSettings);
}
