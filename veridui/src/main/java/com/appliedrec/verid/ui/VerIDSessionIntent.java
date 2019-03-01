package com.appliedrec.verid.ui;

import android.content.Context;
import android.content.Intent;

import com.appliedrec.verid.core.SessionSettings;
import com.appliedrec.verid.core.VerID;

/**
 * Convenience Intent wrapper that constructs an intent to launch a Ver-ID session activity
 * @param <T> Session settings type
 * @since 1.0.0
 */
public class VerIDSessionIntent<T extends SessionSettings> extends Intent {

    /**
     * Constructor
     * @param context Context
     * @param environment Ver-ID environment (see {@link com.appliedrec.verid.core.VerIDFactory VerIDFactory} how to create it)
     * @param sessionSettings Session settings
     * @since 1.0.0
     */
    public VerIDSessionIntent(Context context, VerID environment, T sessionSettings) {
        super(context, VerIDSessionActivity.class);
        putExtra(VerIDSessionActivity.EXTRA_SETTINGS, sessionSettings);
        putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, environment.getInstanceId());
    }
}
