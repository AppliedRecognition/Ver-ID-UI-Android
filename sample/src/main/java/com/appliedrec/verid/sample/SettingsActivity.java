package com.appliedrec.verid.sample;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.appliedrec.verid.core.SessionSettings;

public class SettingsActivity extends AppCompatActivity {

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        private YawThresholdPreference yawThresholdPreference;
        private PitchThresholdPreference pitchThresholdPreference;
        private AuthenticationThresholdPreference authThresholdPreference;
        private FaceBoundsWidthPreference faceBoundsWidthPreference;
        private FaceBoundsHeightPreference faceBoundsHeightPreference;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            yawThresholdPreference = (YawThresholdPreference) findPreference(getString(R.string.pref_key_yaw_threshold));
            pitchThresholdPreference = (PitchThresholdPreference) findPreference(getString(R.string.pref_key_pitch_threshold));
            authThresholdPreference = (AuthenticationThresholdPreference) findPreference(getString(R.string.pref_key_auth_threshold));
            SessionSettings settings = new SessionSettings();
            yawThresholdPreference.setDefaultValue(settings.getYawThreshold());
            pitchThresholdPreference.setDefaultValue(settings.getPitchThreshold());
            authThresholdPreference.setDefaultValue(40);
            faceBoundsWidthPreference = (FaceBoundsWidthPreference) findPreference(getString(R.string.pref_key_face_bounds_width));
            faceBoundsHeightPreference = (FaceBoundsHeightPreference) findPreference(getString(R.string.pref_key_face_bounds_height));
            faceBoundsWidthPreference.setDefaultValue((int)(settings.getFaceBoundsFraction().x * 20));
            faceBoundsHeightPreference.setDefaultValue((int)(settings.getFaceBoundsFraction().y * 20));
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            PreferenceHelper preferenceHelper = new PreferenceHelper(getActivity(), getPreferenceScreen().getSharedPreferences());
            yawThresholdPreference.setSummary(Integer.toString(Math.round(preferenceHelper.getYawThreshold())));
            pitchThresholdPreference.setSummary(Integer.toString(Math.round(preferenceHelper.getPitchThreshold())));
            int val = getPreferenceScreen().getSharedPreferences().getInt(getString(R.string.pref_key_auth_threshold), 40);
            authThresholdPreference.setSummary(Integer.toString(val));
            SessionSettings settings = new SessionSettings();
            val = getPreferenceScreen().getSharedPreferences().getInt(getString(R.string.pref_key_face_bounds_width), (int)(settings.getFaceBoundsFraction().x * 20)) * 5;
            faceBoundsWidthPreference.setSummary(val+"% of view width");
            val = getPreferenceScreen().getSharedPreferences().getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 5;
            faceBoundsHeightPreference.setSummary(val+"% of view height");
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null) {
                return;
            }
            SessionSettings settings = new SessionSettings();
            PreferenceHelper preferenceHelper = new PreferenceHelper(getActivity(), sharedPreferences);
            if (key.equals(getString(R.string.pref_key_yaw_threshold))) {
                int val = sharedPreferences.getInt(key, (int)settings.getYawThreshold());
                yawThresholdPreference.setSummary(Integer.toString(val));
            } else if (key.equals(getString(R.string.pref_key_pitch_threshold))) {
                int val = sharedPreferences.getInt(key, (int)settings.getPitchThreshold());
                pitchThresholdPreference.setSummary(Integer.toString(val));
            } else if (key.equals(getString(R.string.pref_key_auth_threshold))) {
                int val = sharedPreferences.getInt(key, 40);
                authThresholdPreference.setSummary(Integer.toString(val));
            } else if (key.equals(getString(R.string.pref_key_face_bounds_width))) {
                int val = sharedPreferences.getInt(getString(R.string.pref_key_face_bounds_width), (int) (settings.getFaceBoundsFraction().x * 20)) * 5;
                faceBoundsWidthPreference.setSummary(val + "% of view width");
            } else if (key.equals(getString(R.string.pref_key_face_bounds_height))) {
                int val = sharedPreferences.getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 5;
                faceBoundsHeightPreference.setSummary(val+"% of view height");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getFragmentManager().beginTransaction().replace(R.id.root_view, new SettingsFragment()).commit();

    }
}
