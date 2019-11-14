package com.appliedrec.verid.sample;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.appliedrec.rxverid.RxVerID;
import com.appliedrec.verid.core.UserManagementFactory;

public class RxVerIDInstance {

    private static RxVerID rxVerID;
    private static Context context;
    private static boolean disableEncryption = false;

    static synchronized void load(Context context) {
        RxVerIDInstance.context = context;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        disableEncryption = preferences.getBoolean(context.getString(R.string.pref_key_disable_encryption), false);
        preferences.registerOnSharedPreferenceChangeListener((SharedPreferences prefs, String key) -> {
            if (key.equals(context.getString(R.string.pref_key_disable_encryption)) && disableEncryption != prefs.getBoolean(key, disableEncryption)) {
                rxVerID = null;
                disableEncryption = prefs.getBoolean(key, disableEncryption);
            }
        });
    }

    static synchronized RxVerID get() {
        if (rxVerID == null) {
            UserManagementFactory userManagementFactory = new UserManagementFactory(context, disableEncryption);
            rxVerID = new RxVerID.Builder(context).setUserManagementFactory(userManagementFactory).build();
        }
        return rxVerID;
    }
}
