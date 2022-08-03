package com.appliedrec.verid.sample.preferences;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.sample.IVerIDLoadObserver;
import com.appliedrec.verid.sample.R;

public class SettingsActivity extends AppCompatActivity implements IVerIDLoadObserver, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, FaceSizeSettingsFragment.Listener {

    private static final String FRAGMENT_TAG = "settings_fragment";
    private VerID verID;

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        if (PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION.equals(pref.getKey()) || PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION.equals(pref.getKey())) {
            LivenessDetectionSessionSettings livenessDetectionSessionSettings = new LivenessDetectionSessionSettings();
            boolean isLandscape = PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION.equals(pref.getKey());
            float value = PreferenceManager.getDefaultSharedPreferences(this).getFloat(pref.getKey(), isLandscape ? livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewHeight() : livenessDetectionSessionSettings.getExpectedFaceExtents().getProportionOfViewWidth());
            Bundle args = new Bundle();
            args.putFloat(FaceSizeSettingsFragment.ARG_INITIAL_VALUE, value);
            args.putBoolean(FaceSizeSettingsFragment.ARG_IS_LANDSCAPE, isLandscape);
            fragment.setArguments(args);
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.root_view, fragment, FRAGMENT_TAG).addToBackStack(null).commit();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsFragment settingsFragment = SettingsFragment.newInstance(verID);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction().replace(R.id.root_view, settingsFragment).commit();
    }

    @Override
    public void onFaceSizeFractionChanged(float value, boolean isLandscape) {
        String key = isLandscape ? PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION : PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION;
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putFloat(key, value).apply();
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment != null) {
            Bundle args = new Bundle();
            args.putInt("verid", verID.getInstanceId());
            fragment.setArguments(args);
        }
    }

    @Override
    public void onVerIDUnloaded() {
        this.verID = null;
    }
}
