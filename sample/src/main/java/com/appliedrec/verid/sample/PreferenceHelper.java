package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.SharedPreferences;

class PreferenceHelper {

    SharedPreferences preferences;
    Context context;

    PreferenceHelper(Context context, SharedPreferences preferences) {
        this.context = context;
        this.preferences = preferences;
    }

    float getYawThreshold() {
        float val = (float) preferences.getInt(context.getString(R.string.pref_key_yaw_threshold), 15);
        return val;
    }

    float getPitchThreshold() {
        float val = (float) preferences.getInt(context.getString(R.string.pref_key_pitch_threshold), 12);
        return val;
    }

    float getAuthenticationThreshold() {
        float val = (float) preferences.getInt(context.getString(R.string.pref_key_auth_threshold), 40);
        return val / 10;
    }
}
