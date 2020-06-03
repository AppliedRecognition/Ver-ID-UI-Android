package com.appliedrec.verid.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionSettings;

/**
 * Convenience Intent wrapper that constructs an intent to launch a Ver-ID session activity
 * @param <T> Session settings type
 * @since 1.0.0
 */
public class VerIDSessionIntent<T extends VerIDSessionSettings> extends Intent {

    /**
     * Constructor
     * @param context Context
     * @param environment Ver-ID environment (see {@link com.appliedrec.verid.core.VerIDFactory VerIDFactory} how to create it)
     * @param sessionSettings Session settings
     * @since 1.0.0
     */
    public VerIDSessionIntent(Context context, VerID environment, T sessionSettings) {
        this(context, environment, sessionSettings, null);
    }

    public <U extends IStringTranslator & ILocaleProvider & Parcelable> VerIDSessionIntent(@NonNull Context context, @NonNull VerID environment, @NonNull T sessionSettings, @Nullable U translatedStrings) {
        super(context, VerIDSessionActivity.class);
//        putExtra(VerIDSessionActivity.EXTRA_SETTINGS, sessionSettings);
        putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, environment.getInstanceId());
        if (translatedStrings != null) {
            putExtra(VerIDSessionActivity.EXTRA_TRANSLATION, translatedStrings);
        }
    }
}
