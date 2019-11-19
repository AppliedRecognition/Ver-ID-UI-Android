package com.appliedrec.verid.sample;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.appliedrec.rxverid.RxVerID;
import com.appliedrec.verid.core.UserManagementFactory;

public class SampleApplication extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {

    private RxVerID rxVerID;
    private boolean disableEncryption = false;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        disableEncryption = preferences.getBoolean(getString(R.string.pref_key_disable_encryption), false);
        preferences.registerOnSharedPreferenceChangeListener(this);
        loadVerID();
    }

    private synchronized void loadVerID() {
        UserManagementFactory userManagementFactory = new UserManagementFactory(this, disableEncryption);
        rxVerID = new RxVerID.Builder(this).setUserManagementFactory(userManagementFactory).build();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_key_disable_encryption)) && disableEncryption != sharedPreferences.getBoolean(key, disableEncryption)) {
            disableEncryption = sharedPreferences.getBoolean(key, disableEncryption);
            loadVerID();
        }
    }

    public synchronized RxVerID getRxVerID() {
        return rxVerID;
    }
}
