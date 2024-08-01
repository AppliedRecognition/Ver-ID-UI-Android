package com.appliedrec.verid.sample.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.appliedrec.verid.core2.FaceDetection;
import com.appliedrec.verid.core2.FaceDetectionRecognitionSettings;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.sample.IntroActivity;
import com.appliedrec.verid.sample.R;

import static android.content.Context.CAMERA_SERVICE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private HashMap<String,String> diagnosticUploadMap = new HashMap<>();

    public static SettingsFragment newInstance(VerID verID) {
        Bundle bundle = new Bundle();
        bundle.putInt("verid", verID.getInstanceId());
        SettingsFragment fragment = new SettingsFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        diagnosticUploadMap.put("allow", "Allow");
        diagnosticUploadMap.put("deny", "Deny");
        diagnosticUploadMap.put("ask", "Ask on every session");
        Context context = getPreferenceManager().getContext();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        Bundle args = getArguments();
        if (sharedPreferences == null || args == null || !args.containsKey("verid")) {
            return;
        }
        VerID verID;
        try {
            verID = VerID.getInstance(args.getInt("verid", -1));
        } catch (Exception e) {
            verID = null;
        }
        if (verID == null || !(verID.getFaceDetection() instanceof FaceDetection)) {
            return;
        }

        LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);

        // ABOUT
        PreferenceCategory aboutCategory = new PreferenceCategory(context);
        aboutCategory.setTitle(R.string.about);
        preferenceScreen.addPreference(aboutCategory);
        Preference aboutPref = new Preference(context);
        aboutPref.setTitle(R.string.about_this_app);
        aboutPref.setKey(PreferenceKeys.ABOUT);
        Intent intent = new Intent(context, IntroActivity.class);
        intent.putExtra(IntroActivity.EXTRA_SHOW_REGISTRATION, false);
        aboutPref.setIntent(intent);
        Preference versionPref = new Preference(context);
        versionPref.setTitle(R.string.verid_version);
        versionPref.setKey(PreferenceKeys.VERID_VERSION);
        versionPref.setSummary(VerID.getVersion());
        aboutCategory.addPreference(aboutPref);
        aboutCategory.addPreference(versionPref);

        // SECURITY
        PreferenceCategory securityCategory = new PreferenceCategory(context);
        securityCategory.setTitle(R.string.security);
        preferenceScreen.addPreference(securityCategory);
        Preference securityProfilePref = new Preference(context);
        securityProfilePref.setTitle(R.string.security_profile);
        securityProfilePref.setKey(PreferenceKeys.SECURITY_PROFILE);
        securityProfilePref.setFragment(SecuritySettingsFragment.class.getName());
        securityProfilePref.setSummaryProvider(preference -> sharedPreferences.getString(preference.getKey(), getString(R.string.normal)));
        securityCategory.addPreference(securityProfilePref);

        // FACE DETECTION
        int i = 0;
        FaceDetection faceDetection = (FaceDetection) verID.getFaceDetection();

        PreferenceCategory faceDetectionCategory = new PreferenceCategory(context);
        faceDetectionCategory.setTitle(R.string.face_detection);
        preferenceScreen.addPreference(faceDetectionCategory);

        ListPreference confidenceThresholdPreference = new ListPreference(context);
        confidenceThresholdPreference.setEntryValues(R.array.confidence_threshold_values);
        confidenceThresholdPreference.setEntries(R.array.confidence_threshold_values);
        confidenceThresholdPreference.setTitle(R.string.confidence_threshold);
        confidenceThresholdPreference.setKey(PreferenceKeys.CONFIDENCE_THRESHOLD);
        for (CharSequence val : confidenceThresholdPreference.getEntryValues()) {
            if (val.toString().equals(String.format(Locale.ROOT, "%.02f", faceDetection.detRecLib.getSettings().getConfidenceThreshold()))) {
                confidenceThresholdPreference.setValueIndex(i);
                break;
            }
            i++;
        }
        confidenceThresholdPreference.setSummaryProvider(pref -> sharedPreferences.getString(pref.getKey(), String.format(Locale.ROOT, "%.02f", faceDetection.detRecLib.getSettings().getConfidenceThreshold())));
        faceDetectionCategory.addPreference(confidenceThresholdPreference);

        ListPreference faceDetectorVersionPreference = new ListPreference(context);
        faceDetectorVersionPreference.setEntryValues(R.array.face_detector_version_values);
        faceDetectorVersionPreference.setEntries(R.array.face_detector_versions);
        faceDetectorVersionPreference.setTitle(R.string.face_detector_version);
        faceDetectorVersionPreference.setKey(PreferenceKeys.FACE_DETECTOR_VERSION);
        i=0;
        for (CharSequence val : faceDetectorVersionPreference.getEntryValues()) {
            if (val.toString().equals(String.format(Locale.ROOT, "%d", faceDetection.detRecLib.getSettings().getDetectorVersion()))) {
                faceDetectorVersionPreference.setValueIndex(i);
                break;
            }
            i ++;
        }
        faceDetectorVersionPreference.setSummaryProvider(preference -> sharedPreferences.getString(preference.getKey(), String.format(Locale.ROOT, "%d", faceDetection.detRecLib.getSettings().getDetectorVersion())));
        faceDetectionCategory.addPreference(faceDetectorVersionPreference);

        ListPreference faceTemplateExtractionThresholdPreference = new ListPreference(context);
        faceTemplateExtractionThresholdPreference.setEntries(R.array.face_template_extraction_thresholds);
        faceTemplateExtractionThresholdPreference.setEntryValues(R.array.face_template_extraction_thresholds);
        faceTemplateExtractionThresholdPreference.setTitle(R.string.face_template_extraction_threshold);
        faceTemplateExtractionThresholdPreference.setKey(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD);
        i=0;
        for (CharSequence val : faceTemplateExtractionThresholdPreference.getEntryValues()) {
            if (val.toString().equals(String.format(Locale.ROOT, "%.01f", faceDetection.detRecLib.getSettings().getFaceExtractQualityThreshold()))) {
                faceTemplateExtractionThresholdPreference.setValueIndex(i);
                break;
            }
            i++;
        }
        faceTemplateExtractionThresholdPreference.setSummaryProvider(preference -> sharedPreferences.getString(preference.getKey(), String.format(Locale.ROOT, "%.01f", faceDetection.detRecLib.getSettings().getFaceExtractQualityThreshold())));
        faceDetectionCategory.addPreference(faceTemplateExtractionThresholdPreference);

        ListPreference landmarkTrackingThresholdPreference = new ListPreference(context);
        landmarkTrackingThresholdPreference.setEntries(R.array.face_template_extraction_thresholds);
        landmarkTrackingThresholdPreference.setEntryValues(R.array.face_template_extraction_thresholds);
        landmarkTrackingThresholdPreference.setTitle(R.string.face_landmark_tracking_threshold);
        landmarkTrackingThresholdPreference.setKey(PreferenceKeys.FACE_LANDMARK_TRACKING_THRESHOLD);
        i=0;
        for (CharSequence val : landmarkTrackingThresholdPreference.getEntryValues()) {
            if (val.toString().equals(String.format(Locale.ROOT, "%.01f", faceDetection.detRecLib.getSettings().getLandmarkTrackingQualityThreshold()))) {
                landmarkTrackingThresholdPreference.setValueIndex(i);
                break;
            }
            i++;
        }
        landmarkTrackingThresholdPreference.setSummaryProvider(preference -> sharedPreferences.getString(preference.getKey(), String.format(Locale.ROOT, "%.01f", faceDetection.detRecLib.getSettings().getLandmarkTrackingQualityThreshold())));
        faceDetectionCategory.addPreference(landmarkTrackingThresholdPreference);

        Preference faceWidthPref = new Preference(context);
        faceWidthPref.setKey(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION);
        faceWidthPref.setTitle(R.string.face_bounds_width);
        faceWidthPref.setFragment(FaceSizeSettingsFragment.class.getName());
        faceWidthPref.setSummary(String.format(Locale.ROOT, "%.0f%% of view width", sharedPreferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth()) * 100));
        faceDetectionCategory.addPreference(faceWidthPref);

        Preference faceHeightPref = new Preference(context);
        faceHeightPref.setKey(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION);
        faceHeightPref.setTitle(R.string.face_bounds_height);
        faceHeightPref.setFragment(FaceSizeSettingsFragment.class.getName());
        faceHeightPref.setSummary(String.format(Locale.ROOT, "%.0f%% of view height", sharedPreferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight()) * 100));
        faceDetectionCategory.addPreference(faceHeightPref);

        SwitchPreferenceCompat enableMaskDetectionPref = new SwitchPreferenceCompat(context);
        enableMaskDetectionPref.setKey(PreferenceKeys.ENABLE_MASK_DETECTION);
        enableMaskDetectionPref.setTitle(R.string.enable_face_covering_detection);
        enableMaskDetectionPref.setChecked(sharedPreferences.getBoolean(PreferenceKeys.ENABLE_MASK_DETECTION, livenessDetectionSessionSettings.isFaceCoveringDetectionEnabled()));
        preferenceScreen.addPreference(enableMaskDetectionPref);

        // REGISTRATION
        PreferenceCategory registrationCategory = new PreferenceCategory(context);
        registrationCategory.setTitle(R.string.registration);
        preferenceScreen.addPreference(registrationCategory);
        ListPreference registrationFaceCountPref = new ListPreference(context);
        registrationFaceCountPref.setKey(PreferenceKeys.REGISTRATION_FACE_COUNT);
        registrationFaceCountPref.setTitle(R.string.number_of_faces_to_register);
        registrationFaceCountPref.setEntries(R.array.registration_face_count_titles);
        registrationFaceCountPref.setEntryValues(R.array.registration_face_count_values);
        RegistrationSessionSettings registrationSessionSettings = new RegistrationSessionSettings("");
        String registrationFaceCount = sharedPreferences.getString(PreferenceKeys.REGISTRATION_FACE_COUNT, String.format(Locale.ROOT, "%d",registrationSessionSettings.getFaceCaptureCount()));
        registrationFaceCountPref.setValue(registrationFaceCount);
        registrationFaceCountPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        registrationCategory.addPreference(registrationFaceCountPref);
        SwitchPreferenceCompat enableEncryptionPref = new SwitchPreferenceCompat(context);
        enableEncryptionPref.setTitle(R.string.enable_face_template_encryption);
        enableEncryptionPref.setKey(PreferenceKeys.ENABLE_FACE_TEMPLATE_ENCRYPTION);
        enableEncryptionPref.setChecked(sharedPreferences.getBoolean(PreferenceKeys.ENABLE_FACE_TEMPLATE_ENCRYPTION, true));
        registrationCategory.addPreference(enableEncryptionPref);

        // ACCESSIBILITY
        PreferenceCategory accessibilityCategory = new PreferenceCategory(context);
        accessibilityCategory.setTitle(R.string.accessibility);
        preferenceScreen.addPreference(accessibilityCategory);
        SwitchPreferenceCompat speakPromptsPref = new SwitchPreferenceCompat(context);
        speakPromptsPref.setTitle(R.string.speak_prompts);
        speakPromptsPref.setKey(PreferenceKeys.SPEAK_PROMPTS);
        speakPromptsPref.setChecked(sharedPreferences.getBoolean(PreferenceKeys.SPEAK_PROMPTS, false));
        accessibilityCategory.addPreference(speakPromptsPref);

        // CAMERA
        PreferenceCategory cameraCategory = new PreferenceCategory(context);
        cameraCategory.setTitle(R.string.camera);
        preferenceScreen.addPreference(cameraCategory);
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
            if (cameraManager != null) {
                for (String id : cameraManager.getCameraIdList()) {
                    Integer facing = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing.equals(CameraCharacteristics.LENS_FACING_BACK)) {
                        SwitchPreferenceCompat useBackCameraPref = new SwitchPreferenceCompat(context);
                        useBackCameraPref.setTitle(R.string.use_back_camera);
                        useBackCameraPref.setKey(PreferenceKeys.USE_BACK_CAMERA);
                        useBackCameraPref.setChecked(sharedPreferences.getBoolean(PreferenceKeys.USE_BACK_CAMERA, false));
                        cameraCategory.addPreference(useBackCameraPref);
                        break;
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        SwitchPreferenceCompat recordSessionVideo = new SwitchPreferenceCompat(context);
        recordSessionVideo.setTitle(R.string.record_session_video);
        recordSessionVideo.setKey(PreferenceKeys.RECORD_SESSION_VIDEO);
        recordSessionVideo.setChecked(sharedPreferences.getBoolean(PreferenceKeys.RECORD_SESSION_VIDEO, false));
        cameraCategory.addPreference(recordSessionVideo);

        // DIAGNOSTICS
        PreferenceCategory diagnosticUploadCategory = new PreferenceCategory(context);
        diagnosticUploadCategory.setTitle(R.string.diagnostic_upload);
        preferenceScreen.addPreference(diagnosticUploadCategory);
        ListPreference allowDiagnosticUpload = new ListPreference(context);
        allowDiagnosticUpload.setTitle(R.string.allow_diagnostic_upload);
        allowDiagnosticUpload.setKey(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD);
        allowDiagnosticUpload.setEntries(diagnosticUploadMap.values().toArray(String[]::new));
        allowDiagnosticUpload.setEntryValues(diagnosticUploadMap.keySet().toArray(String[]::new));
        String diagnosticUploadPreference = sharedPreferences.getString(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD, "ask");
        allowDiagnosticUpload.setValue(diagnosticUploadPreference);
        allowDiagnosticUpload.setDialogTitle(R.string.allow_diagnostic_upload);
        allowDiagnosticUpload.setSummary(diagnosticUploadMap.get(diagnosticUploadPreference));
//        allowDiagnosticUpload.setSummary(R.string.allow_diagnostic_upload_summary);
        diagnosticUploadCategory.addPreference(allowDiagnosticUpload);

        setPreferenceScreen(preferenceScreen);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION) || s.equals(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION)) {
            LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
            String summary;
            if (s.equals(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION)) {
                summary = String.format(Locale.ROOT, "%.0f%% of view height", sharedPreferences.getFloat(s, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight()) * 100);
            } else {
                summary = String.format(Locale.ROOT, "%.0f%% of view width", sharedPreferences.getFloat(s, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth()) * 100);
            }
            Preference preference = findPreference(s);
            if (preference != null) {
                preference.setSummary(summary);
            }
        } else if (s.equals(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD)) {
            String summaryKey = sharedPreferences.getString(s, "ask");
            String summary = diagnosticUploadMap.get(summaryKey);
            Preference preference = findPreference(s);
            if (preference != null) {
                preference.setSummary(summary);
            }
        }
    }
}
