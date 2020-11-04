package com.appliedrec.verid.sample.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.sample.IntroActivity;
import com.appliedrec.verid.sample.R;

import static android.content.Context.CAMERA_SERVICE;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getPreferenceManager().getContext();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (context == null || sharedPreferences == null) {
            return;
        }
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
        PreferenceCategory faceDetectionCategory = new PreferenceCategory(context);
        faceDetectionCategory.setTitle(R.string.face_detection);
        preferenceScreen.addPreference(faceDetectionCategory);
        Preference faceDetectionProfilePref = new Preference(context);
        faceDetectionProfilePref.setTitle(R.string.face_detection_profile);
        faceDetectionProfilePref.setKey(PreferenceKeys.FACE_DETECTION_PROFILE);
        faceDetectionProfilePref.setFragment(FaceDetectionSettingsFragment.class.getName());
        faceDetectionProfilePref.setSummaryProvider(preference -> sharedPreferences.getString(preference.getKey(), getString(R.string.normal)));
        faceDetectionCategory.addPreference(faceDetectionProfilePref);
        LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
        Preference faceWidthPref = new Preference(context);
        faceWidthPref.setKey(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION);
        faceWidthPref.setTitle(R.string.face_bounds_width);
        faceWidthPref.setFragment(FaceSizeSettingsFragment.class.getName());
        faceWidthPref.setSummary(String.format("%.0f%% of view width", sharedPreferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth()) * 100));
        faceDetectionCategory.addPreference(faceWidthPref);
        Preference faceHeightPref = new Preference(context);
        faceHeightPref.setKey(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION);
        faceHeightPref.setTitle(R.string.face_bounds_height);
        faceHeightPref.setFragment(FaceSizeSettingsFragment.class.getName());
        faceHeightPref.setSummary(String.format("%.0f%% of view height", sharedPreferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight()) * 100));
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
        String registrationFaceCount = sharedPreferences.getString(PreferenceKeys.REGISTRATION_FACE_COUNT, String.format("%d",registrationSessionSettings.getFaceCaptureCount()));
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
        SwitchPreferenceCompat useCameraX = new SwitchPreferenceCompat(context);
        useCameraX.setTitle(R.string.use_camerax_api);
        useCameraX.setKey(PreferenceKeys.USE_CAMERAX);
        boolean shouldUseCameraX = sharedPreferences.getBoolean(PreferenceKeys.USE_CAMERAX, false);
        useCameraX.setChecked(shouldUseCameraX);
        cameraCategory.addPreference(useCameraX);
        SwitchPreferenceCompat recordSessionVideo = new SwitchPreferenceCompat(context);
        recordSessionVideo.setTitle(R.string.record_session_video);
        recordSessionVideo.setKey(PreferenceKeys.RECORD_SESSION_VIDEO);
        recordSessionVideo.setChecked(!shouldUseCameraX && sharedPreferences.getBoolean(PreferenceKeys.RECORD_SESSION_VIDEO, false));
        recordSessionVideo.setEnabled(!shouldUseCameraX);
        cameraCategory.addPreference(recordSessionVideo);
        SwitchPreferenceCompat preferSurfaceViewPref = new SwitchPreferenceCompat(context);
        preferSurfaceViewPref.setKey(PreferenceKeys.PREFER_SURFACE_VIEW);
        preferSurfaceViewPref.setTitle(R.string.prefer_surface_view);
        preferSurfaceViewPref.setChecked(sharedPreferences.getBoolean(PreferenceKeys.PREFER_SURFACE_VIEW, false));
        preferSurfaceViewPref.setEnabled(!shouldUseCameraX);
        cameraCategory.addPreference(preferSurfaceViewPref);
        setPreferenceScreen(preferenceScreen);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION) || s.equals(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION)) {
            LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
            String summary;
            if (s.equals(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION)) {
                summary = String.format("%.0f%% of view height", sharedPreferences.getFloat(s, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight()) * 100);
            } else {
                summary = String.format("%.0f%% of view width", sharedPreferences.getFloat(s, livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth()) * 100);
            }
            Preference preference = findPreference(s);
            if (preference != null) {
                preference.setSummary(summary);
            }
        } else if (s.equals(PreferenceKeys.USE_CAMERAX)) {
            Preference videoPreference = findPreference(PreferenceKeys.RECORD_SESSION_VIDEO);
            boolean useCameraX = sharedPreferences.getBoolean(s, false);
            if (videoPreference != null) {
                ((SwitchPreferenceCompat)videoPreference).setChecked(!useCameraX && sharedPreferences.getBoolean(PreferenceKeys.RECORD_SESSION_VIDEO, false));
                videoPreference.setEnabled(!useCameraX);
            }
            Preference preferSurfaceView = findPreference(PreferenceKeys.PREFER_SURFACE_VIEW);
            if (preferSurfaceView != null) {
                ((SwitchPreferenceCompat)preferSurfaceView).setChecked(!useCameraX && sharedPreferences.getBoolean(PreferenceKeys.PREFER_SURFACE_VIEW, false));
                preferSurfaceView.setEnabled(!useCameraX);
            }
        }
    }
}
