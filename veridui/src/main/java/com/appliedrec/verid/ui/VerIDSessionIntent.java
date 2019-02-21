package com.appliedrec.verid.ui;

import android.content.Context;
import android.content.Intent;

import com.appliedrec.verid.core.SessionSettings;
import com.appliedrec.verid.core.VerID;

public class VerIDSessionIntent<T extends SessionSettings> extends Intent {

    public VerIDSessionIntent(Context context, VerID environment, T sessionSettings) {
        super(context, VerIDSessionActivity.class);
        putExtra(VerIDSessionActivity.EXTRA_SETTINGS, sessionSettings);
        putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, environment.getInstanceId());
    }
}
