package com.appliedrec.verid.sample.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.sample.R;

public class SecuritySettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();

        LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
        int requiredPoseCount = Integer.parseInt(preferences.getString(PreferenceKeys.REQUIRED_POSE_COUNT, Integer.toString(livenessDetectionSessionSettings.getFaceCaptureCount())));
        float yawThreshold = Float.parseFloat(preferences.getString(PreferenceKeys.YAW_THRESHOLD, Float.toString(livenessDetectionSessionSettings.getYawThreshold())));
        float pitchThreshold = Float.parseFloat(preferences.getString(PreferenceKeys.PITCH_THRESHOLD, Float.toString(livenessDetectionSessionSettings.getPitchThreshold())));
        float authThreshold = Float.parseFloat(preferences.getString(PreferenceKeys.AUTHENTICATION_THRESHOLD, "4.0"));
        boolean passiveLivenessEnabled = preferences.getBoolean(PreferenceKeys.PASSIVE_LIVENESS_ENABLED, true);
        SecuritySettings securitySettings = new SecuritySettings(requiredPoseCount, yawThreshold, pitchThreshold, authThreshold, passiveLivenessEnabled);
        String[] securityPresets = getResources().getStringArray(R.array.security_profile_presets);

        ListPreference presetPreference = new ListPreference(context);
        presetPreference.setEntryValues(R.array.security_profile_presets);
        presetPreference.setKey(PreferenceKeys.SECURITY_PROFILE);
        presetPreference.setTitle(R.string.preset);
        presetPreference.setEntryValues(securityPresets);
        presetPreference.setEntries(securityPresets);
        presetPreference.setOnPreferenceChangeListener(this);
        if (securitySettings.equals(SecuritySettings.LOW)) {
            presetPreference.setValue(securityPresets[0]);
            presetPreference.setSummary(securityPresets[0]);
        } else if (securitySettings.equals(SecuritySettings.NORMAL)) {
            presetPreference.setValue(securityPresets[1]);
            presetPreference.setSummary(securityPresets[1]);
        } else if (securitySettings.equals(SecuritySettings.HIGH)) {
            presetPreference.setValue(securityPresets[2]);
            presetPreference.setSummary(securityPresets[2]);
        } else {
            presetPreference.setSummary(R.string.custom);
        }
        preferenceScreen.addPreference(presetPreference);

        PreferenceCategory livenessDetectionCategory = new PreferenceCategory(context);
        livenessDetectionCategory.setTitle(R.string.liveness_detection);
        livenessDetectionCategory.setSummary(R.string.liveness_detection_prevents_spoofing);
        preferenceScreen.addPreference(livenessDetectionCategory);
        ListPreference poseCountPref = new ListPreference(context);
        poseCountPref.setKey(PreferenceKeys.REQUIRED_POSE_COUNT);
        poseCountPref.setTitle(R.string.pose_count);
        poseCountPref.setEntryValues(R.array.pose_count_values);
        poseCountPref.setEntries(R.array.pose_count_titles);
        poseCountPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        poseCountPref.setValue(Integer.toString(requiredPoseCount));
        poseCountPref.setOnPreferenceChangeListener(this);
        livenessDetectionCategory.addPreference(poseCountPref);
        ListPreference yawThresholdPref = new ListPreference(context);
        yawThresholdPref.setKey(PreferenceKeys.YAW_THRESHOLD);
        yawThresholdPref.setTitle(R.string.yaw_threshold);
        yawThresholdPref.setEntryValues(R.array.yaw_thresholds);
        yawThresholdPref.setEntries(R.array.yaw_thresholds);
        yawThresholdPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        yawThresholdPref.setValue(String.format("%.01f",yawThreshold));
        yawThresholdPref.setOnPreferenceChangeListener(this);
        livenessDetectionCategory.addPreference(yawThresholdPref);
        ListPreference pitchThresholdPref = new ListPreference(context);
        pitchThresholdPref.setKey(PreferenceKeys.PITCH_THRESHOLD);
        pitchThresholdPref.setTitle(R.string.pitch_threshold);
        pitchThresholdPref.setEntryValues(R.array.pitch_thresholds);
        pitchThresholdPref.setEntries(R.array.pitch_thresholds);
        pitchThresholdPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        pitchThresholdPref.setValue(String.format("%.01f", pitchThreshold));
        pitchThresholdPref.setOnPreferenceChangeListener(this);
        livenessDetectionCategory.addPreference(pitchThresholdPref);
        SwitchPreference passiveLivenessPref = new SwitchPreference(context);
        passiveLivenessPref.setKey(PreferenceKeys.PASSIVE_LIVENESS_ENABLED);
        passiveLivenessPref.setTitle(R.string.enable_passive_liveness);
        passiveLivenessPref.setSummaryOn(android.R.string.yes);
        passiveLivenessPref.setSummaryOff(android.R.string.no);
        passiveLivenessPref.setChecked(passiveLivenessEnabled);
        passiveLivenessPref.setOnPreferenceChangeListener(this);
        livenessDetectionCategory.addPreference(passiveLivenessPref);

        PreferenceCategory authenticationCategory = new PreferenceCategory(context);
        authenticationCategory.setTitle(R.string.authentication);
        preferenceScreen.addPreference(authenticationCategory);
        ListPreference authThresholdPref = new ListPreference(context);
        authThresholdPref.setKey(PreferenceKeys.AUTHENTICATION_THRESHOLD);
        authThresholdPref.setTitle(R.string.threshold_score);
        authThresholdPref.setEntries(R.array.auth_threshold_values);
        authThresholdPref.setEntryValues(R.array.auth_threshold_values);
        authThresholdPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        authThresholdPref.setValue(String.format("%.01f", authThreshold));
        authThresholdPref.setOnPreferenceChangeListener(this);
        authenticationCategory.addPreference(authThresholdPref);

        setPreferenceScreen(preferenceScreen);

        updatePresetFromSharedPrefs();
    }

    private void updatePresetFromSharedPrefs() {
        ListPreference poseCountPref = findPreference(PreferenceKeys.REQUIRED_POSE_COUNT);
        ListPreference yawThresholdPref = findPreference(PreferenceKeys.YAW_THRESHOLD);
        ListPreference pitchThresholdPref = findPreference(PreferenceKeys.PITCH_THRESHOLD);
        ListPreference authThresholdPref = findPreference(PreferenceKeys.AUTHENTICATION_THRESHOLD);
        SwitchPreference passiveLivenessPref = findPreference(PreferenceKeys.PASSIVE_LIVENESS_ENABLED);
        if (poseCountPref == null || yawThresholdPref == null || pitchThresholdPref == null || authThresholdPref == null || passiveLivenessPref == null) {
            return;
        }
        int requiredPoseCount = Integer.parseInt(poseCountPref.getValue());
        float yawThreshold = Float.parseFloat(yawThresholdPref.getValue());
        float pitchThreshold = Float.parseFloat(pitchThresholdPref.getValue());
        float authThreshold = Float.parseFloat(authThresholdPref.getValue());
        boolean passiveLivenessEnabled = passiveLivenessPref.isChecked();
        SecuritySettings securitySettings = new SecuritySettings(requiredPoseCount, yawThreshold, pitchThreshold, authThreshold, passiveLivenessEnabled);
        ListPreference presetPref = findPreference(PreferenceKeys.SECURITY_PROFILE);
        if (presetPref == null) {
            return;
        }
        String[] entries = new String[]{getString(R.string.low),getString(R.string.normal),getString(R.string.high)};
        String value;
        if (securitySettings.equals(SecuritySettings.LOW)) {
            value = getString(R.string.low);
        } else if (securitySettings.equals(SecuritySettings.NORMAL)) {
            value = getString(R.string.normal);
        } else if (securitySettings.equals(SecuritySettings.HIGH)) {
            value = getString(R.string.high);
        } else {
            entries = new String[]{getString(R.string.low),getString(R.string.normal),getString(R.string.high),getString(R.string.custom)};
            value = getString(R.string.custom);
        }
        presetPref.setEntries(entries);
        presetPref.setEntryValues(entries);
        presetPref.setValue(value);
        presetPref.setSummary(value);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PreferenceKeys.SECURITY_PROFILE.equals(preference.getKey())) {
            ListPreference presetPref = (ListPreference) preference;
            int index = presetPref.findIndexOfValue((String) newValue);
            SecuritySettings securitySettings;
            switch (index) {
                case 0:
                    securitySettings = SecuritySettings.LOW;
                    break;
                case 2:
                    securitySettings = SecuritySettings.HIGH;
                    break;
                default:
                    securitySettings = SecuritySettings.NORMAL;
                    break;
            }
            presetPref.setSummary((String) newValue);
            String[] entries = new String[]{getString(R.string.low), getString(R.string.normal), getString(R.string.high)};
            presetPref.setEntryValues(entries);
            presetPref.setEntries(entries);
            String poseCount = Integer.toString(securitySettings.getPoseCount());
            String yawThreshold = String.format("%.01f", securitySettings.getYawThreshold());
            String pitchThreshold = String.format("%.01f", securitySettings.getPitchThreshold());
            String authThreshold = String.format("%.01f", securitySettings.getAuthThreshold());
            ListPreference poseCountPref = findPreference(PreferenceKeys.REQUIRED_POSE_COUNT);
            ListPreference yawThresholdPref = findPreference(PreferenceKeys.YAW_THRESHOLD);
            ListPreference pitchThresholdPref = findPreference(PreferenceKeys.PITCH_THRESHOLD);
            ListPreference authThresholdPref = findPreference(PreferenceKeys.AUTHENTICATION_THRESHOLD);
            SwitchPreference passiveLivenessPref = findPreference(PreferenceKeys.PASSIVE_LIVENESS_ENABLED);
            if (poseCountPref != null) poseCountPref.setValue(poseCount);
            if (yawThresholdPref != null) yawThresholdPref.setValue(yawThreshold);
            if (pitchThresholdPref != null) pitchThresholdPref.setValue(pitchThreshold);
            if (authThresholdPref != null) authThresholdPref.setValue(authThreshold);
            if (passiveLivenessPref != null) passiveLivenessPref.setChecked(securitySettings.isPassiveLivenessEnabled());

            SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
            editor.putString(PreferenceKeys.REQUIRED_POSE_COUNT, poseCount);
            editor.putString(PreferenceKeys.YAW_THRESHOLD, yawThreshold);
            editor.putString(PreferenceKeys.PITCH_THRESHOLD, pitchThreshold);
            editor.putString(PreferenceKeys.AUTHENTICATION_THRESHOLD, authThreshold);
            editor.putBoolean(PreferenceKeys.PASSIVE_LIVENESS_ENABLED, securitySettings.isPassiveLivenessEnabled());
            editor.apply();
        } else {
            new Handler(Looper.getMainLooper()).post(this::updatePresetFromSharedPrefs);
        }
        return true;
    }
}
