package com.appliedrec.verid.sample;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.lifecycle.Lifecycle;

import com.appliedrec.rxverid.RxVerIDActivity;
import com.appliedrec.verid.core.AuthenticationSessionSettings;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.ui.TranslatedStrings;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.appliedrec.verid.ui.VerIDSessionIntent;
import com.trello.lifecycle2.android.lifecycle.AndroidLifecycle;
import com.trello.rxlifecycle3.LifecycleProvider;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class RegisteredUserActivity extends RxVerIDActivity {

    private static final int AUTHENTICATION_REQUEST_CODE = 0;
    private static final int REGISTRATION_REQUEST_CODE = 1;
    private static final int IMPORT_REQUEST_CODE = 2;

    private ProfilePhotoHelper profilePhotoHelper;
    private final LifecycleProvider<Lifecycle.Event> lifecycleProvider = AndroidLifecycle.createLifecycleProvider(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registered_user);
        profilePhotoHelper = new ProfilePhotoHelper(this);
        loadProfilePicture();
        findViewById(R.id.removeButton).setOnClickListener(v -> unregisterUser());
        findViewById(R.id.authenticate).setOnClickListener(v -> authenticate());
        findViewById(R.id.register).setOnClickListener(v -> registerMoreFaces());
        // Show the registration import button if the app handles registration downloads
        Intent importIntent = new Intent("com.appliedrec.ACTION_IMPORT_REGISTRATION");
        ComponentName importActivity = importIntent.resolveActivity(getPackageManager());
        findViewById(R.id.import_registration).setVisibility(importActivity != null ? View.VISIBLE : View.GONE);
        findViewById(R.id.import_registration).setOnClickListener(v -> importRegistration());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // To inspect the result of the session:
        if (resultCode == RESULT_OK && requestCode == REGISTRATION_REQUEST_CODE) {
            addDisposable(getRxVerID()
                    .getSessionResultFromIntent(data)
                    .flatMapObservable(result -> getRxVerID().getFacesAndImageUrisFromSessionResult(result, Bearing.STRAIGHT))
                    .firstOrError()
                    .flatMapCompletable(face -> profilePhotoHelper.setProfilePhotoFromUri(face.getImageUri(), face.getFace().getBounds()))
                    .compose(lifecycleProvider.bindToLifecycle())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            RegisteredUserActivity.this::loadProfilePicture,
                            error -> {}
                    ));
            // See documentation at
            // https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.VerIDSessionResult.html
        } else if (resultCode == RESULT_OK && data != null && requestCode == IMPORT_REQUEST_CODE && data.hasExtra(Intent.EXTRA_TEXT)) {
            loadProfilePicture();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.registered_user, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Intent exportIntent = new Intent("com.appliedrec.ACTION_EXPORT_REGISTRATION");
        ComponentName exportActivity = exportIntent.resolveActivity(getPackageManager());
        menu.findItem(R.id.action_export_registration).setVisible(exportActivity != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                showIntro();
                return true;
            case R.id.action_settings:
                showSettings();
                return true;
            case R.id.action_export_registration:
                exportRegistration();
                return true;
        }
        return false;
    }

    private void loadProfilePicture() {
        ImageView profileImageView = findViewById(R.id.profileImage);
        refreshProfilePictureInView(profileImageView);
        profileImageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                profileImageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                refreshProfilePictureInView(profileImageView);
            }
        });
    }

    private void refreshProfilePictureInView(ImageView profileImageView) {
        int width = profileImageView.getWidth();
        if (width == 0) {
            return;
        }
        addDisposable(profilePhotoHelper.getProfilePhotoDrawable(width)
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        profileImageView::setImageDrawable,
                        error -> {}
                ));
    }

    private void showIntro() {
        Intent intent = new Intent(this, IntroActivity.class);
        intent.putExtra(IntroActivity.EXTRA_SHOW_REGISTRATION, false);
        startActivity(intent);
    }

    private void showSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    //region Authentication

    private void authenticate() {
        addDisposable(getRxVerID().getVerID()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribe(
                        verID -> new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setItems(new String[]{
                                            "English", "Français"
                                    }, (DialogInterface dialogInterface, int i) -> {
                                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                        AuthenticationSessionSettings settings = new AuthenticationSessionSettings(VerIDUser.DEFAULT_USER_ID);
                                        // This setting dictates how many poses the user will be required to move her/his head to to ensure liveness
                                        // The higher the count the more confident we can be of a live face at the expense of usability
                                        // Note that 1 is added to the setting to include the initial mandatory straight pose
                                        settings.setNumberOfResultsToCollect(Integer.parseInt(Objects.requireNonNull(preferences.getString(getString(R.string.pref_key_required_pose_count), "1"))) + 1);
                                        PreferenceHelper preferenceHelper = new PreferenceHelper(RegisteredUserActivity.this, preferences);
                                        settings.setYawThreshold(preferenceHelper.getYawThreshold());
                                        settings.setPitchThreshold(preferenceHelper.getPitchThreshold());
                                        verID.getFaceRecognition().setAuthenticationThreshold(preferenceHelper.getAuthenticationThreshold());
                                        // Setting showResult to false will prevent the activity from displaying a result at the end of the session
                                        settings.setShowResult(true);
                                        if (preferences.getBoolean(getString(R.string.pref_key_use_back_camera), false)) {
                                            settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
                                        }
                                        settings.getFaceBoundsFraction().x = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_width), (int)(settings.getFaceBoundsFraction().x * 20)) * 0.05f;
                                        settings.getFaceBoundsFraction().y = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_height), (int)(settings.getFaceBoundsFraction().y * 20)) * 0.05f;
                                        if (preferences.getBoolean(getString(R.string.pref_key_speak_prompts), false)) {
                                            settings.shouldSpeakPrompts(true);
                                        }
                                        TranslatedStrings translatedStrings = null;
                                        if (i == 1) {
                                            translatedStrings = new TranslatedStrings(this, "fr.xml", Locale.FRENCH);
                                        }
                                        Intent intent = new VerIDSessionIntent<>(RegisteredUserActivity.this, verID, settings, translatedStrings);
                                        startActivityForResult(intent, AUTHENTICATION_REQUEST_CODE);
                                    })
                                    .setTitle("Select language")
                                    .create()
                                    .show(),
                        error -> {

                        }));
    }
    //endregion

    //region Registration

    private void registerMoreFaces() {
        addDisposable(getRxVerID().getVerID()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(lifecycleProvider.bindToLifecycle())
                .subscribe(
                        verID -> {
                            RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
                            // Setting showResult to false will prevent the activity from displaying a result at the end of the session
                            settings.setShowResult(true);
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            settings.setNumberOfResultsToCollect(Integer.parseInt(Objects.requireNonNull(preferences.getString(getString(R.string.pref_key_number_of_faces_to_register), "1"))));
                            settings.getFaceBoundsFraction().x = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_width), (int) (settings.getFaceBoundsFraction().x * 20)) * 0.05f;
                            settings.getFaceBoundsFraction().y = (float) preferences.getInt(getString(R.string.pref_key_face_bounds_height), (int) (settings.getFaceBoundsFraction().y * 20)) * 0.05f;
                            if (preferences.getBoolean(getString(R.string.pref_key_use_back_camera), false)) {
                                settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
                            }
                            if (preferences.getBoolean(getString(R.string.pref_key_speak_prompts), false)) {
                                settings.shouldSpeakPrompts(true);
                            }
                            Intent intent = new Intent(this, VerIDSessionActivity.class);
                            intent.putExtra(VerIDSessionActivity.EXTRA_SETTINGS, settings);
                            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                            startActivityForResult(intent, REGISTRATION_REQUEST_CODE);
                        },
                        error -> {

                        }));
    }

    private void unregisterUser() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_unregister)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unregister, (dialog, which) -> addDisposable(getRxVerID().deleteUser(VerIDUser.DEFAULT_USER_ID)
                        .andThen(getRxVerID().getVerID())
                        .compose(lifecycleProvider.bindToLifecycle())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                verID -> {
                                    Intent intent = new Intent(RegisteredUserActivity.this, IntroActivity.class);
                                    intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                                    startActivity(intent);
                                    finish();
                                },
                                error -> {

                                }
                        )))
                .create()
                .show();
    }
    //endregion

    //region Registration import and export

    private void importRegistration() {
        // If you want to be able to import face registrations from other devices create an activity
        // that scans a QR code and returns a URL string in its intent's Intent.EXTRA_TEXT extra.
        Intent intent = new Intent("com.appliedrec.ACTION_IMPORT_REGISTRATION");
        startActivityForResult(intent, IMPORT_REQUEST_CODE);
    }

    private void exportRegistration() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.share_registration)
                .setMessage(R.string.app_will_generate_code)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.generate_code, (dialog, which) -> {
                    Intent intent = new Intent("com.appliedrec.ACTION_EXPORT_REGISTRATION");
                    startActivity(intent);
                })
                .create()
                .show();
    }

    //endregion
}
