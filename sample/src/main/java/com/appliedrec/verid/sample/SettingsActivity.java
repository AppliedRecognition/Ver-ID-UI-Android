package com.appliedrec.verid.sample;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.ListPreferenceDialogFragmentCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SettingsActivity extends AppCompatActivity {

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        static String ARG_VERSION_NAME = "versionName";
        static String ARG_VERSION_CODE = "versionCode";
        static String ARG_PACKAGE_NAME = "packageName";
        static String ARG_LAST_UPDATE_TIME = "lastUpdateTime";
        static String ARG_FIRST_INSTALL_TIME = "firstInstallTime";

        public static SettingsFragment newInstance(PackageInfo packageInfo) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle args = new Bundle();
            args.putString(ARG_VERSION_NAME, packageInfo.versionName);
            args.putInt(ARG_VERSION_CODE, packageInfo.versionCode);
            args.putString(ARG_PACKAGE_NAME, packageInfo.packageName);
            args.putLong(ARG_LAST_UPDATE_TIME, packageInfo.lastUpdateTime);
            args.putLong(ARG_FIRST_INSTALL_TIME, packageInfo.firstInstallTime);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences);
            CheckBoxPreference useBackCameraPreference = (CheckBoxPreference) findPreference(getString(R.string.pref_key_use_back_camera));
            useBackCameraPreference.setDefaultValue(false);
            int camCount = Camera.getNumberOfCameras();
            for (int i=0; i<camCount; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    useBackCameraPreference.setEnabled(true);
                    break;
                }
            }
            CheckBoxPreference disableEncryptionPreference = (CheckBoxPreference) findPreference(getString(R.string.pref_key_disable_encryption));
            disableEncryptionPreference.setDefaultValue(false);
            Bundle args = getArguments();
            if (args != null) {
                String version = args.getString(ARG_VERSION_NAME);
                findPreference(getString(R.string.pref_key_version)).setSummary(version);
                findPreference(getString(R.string.pref_key_version_code)).setSummary(Integer.toString(args.getInt(ARG_VERSION_CODE)));
                findPreference(getString(R.string.pref_key_package_name)).setSummary(args.getString(ARG_PACKAGE_NAME));
                DateFormat dateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
                findPreference(getString(R.string.pref_key_first_installed)).setSummary(dateFormat.format(new Date(args.getLong(ARG_FIRST_INSTALL_TIME))));
                findPreference(getString(R.string.pref_key_last_updated)).setSummary(dateFormat.format(new Date(args.getLong(ARG_LAST_UPDATE_TIME))));
            }
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            if (preference instanceof NumberPreference) {
                NumberPreferenceDialog dialogFragment = NumberPreferenceDialog.newInstance(preference.getKey(), ((NumberPreference) preference).getMinValue(), ((NumberPreference) preference).getMaxValue());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getFragmentManager(), null);
            } else if (preference instanceof FaceGuidePreference) {
                FaceGuidePreferenceFragment dialogFragment = FaceGuidePreferenceFragment.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 1);
                dialogFragment.show(getFragmentManager(), null);
            } else if (preference instanceof ListPreference) {
                ListPreferenceDialogFragmentCompat dialogFragment = ListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 2);
                dialogFragment.show(getFragmentManager(), null);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (getActivity() != null && key.equals(getString(R.string.pref_key_disable_encryption))) {
                PackageManager packageManager = getActivity().getPackageManager();
                if (packageManager == null) {
                    return;
                }
                Intent startIntent = packageManager.getLaunchIntentForPackage(getActivity().getPackageName());
                final PendingIntent pendingIntent = PendingIntent.getActivity(getActivity(), 123, startIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                final AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.app_will_restart)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis()+100, pendingIntent);
                                Runtime.getRuntime().exit(0);
                            }
                        })
                        .create()
                        .show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsFragment settingsFragment;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            settingsFragment = SettingsFragment.newInstance(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            settingsFragment = new SettingsFragment();
        }
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction().replace(R.id.root_view, settingsFragment).commit();

    }
}
