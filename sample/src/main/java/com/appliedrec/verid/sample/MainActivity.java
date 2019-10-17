package com.appliedrec.verid.sample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core.UserManagementFactory;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDFactoryDelegate;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.trello.lifecycle2.android.lifecycle.AndroidLifecycle;
import com.trello.rxlifecycle3.LifecycleProvider;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity implements VerIDFactoryDelegate {

    private static final int REQUEST_CODE_ONERROR = 0;

    private int veridInstanceId = -1;

    private final LifecycleProvider<Lifecycle.Event> lifecycleProvider = AndroidLifecycle.createLifecycleProvider(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        if (savedInstanceState != null) {
            int veridInstanceId = savedInstanceState.getInt(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, -1);
            if (veridInstanceId > -1) {
                try {
                    loadRegisteredUsers(VerID.getInstance(veridInstanceId));
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        createVerID();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, veridInstanceId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ONERROR && resultCode == RESULT_OK) {
            createVerID();
        }
    }

    private void createVerID() {
        if (veridInstanceId > -1) {
            try {
                VerID verID = VerID.getInstance(veridInstanceId);
                verID.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        VerIDFactory verIDFactory = new VerIDFactory(this, this);
        boolean disableEncryption = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_key_disable_encryption), false);
        UserManagementFactory userManagementFactory = new UserManagementFactory(this, disableEncryption);
        verIDFactory.setUserManagementFactory(userManagementFactory);
        verIDFactory.createVerID();
    }

    @Override
    public void veridFactoryDidCreateEnvironment(VerIDFactory factory, VerID environment) {
        veridInstanceId = environment.getInstanceId();
        registerPreferences(environment);
        loadRegisteredUsers(environment);
    }

    @Override
    public void veridFactoryDidFailWithException(VerIDFactory factory, Exception error) {
        showError("Failed to create Ver-ID environment");
    }

    private void registerPreferences(VerID environment) {
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
    }

    private void loadRegisteredUsers(final VerID verID) {
        Maybe.create(emitter -> {
            try {
                final String[] users = verID.getUserManagement().getUsers();
                if (users.length > 0) {
                    emitter.onSuccess(users[0]);
                } else {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).compose(lifecycleProvider.bindToLifecycle()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(userId -> {
            Intent intent = new Intent(MainActivity.this, RegisteredUserActivity.class);
            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
            startActivity(intent);
            finish();
        }, error -> showError("Failed to retrieve registered users"), () -> {
            Intent intent = new Intent(MainActivity.this, IntroActivity.class);
            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
            startActivity(intent);
            finish();
        });
    }

    private void showError(String message) {
        Intent intent = new Intent(this, ErrorActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivityForResult(intent, REQUEST_CODE_ONERROR);
    }
}
