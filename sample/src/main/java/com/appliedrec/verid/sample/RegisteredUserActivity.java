package com.appliedrec.verid.sample;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core.AuthenticationSessionSettings;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.preferences.SettingsActivity;
import com.appliedrec.verid.sample.sharing.RegistrationExport;
import com.appliedrec.verid.sample.sharing.RegistrationImportReviewActivity;
import com.appliedrec.verid.ui.TranslatedStrings;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.appliedrec.verid.ui.VerIDSessionIntent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class RegisteredUserActivity extends AppCompatActivity implements IVerIDLoadObserver {

    private static final int AUTHENTICATION_REQUEST_CODE = 0;
    private static final int REGISTRATION_REQUEST_CODE = 1;
    private static final int IMPORT_REQUEST_CODE = 2;
    private static final int REGISTRATION_EXPORT_REQUEST_CODE = 3;
    private static final int IMPORT_REVIEW_REQUEST_CODE = 4;

    private ProfilePhotoHelper profilePhotoHelper;
    private VerID verID;
    private Button authenticateButton;
    private Button registerButton;
    private RegistrationExport registrationExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        profilePhotoHelper = new ProfilePhotoHelper(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registered_user);
        loadProfilePicture();
        authenticateButton = findViewById(R.id.authenticate);
        registerButton = findViewById(R.id.register);
        findViewById(R.id.removeButton).setOnClickListener(v -> unregisterUser());
        authenticateButton.setOnClickListener(v -> authenticate());
        registerButton.setOnClickListener(v -> registerMoreFaces());
        authenticateButton.setEnabled(verID != null);
        registerButton.setEnabled(verID != null);
        findViewById(R.id.import_registration).setOnClickListener(v -> importRegistration());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        registerButton = null;
        authenticateButton = null;
        registrationExport = null;
        verID = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // To inspect the result of the session:
        if (resultCode == RESULT_OK && (requestCode == REGISTRATION_REQUEST_CODE || requestCode == AUTHENTICATION_REQUEST_CODE) && data != null) {
            // See documentation at
            // https://appliedrecognition.github.io/Ver-ID-UI-Android/com.appliedrec.verid.core.VerIDSessionResult.html
            VerIDSessionResult sessionResult = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (sessionResult != null) {
                if (requestCode == REGISTRATION_REQUEST_CODE && sessionResult.getError() == null) {
                    Iterator<Map.Entry<Face, Uri>> faceImageIterator = sessionResult.getFaceImages(Bearing.STRAIGHT).entrySet().iterator();
                    if (faceImageIterator.hasNext()) {
                        AsyncTask.execute(() -> {
                            Map.Entry<Face, Uri> entry = faceImageIterator.next();
                            try {
                                profilePhotoHelper.setProfilePhotoFromUri(entry.getValue(), entry.getKey().getBounds());
                                runOnUiThread(() -> {
                                    if (isDestroyed()) {
                                        return;
                                    }
                                    loadProfilePicture();
                                    Toast.makeText(this, "Registration succeeded", Toast.LENGTH_SHORT).show();
                                });
                            } catch (Exception ignore) {
                            }
                        });
                    }
                } else {
                    Intent intent = new Intent(this, SessionResultActivity.class);
                    intent.putExtras(data);
                    startActivity(intent);
                }
            }
        } else if (resultCode == RESULT_OK && data != null && requestCode == IMPORT_REQUEST_CODE) {
            Intent intent = new Intent(this, RegistrationImportReviewActivity.class);
            intent.setData(data.getData());
            intent.putExtras(data);
            startActivityForResult(intent, IMPORT_REVIEW_REQUEST_CODE);
        } else if (resultCode == RESULT_OK && data != null && requestCode == IMPORT_REVIEW_REQUEST_CODE && data.getIntExtra(RegistrationImportReviewActivity.EXTRA_IMPORTED_FACE_COUNT, 0) > 0) {
            loadProfilePicture();
            Toast.makeText(this, R.string.registration_imported, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.registered_user, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_export_registration).setVisible(registrationExport != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
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
        AsyncTask.execute(() -> {
            try {
                Drawable drawable = profilePhotoHelper.getProfilePhotoDrawable(width);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    profileImageView.setImageDrawable(drawable);
                });
            } catch (Exception ignore) {
            }
        });
    }

    private void showSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    //region Authentication

    private void authenticate() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{
                        "English", "Français"
                }, (DialogInterface dialogInterface, int i) -> {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    AuthenticationSessionSettings settings = new AuthenticationSessionSettings(VerIDUser.DEFAULT_USER_ID);
                    // This setting dictates how many poses the user will be required to move her/his head to to ensure liveness
                    // The higher the count the more confident we can be of a live face at the expense of usability
                    // Note that 1 is added to the setting to include the initial mandatory straight pose
                    if (preferences != null) {
                        settings.setNumberOfResultsToCollect(Integer.parseInt(preferences.getString(PreferenceKeys.REQUIRED_POSE_COUNT, Integer.toString(settings.getNumberOfResultsToCollect()))));
                        settings.setYawThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.YAW_THRESHOLD, Float.toString(settings.getYawThreshold()))));
                        settings.setPitchThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.PITCH_THRESHOLD, Float.toString(settings.getPitchThreshold()))));
                        if (preferences.getBoolean(PreferenceKeys.USE_BACK_CAMERA, false)) {
                            settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
                        }
                        settings.shouldSpeakPrompts(preferences.getBoolean(PreferenceKeys.SPEAK_PROMPTS, false));
                        settings.getFaceBoundsFraction().x = preferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, settings.getFaceBoundsFraction().x);
                        settings.getFaceBoundsFraction().y = preferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, settings.getFaceBoundsFraction().y);
                    }
                    settings.shouldRecordSessionVideo(true);
                    TranslatedStrings translatedStrings = null;
                    if (i == 1) {
                        translatedStrings = new TranslatedStrings(this, "fr.xml", Locale.FRENCH);
                    }
                    Intent intent = new VerIDSessionIntent<>(RegisteredUserActivity.this, verID, settings, translatedStrings);
                    startActivityForResult(intent, AUTHENTICATION_REQUEST_CODE);
                })
                .setTitle("Select language")
                .create()
                .show();
    }
    //endregion

    //region Registration

    private void registerMoreFaces() {
        RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
        // Setting showResult to false will prevent the activity from displaying a result at the end of the session
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (preferences != null) {
            settings.setNumberOfResultsToCollect(Integer.parseInt(preferences.getString(PreferenceKeys.REGISTRATION_FACE_COUNT, Integer.toString(settings.getNumberOfResultsToCollect()))));
            settings.setYawThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.YAW_THRESHOLD, Float.toString(settings.getYawThreshold()))));
            settings.setPitchThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.PITCH_THRESHOLD, Float.toString(settings.getPitchThreshold()))));
            if (preferences.getBoolean(PreferenceKeys.USE_BACK_CAMERA, false)) {
                settings.setFacingOfCameraLens(VerIDSessionSettings.LensFacing.BACK);
            }
            settings.shouldSpeakPrompts(preferences.getBoolean(PreferenceKeys.SPEAK_PROMPTS, false));
            settings.getFaceBoundsFraction().x = preferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, settings.getFaceBoundsFraction().x);
            settings.getFaceBoundsFraction().y = preferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, settings.getFaceBoundsFraction().y);
        }
        settings.shouldRecordSessionVideo(true);
        Intent intent = new VerIDSessionIntent<>(this, verID, settings);
        startActivityForResult(intent, REGISTRATION_REQUEST_CODE);
    }

    private void unregisterUser() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_unregister)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unregister, (dialog, which) -> AsyncTask.execute(() -> {
                    try {
                        verID.getUserManagement().deleteUsers(new String[]{VerIDUser.DEFAULT_USER_ID});
                        runOnUiThread(() -> {
                            if (isDestroyed()) {
                                return;
                            }
                            Intent intent = new Intent(RegisteredUserActivity.this, IntroActivity.class);
                            intent.putExtra(VerIDSessionActivity.EXTRA_VERID_INSTANCE_ID, verID.getInstanceId());
                            startActivity(intent);
                            finish();
                        });
                    } catch (Exception ignore) {
                    }
                }))
                .create()
                .show();
    }
    //endregion

    //region Registration import and export

    private void importRegistration() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/verid-registration");
        startActivityForResult(intent, IMPORT_REQUEST_CODE);
    }

    private void exportRegistration() {
        AsyncTask.execute(() -> {
            if (registrationExport != null) {
                try {
                    Intent intent = registrationExport.createShareIntent(this);
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return;
                        }
                        ArrayList<Intent> consumers = new ArrayList<>();
                        for (ResolveInfo resolveInfo : getPackageManager ().queryIntentActivities(intent, 0)) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            if (!packageName.equals(getPackageName())) {
                                Intent consumerIntent = new Intent(intent);
                                consumerIntent.setPackage(resolveInfo.activityInfo.packageName);
                                consumers.add(consumerIntent);
                            }
                        }
                        if (consumers.isEmpty()) {
                            return;
                        }
                        if (consumers.size() > 1) {
                            Intent chooser = Intent.createChooser(consumers.remove(0), "Share registration");
                            Parcelable[] intents = new Parcelable[consumers.size()];
                            consumers.toArray(intents);
                            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents);
                            startActivityForResult(chooser, REGISTRATION_EXPORT_REQUEST_CODE);
                        } else {
                            startActivityForResult(consumers.get(0), REGISTRATION_EXPORT_REQUEST_CODE);
                        }

                    });
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to create registration file", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //endregion

    //region Ver-ID load observer

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
        if (registerButton != null) {
            registerButton.setEnabled(true);
        }
        if (authenticateButton != null) {
            authenticateButton.setEnabled(true);
        }
        registrationExport = new RegistrationExport(verid, profilePhotoHelper);
        invalidateOptionsMenu();
    }

    @Override
    public void onVerIDUnloaded() {
        verID = null;
        if (registerButton != null) {
            registerButton.setEnabled(false);
        }
        if (authenticateButton != null) {
            authenticateButton.setEnabled(false);
        }
        registrationExport = null;
        invalidateOptionsMenu();
    }

    //endregion
}
