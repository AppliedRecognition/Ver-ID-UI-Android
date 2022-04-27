package com.appliedrec.verid.sample;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.AuthenticationSessionSettings;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivityRegisteredUserBinding;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.preferences.SettingsActivity;
import com.appliedrec.verid.sample.sharing.RegistrationExport;
import com.appliedrec.verid.ui2.CameraLocation;
import com.appliedrec.verid.ui2.ISessionActivity;
import com.appliedrec.verid.ui2.IVerIDSession;
import com.appliedrec.verid.ui2.TranslatedStrings;
import com.appliedrec.verid.ui2.VerIDSession;
import com.appliedrec.verid.ui2.VerIDSessionDelegate;
import com.appliedrec.verid.ui2.sharing.SessionResultPackage;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class RegisteredUserActivity extends AppCompatActivity implements IVerIDLoadObserver, VerIDSessionDelegate {

    private ProfilePhotoHelper profilePhotoHelper;
    private VerID verID;
    private RegistrationExport registrationExport;
    private ExecutorService backgroundExecutor;
    private ActivityRegisteredUserBinding viewBinding;
    private AtomicInteger sessionRunCount = new AtomicInteger(0);
    private final int sessionMaxRetryCount = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        profilePhotoHelper = new ProfilePhotoHelper(this);
        super.onCreate(savedInstanceState);
        viewBinding = ActivityRegisteredUserBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Registered user activity");
            return thread;
        });
        loadProfilePicture();
        viewBinding.removeButton.setOnClickListener(v -> unregisterUser());
        viewBinding.authenticate.setOnClickListener(v -> authenticate());
        viewBinding.register.setOnClickListener(v -> registerMoreFaces());
        viewBinding.identificationDemo.setOnClickListener(v -> startIdentificationDemo());
        viewBinding.authenticate.setEnabled(verID != null);
        viewBinding.register.setEnabled(verID != null);
        viewBinding.importRegistration.setOnClickListener(v -> importRegistration());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
        registrationExport = null;
        verID = null;
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
        }
        backgroundExecutor = null;
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
            case R.id.action_kiosk_demo:
                startActivity(new Intent(this, ContinuousLivenessActivity.class));
                return true;
            case R.id.action_small_view_session:
                startActivity(new Intent(this, SessionInSmallViewActivity.class));
        }
        return false;
    }

    private void loadProfilePicture() {
        refreshProfilePictureInView(viewBinding.profileImage);
        viewBinding.profileImage.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewBinding.profileImage.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                refreshProfilePictureInView(viewBinding.profileImage);
            }
        });
    }

    private void refreshProfilePictureInView(ImageView profileImageView) {
        int width = profileImageView.getWidth();
        if (width == 0) {
            return;
        }
        executeInBackground(() -> {
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
                        settings.setFaceCaptureCount(Integer.parseInt(preferences.getString(PreferenceKeys.REQUIRED_POSE_COUNT, Integer.toString(settings.getFaceCaptureCount()))));
                        settings.setYawThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.YAW_THRESHOLD, Float.toString(settings.getYawThreshold()))));
                        settings.setPitchThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.PITCH_THRESHOLD, Float.toString(settings.getPitchThreshold()))));
                        settings.setExpectedFaceExtents(new FaceExtents(
                                preferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, settings.getExpectedFaceExtents().getProportionOfViewWidth()),
                                preferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, settings.getExpectedFaceExtents().getProportionOfViewHeight())
                        ));
                        settings.setFaceCoveringDetectionEnabled(preferences.getBoolean(PreferenceKeys.ENABLE_MASK_DETECTION, settings.isFaceCoveringDetectionEnabled()));
                    }
                    settings.setSessionDiagnosticsEnabled(true);
                    sessionRunCount.set(0);
                    VerIDSession authenticationSession;
                    if (i == 1) {
                        TranslatedStrings translatedStrings = new TranslatedStrings(this, "fr.xml", Locale.FRENCH);
                        authenticationSession = new VerIDSession(verID, settings, translatedStrings);
                    } else {
                        authenticationSession = new VerIDSession(verID, settings);
                    }
                    authenticationSession.setDelegate(this);
                    authenticationSession.start();
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
            settings.setFaceCaptureCount(Integer.parseInt(preferences.getString(PreferenceKeys.REGISTRATION_FACE_COUNT, Integer.toString(settings.getFaceCaptureCount()))));
            settings.setYawThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.YAW_THRESHOLD, Float.toString(settings.getYawThreshold()))));
            settings.setPitchThreshold(Float.parseFloat(preferences.getString(PreferenceKeys.PITCH_THRESHOLD, Float.toString(settings.getPitchThreshold()))));
            settings.setExpectedFaceExtents(new FaceExtents(
                    preferences.getFloat(PreferenceKeys.FACE_BOUNDS_WIDTH_FRACTION, settings.getExpectedFaceExtents().getProportionOfViewWidth()),
                    preferences.getFloat(PreferenceKeys.FACE_BOUNDS_HEIGHT_FRACTION, settings.getExpectedFaceExtents().getProportionOfViewHeight())
            ));
            settings.setFaceCoveringDetectionEnabled(preferences.getBoolean(PreferenceKeys.ENABLE_MASK_DETECTION, settings.isFaceCoveringDetectionEnabled()));
        }
        settings.setSessionDiagnosticsEnabled(true);
        sessionRunCount.set(0);
        VerIDSession registrationSession = new VerIDSession(verID, settings);
        registrationSession.setDelegate(this);
        registrationSession.start();
    }

    private void unregisterUser() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_unregister)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.unregister, (dialog, which) ->
                        executeInBackground(() -> {
                            try {
                                verID.getUserManagement().deleteUsers(new String[]{VerIDUser.DEFAULT_USER_ID});
                                runOnUiThread(() -> {
                                    if (isDestroyed()) {
                                        return;
                                    }
                                    Intent intent = new Intent(RegisteredUserActivity.this, IntroActivity.class);
                                    startActivity(intent);
                                    finish();
                                });
                            } catch (Exception ignore) {
                            }
                        })
                )
                .create()
                .show();
    }
    //endregion

    //region Registration import and export

    private ActivityResultLauncher<Intent> registrationImport = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new RegistrationImportActivityResultCallback(registerForActivityResult(new RegistrationImportReviewActivityResultContract(), result -> {
        if (result != null) {
            loadProfilePicture();
            Toast.makeText(this, R.string.registration_imported, Toast.LENGTH_SHORT).show();
        }
    })));

    private void importRegistration() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/verid-registration");
        registrationImport.launch(intent);
    }

    private void exportRegistration() {
        executeInBackground(() -> {
            if (registrationExport != null) {
                try {
                    Intent intent = registrationExport.createShareIntent(this);
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return;
                        }
                        ArrayList<Intent> consumers = new ArrayList<>();
                        for (ResolveInfo resolveInfo : getPackageManager().queryIntentActivities(intent, 0)) {
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
                            startActivity(chooser);
                        } else {
                            startActivity(consumers.get(0));
                        }

                    });
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to create registration file", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //endregion

    //region Identification demo

    private void startIdentificationDemo() {
        Intent intent = new Intent(this, IdentificationDemoActivity.class);
        startActivity(intent);
    }

    //endregion

    //region Ver-ID load observer

    @Override
    public void onVerIDLoaded(VerID verid) {
        this.verID = verid;
        if (viewBinding != null) {
            viewBinding.register.setEnabled(true);
            viewBinding.authenticate.setEnabled(true);
        }
        registrationExport = new RegistrationExport(verid, profilePhotoHelper);
        invalidateOptionsMenu();
    }

    @Override
    public void onVerIDUnloaded() {
        verID = null;
        if (viewBinding != null) {
            viewBinding.register.setEnabled(false);
            viewBinding.authenticate.setEnabled(false);
        }
        registrationExport = null;
        invalidateOptionsMenu();
    }

    //endregion

    @Override
    public void onSessionFinished(IVerIDSession<?> session, VerIDSessionResult result) {
        try {
            File jsonFile = File.createTempFile("verid_", ".json");
            try (FileOutputStream fileOutputStream = new FileOutputStream(jsonFile)) {
                SessionResultPackage sessionResultPackage = new SessionResultPackage(session.getVerID(), session.getSettings(), result);
                sessionResultPackage.archiveToStream(fileOutputStream);
                // Send us the jsonFile
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        if (session.getSettings() instanceof RegistrationSessionSettings && !result.getError().isPresent()) {
            result.getFirstFaceCapture(Bearing.STRAIGHT).ifPresent(faceCapture -> {
                try {
                    profilePhotoHelper.setProfilePhoto(faceCapture.getFaceImage());
                    loadProfilePicture();
                    Toast.makeText(this, "Registration succeeded", Toast.LENGTH_SHORT).show();
                } catch (Exception ignore) {
                }
            });
        }
    }

    @Override
    public boolean shouldSessionDisplayResult(IVerIDSession<?> session, VerIDSessionResult result) {
        return !(session.getSettings() instanceof RegistrationSessionSettings && !result.getError().isPresent());
    }

    @Override
    public <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(IVerIDSession<?> session, VerIDSessionResult result) {
        return (Class<A>) SessionResultActivity.class;
    }

    @Override
    public boolean shouldSessionSpeakPrompts(IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.SPEAK_PROMPTS, false);
    }

    @Override
    public CameraLocation getSessionCameraLocation(IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.USE_BACK_CAMERA, false) ? CameraLocation.BACK : CameraLocation.FRONT;
    }

    @Override
    public boolean shouldSessionRecordVideo(IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.RECORD_SESSION_VIDEO, false);
    }

    @Override
    public boolean shouldRetrySessionAfterFailure(IVerIDSession<?> session, VerIDSessionException exception) {
        return sessionRunCount.getAndIncrement() < sessionMaxRetryCount;
    }

    private void executeInBackground(Runnable runnable) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(runnable);
        }
    }
}
