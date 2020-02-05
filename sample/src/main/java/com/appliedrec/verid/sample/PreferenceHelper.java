package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.SharedPreferences;

class PreferenceHelper {

    private final SharedPreferences preferences;
    private final Context context;

    PreferenceHelper(Context context, SharedPreferences preferences) {
        this.context = context;
        this.preferences = preferences;
    }

    float getYawThreshold() {
        return (float) preferences.getInt(context.getString(R.string.pref_key_yaw_threshold), 15);
    }

    float getPitchThreshold() {
        return (float) preferences.getInt(context.getString(R.string.pref_key_pitch_threshold), 12);
    }

    float getAuthenticationThreshold() {
        float val = (float) preferences.getInt(context.getString(R.string.pref_key_auth_threshold), 40);
        return val / 10;
    }
}
