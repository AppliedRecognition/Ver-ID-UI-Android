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
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            PreferenceHelper preferenceHelper = new PreferenceHelper(getActivity(), getPreferenceScreen().getSharedPreferences());
            yawThresholdPreference.setSummary(Integer.toString(Math.round(preferenceHelper.getYawThreshold())));
            pitchThresholdPreference.setSummary(Integer.toString(Math.round(preferenceHelper.getPitchThreshold())));
            authThresholdPreference.setSummary(String.format("%.02f", preferenceHelper.getAuthenticationThreshold()));
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
