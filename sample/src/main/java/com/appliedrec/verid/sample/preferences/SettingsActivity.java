package com.appliedrec.verid.sample.preferences;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.sample.R;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, FaceSizeSettingsFragment.Listener {

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        fragment.setTargetFragment(caller, 0);
        if (PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION.equals(pref.getKey()) || PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION.equals(pref.getKey())) {
            LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
            boolean isLandscape = PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION.equals(pref.getKey());
            float value = PreferenceManager.getDefaultSharedPreferences(this).getFloat(pref.getKey(), isLandscape ? livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight() : livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth());
            Bundle args = new Bundle();
            args.putFloat(FaceSizeSettingsFragment.ARG_INITIAL_VALUE, value);
            args.putBoolean(FaceSizeSettingsFragment.ARG_IS_LANDSCAPE, isLandscape);
            fragment.setArguments(args);
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.root_view, fragment).addToBackStack(null).commit();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsFragment settingsFragment = new SettingsFragment();
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction().replace(R.id.root_view, settingsFragment).commit();
    }

    @Override
    public void onFaceSizeFractionChanged(float value, boolean isLandscape) {
        String key = isLandscape ? PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION : PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION;
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putFloat(key, value).apply();
    }
}
