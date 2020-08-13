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
import androidx.preference.SwitchPreferenceCompat;

import com.appliedrec.verid.sample.R;

public class FaceDetectionSettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private String[] presets;
    private String[] presetsIncCustom;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        presets = new String[]{getString(R.string.permissive),getString(R.string.normal),getString(R.string.restrictive)};
        presetsIncCustom = new String[]{getString(R.string.permissive),getString(R.string.normal),getString(R.string.restrictive),getString(R.string.custom)};

        Context context = getPreferenceManager().getContext();
        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();

        float confidenceThreshold = Float.parseFloat(preferences.getString(PreferenceKeys.CONFIDENCE_THRESHOLD, "-0.5"));
        float faceTemplateExtractionThreshold = Float.parseFloat(preferences.getString(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD, "8.0"));

        ListPreference presetPref = new ListPreference(context);
        presetPref.setKey(PreferenceKeys.FACE_DETECTION_PROFILE);
        presetPref.setTitle(R.string.preset);
        presetPref.setOnPreferenceChangeListener(this);
        preferenceScreen.addPreference(presetPref);

        PreferenceCategory faceQualityCategory = new PreferenceCategory(context);
        faceQualityCategory.setTitle(R.string.face_quality);
        preferenceScreen.addPreference(faceQualityCategory);

        ListPreference confidencePref = new ListPreference(context);
        confidencePref.setKey(PreferenceKeys.CONFIDENCE_THRESHOLD);
        confidencePref.setTitle(R.string.confidence_threshold);
        confidencePref.setEntryValues(R.array.confidence_threshold_values);
        confidencePref.setEntries(R.array.confidence_threshold_values);
        confidencePref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        confidencePref.setValue(String.format("%.01f",confidenceThreshold));
        confidencePref.setOnPreferenceChangeListener(this);
        faceQualityCategory.addPreference(confidencePref);

        ListPreference extractionQualityPref = new ListPreference(context);
        extractionQualityPref.setKey(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD);
        extractionQualityPref.setTitle(R.string.face_template_extraction_threshold);
        extractionQualityPref.setEntryValues(R.array.face_template_extraction_thresholds);
        extractionQualityPref.setEntries(R.array.face_template_extraction_thresholds);
        extractionQualityPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        extractionQualityPref.setValue(String.format("%.01f", faceTemplateExtractionThreshold));
        extractionQualityPref.setOnPreferenceChangeListener(this);
        faceQualityCategory.addPreference(extractionQualityPref);

        setPreferenceScreen(preferenceScreen);

        updatePresets();
    }

    private void updatePresets() {
        ListPreference presetPref = findPreference(PreferenceKeys.FACE_DETECTION_PROFILE);
        ListPreference confidencePref = findPreference(PreferenceKeys.CONFIDENCE_THRESHOLD);
        ListPreference extractionPref = findPreference(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD);
        if (presetPref == null || confidencePref == null || extractionPref == null) {
            return;
        }
        presetPref.setEntryValues(presets);
        presetPref.setEntries(presets);

        float confidenceThreshold = Float.parseFloat(confidencePref.getValue());
        float faceTemplateExtractionThreshold = Float.parseFloat(extractionPref.getValue());

        FaceDetectionSettings faceDetectionSettings = new FaceDetectionSettings(confidenceThreshold, faceTemplateExtractionThreshold);
        if (faceDetectionSettings.equals(FaceDetectionSettings.PERMISSIVE)) {
            presetPref.setValue(getString(R.string.permissive));
            presetPref.setSummary(R.string.permissive);
        } else if (faceDetectionSettings.equals(FaceDetectionSettings.NORMAL)) {
            presetPref.setValue(getString(R.string.normal));
            presetPref.setSummary(R.string.normal);
        } else if (faceDetectionSettings.equals(FaceDetectionSettings.RESTRICTIVE)) {
            presetPref.setValue(getString(R.string.restrictive));
            presetPref.setSummary(R.string.restrictive);
        } else {
            presetPref.setEntries(presetsIncCustom);
            presetPref.setEntryValues(presetsIncCustom);
            presetPref.setValue(getString(R.string.custom));
            presetPref.setSummary(R.string.custom);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PreferenceKeys.FACE_DETECTION_PROFILE.equals(preference.getKey())) {
            String value = (String)newValue;
            FaceDetectionSettings faceDetectionSettings;
            if (value.equals(getString(R.string.permissive))) {
                faceDetectionSettings = FaceDetectionSettings.PERMISSIVE;
            } else if (value.equals(getString(R.string.restrictive))) {
                faceDetectionSettings = FaceDetectionSettings.RESTRICTIVE;
            } else {
                faceDetectionSettings = FaceDetectionSettings.NORMAL;
            }
            preference.setSummary(value);
            String confidenceThreshold = String.format("%.01f", faceDetectionSettings.getConfidenceThreshold());
            String extractionThreshold = String.format("%.01f", faceDetectionSettings.getFaceTemplateExtractionThreshold());
            ListPreference confidencePref = findPreference(PreferenceKeys.CONFIDENCE_THRESHOLD);
            ListPreference extractionQualityPref = findPreference(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD);
            SwitchPreferenceCompat useMLKitPref = findPreference(PreferenceKeys.USE_MLKIT);
            if (confidencePref != null) confidencePref.setValue(confidenceThreshold);
            if (extractionQualityPref != null) extractionQualityPref.setValue(extractionThreshold);

            getPreferenceManager().getSharedPreferences().edit()
                    .putString(PreferenceKeys.CONFIDENCE_THRESHOLD, confidenceThreshold)
                    .putString(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD, extractionThreshold)
                    .apply();
            return true;
        } else {
            new Handler(Looper.getMainLooper()).post(this::updatePresets);
            return true;
        }
    }
}
