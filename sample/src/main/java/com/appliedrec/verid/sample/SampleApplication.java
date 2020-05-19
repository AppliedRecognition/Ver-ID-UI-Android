package com.appliedrec.verid.sample;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core.FaceDetectionRecognitionFactory;
import com.appliedrec.verid.core.FaceDetectionRecognitionSettings;
import com.appliedrec.verid.core.UserManagementFactory;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDFactoryDelegate;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;

import java.util.ArrayList;

public class SampleApplication extends MultiDexApplication implements VerIDFactoryDelegate, SharedPreferences.OnSharedPreferenceChangeListener, Application.ActivityLifecycleCallbacks {

    private VerID verID;
    private final Object veridLock = new Object();
    private final ArrayList<IVerIDLoadObserver> createdActivities = new ArrayList<>();
    private final Runnable reloadRunnable = this::loadVerID;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        registerActivityLifecycleCallbacks(this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences != null) {
            preferences.registerOnSharedPreferenceChangeListener(this);
        }
        loadVerID();
    }

    private void loadVerID() {
        synchronized (veridLock) {
            if (verID != null) {
                verID = null;
                for (IVerIDLoadObserver activity : createdActivities) {
                    activity.onVerIDUnloaded();
                }
            }
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean encryptionEnabled = preferences.getBoolean(PreferenceKeys.ENABLE_FACE_TEMPLATE_ENCRYPTION, true);
        UserManagementFactory userManagementFactory = new UserManagementFactory(this, encryptionEnabled);
        FaceDetectionRecognitionSettings faceDetectionRecognitionSettings = new FaceDetectionRecognitionSettings(null);
        if (preferences.contains(PreferenceKeys.CONFIDENCE_THRESHOLD)) {
            faceDetectionRecognitionSettings.setConfidenceThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.CONFIDENCE_THRESHOLD, "-0.5")));
        }
        if (preferences.contains(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD)) {
            faceDetectionRecognitionSettings.setFaceExtractQualityThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD, "8.0")));
        }
        FaceDetectionRecognitionFactory faceDetectionRecognitionFactory = new FaceDetectionRecognitionFactory(this, null, faceDetectionRecognitionSettings);
        VerIDFactory verIDFactory = new VerIDFactory(this, (VerIDFactoryDelegate)this);
        verIDFactory.setUserManagementFactory(userManagementFactory);
        verIDFactory.setFaceDetectionFactory(faceDetectionRecognitionFactory);
        verIDFactory.setFaceRecognitionFactory(faceDetectionRecognitionFactory);
        verIDFactory.createVerID();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PreferenceKeys.AUTHENTICATION_THRESHOLD)) {
            if (verID != null) {
                verID.getFaceRecognition().setAuthenticationThreshold(Float.parseFloat(sharedPreferences.getString(key, Float.toString(verID.getFaceRecognition().getAuthenticationThreshold()))));
            }
            return;
        }
        if (key.equals(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD) || key.equals(PreferenceKeys.CONFIDENCE_THRESHOLD) || key.equals(PreferenceKeys.ENABLE_FACE_TEMPLATE_ENCRYPTION)) {
            // Wait 100 ms in case more preferences were changed at the same time
            mainHandler.removeCallbacks(reloadRunnable);
            mainHandler.postDelayed(reloadRunnable, 100);
        }
    }

    //region Ver-ID factory delegate

    @Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory factory, VerID environment) {
        synchronized (veridLock) {
            verID = environment;
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (preferences.contains(PreferenceKeys.AUTHENTICATION_THRESHOLD)) {
                float authThreshold = Float.parseFloat(preferences.getString(PreferenceKeys.AUTHENTICATION_THRESHOLD, "4.0"));
                verID.getFaceRecognition().setAuthenticationThreshold(authThreshold);
            }
        }
        for (IVerIDLoadObserver activity : createdActivities) {
            activity.onVerIDLoaded(verID);
        }
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory factory, Exception error) {
        Intent intent = new Intent(this, ErrorActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.verid_failed_to_load));
        startActivity(intent);
    }

    //endregion

    //region Activity lifecycle callbacks

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        if (activity instanceof IVerIDLoadObserver) {
            synchronized (veridLock) {
                if (verID != null) {
                    ((IVerIDLoadObserver)activity).onVerIDLoaded(verID);
                }
            }
            createdActivities.add((IVerIDLoadObserver)activity);
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (activity instanceof IVerIDLoadObserver) {
            createdActivities.remove(activity);
        }
    }

    //endregion
}
