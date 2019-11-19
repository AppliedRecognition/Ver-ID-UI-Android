package com.appliedrec.verid.sample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core.LivenessDetectionSessionSettings;
import com.trello.lifecycle2.android.lifecycle.AndroidLifecycle;
import com.trello.rxlifecycle3.LifecycleProvider;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ONERROR = 0;

    private final LifecycleProvider<Lifecycle.Event> lifecycleProvider = AndroidLifecycle.createLifecycleProvider(this);
    private SampleApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        application = (SampleApplication)getApplication();
        createVerID();
        registerPreferences();
    }

    private void createVerID() {
        application.getRxVerID().getUsers()
                .firstElement()
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> {
                            Intent intent = new Intent(MainActivity.this, RegisteredUserActivity.class);
                            startActivity(intent);
                            finish();
                        },
                        error -> showError(error.getLocalizedMessage()),
                        () -> {
                            Intent intent = new Intent(MainActivity.this, IntroActivity.class);
                            startActivity(intent);
                            finish();
                        }
                );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ONERROR && resultCode == RESULT_OK) {
            createVerID();
        }
    }

    private void registerPreferences() {
        application.getRxVerID()
                .getVerID()
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        environment -> {
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                            LivenessDetectionSessionSettings defaultSessionSettings = new LivenessDetectionSessionSettings();
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            if (!sharedPreferences.contains(getString(R.string.pref_key_required_pose_count))) {
                                editor.putString(getString(R.string.pref_key_required_pose_count), Integer.toString(defaultSessionSettings.getNumberOfResultsToCollect()-1));
                            }
                            if (!sharedPreferences.contains(getString(R.string.pref_key_yaw_threshold))) {
                                editor.putInt(getString(R.string.pref_key_yaw_threshold), (int) defaultSessionSettings.getYawThreshold());
                            }
                            if (!sharedPreferences.contains(getString(R.string.pref_key_pitch_threshold))) {
                                editor.putInt(getString(R.string.pref_key_pitch_threshold), (int) defaultSessionSettings.getPitchThreshold());
                            }
                            if (!sharedPreferences.contains(getString(R.string.pref_key_auth_threshold))) {
                                editor.putInt(getString(R.string.pref_key_auth_threshold), (int) (environment.getFaceRecognition().getAuthenticationThreshold() * 10));
                            }
                            if (!sharedPreferences.contains(getString(R.string.pref_key_face_bounds_width))) {
                                editor.putInt(getString(R.string.pref_key_face_bounds_width), (int) (defaultSessionSettings.getFaceBoundsFraction().x * 20f));
                            }
                            if (!sharedPreferences.contains(getString(R.string.pref_key_face_bounds_height))) {
                                editor.putInt(getString(R.string.pref_key_face_bounds_height), (int) (defaultSessionSettings.getFaceBoundsFraction().y * 20f));
                            }
                            if (!sharedPreferences.contains(getString(R.string.pref_key_number_of_faces_to_register))) {
                                editor.putString(getString(R.string.pref_key_number_of_faces_to_register), "1");
                            }
                            editor.apply();
                        },
                        error -> {

                        });
    }

    private void showError(String message) {
        Intent intent = new Intent(this, ErrorActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivityForResult(intent, REQUEST_CODE_ONERROR);
    }
}
