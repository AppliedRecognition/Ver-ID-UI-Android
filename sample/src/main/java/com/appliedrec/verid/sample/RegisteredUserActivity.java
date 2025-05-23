package com.appliedrec.verid.sample;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDCoreException;
import com.appliedrec.verid.core2.session.AuthenticationSessionSettings;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivityRegisteredUserBinding;
import com.appliedrec.verid.sample.preferences.MimeTypes;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.preferences.SettingsActivity;
import com.appliedrec.verid.sample.sharing.RegistrationExport;
import com.appliedrec.verid.sample.sharing.RegistrationImportContract;
import com.appliedrec.verid.sample.sharing.RegistrationImportReviewActivity;
import com.appliedrec.verid.ui2.CameraLocation;
import com.appliedrec.verid.ui2.ISessionActivity;
import com.appliedrec.verid.ui2.ISessionView;
import com.appliedrec.verid.ui2.IVerIDSession;
import com.appliedrec.verid.ui2.SessionView;
import com.appliedrec.verid.ui2.VerIDSession;
import com.appliedrec.verid.ui2.VerIDSessionActivityContract;
import com.appliedrec.verid.ui2.VerIDSessionActivitySettings;
import com.appliedrec.verid.ui2.VerIDSessionDelegate;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class RegisteredUserActivity extends AppCompatActivity implements IVerIDLoadObserver, VerIDSessionDelegate {

    private ProfilePhotoHelper profilePhotoHelper;
    private VerID verID;
    private RegistrationExport registrationExport;
    private ExecutorService backgroundExecutor;
    private ActivityRegisteredUserBinding viewBinding;

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
        if (item.getItemId() == R.id.action_export_registration) {
            exportRegistration();
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            showSettings();
            return true;
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
            settings.setSessionDiagnosticsEnabled(!"deny".equals(preferences.getString(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD, "ask")));
            settings.setPassiveLivenessDetectionEnabled(preferences.getBoolean(PreferenceKeys.PASSIVE_LIVENESS_ENABLED, settings.isPassiveLivenessDetectionEnabled()));
        }
//        authenticationSessionLauncher.launch(new VerIDSessionActivitySettings(verID, settings));
        VerIDSession authenticationSession;
        authenticationSession = new VerIDSession(verID, settings);
        authenticationSession.setDelegate(this);
        authenticationSession.start();
    }
    //endregion

    private ActivityResultLauncher<VerIDSessionActivitySettings> authenticationSessionLauncher = registerForActivityResult(new VerIDSessionActivityContract(), result -> {
        if (result != null) {
            if (result.getError().isPresent()) {
                Toast.makeText(this, "Authentication failed: "+result.getError().get().getMessage(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Authentication succeeded", Toast.LENGTH_SHORT).show();
            }
        }
    });

    private ActivityResultLauncher<VerIDSessionActivitySettings> registrationSessionLauncher = registerForActivityResult(new VerIDSessionActivityContract(), result -> {
        if (result != null && !result.getError().isPresent()) {
            result.getFirstFaceCapture(Bearing.STRAIGHT).ifPresent(faceCapture -> {
                try {
                    profilePhotoHelper.setProfilePhoto(faceCapture.getFaceImage());
                    loadProfilePicture();
                    Toast.makeText(this, "Registration succeeded", Toast.LENGTH_SHORT).show();
                } catch (Exception ignore) {
                }
            });
        }
    });

    //region Registration

    private void registerMoreFaces() {
        RegistrationSessionSettings settings = new RegistrationSessionSettings(VerIDUser.DEFAULT_USER_ID);
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
        settings.setSessionDiagnosticsEnabled(false);
//        registrationSessionLauncher.launch(new VerIDSessionActivitySettings(verID, settings));
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

    private void reviewRegistrationImport(Uri uri) {
        Intent intent = new Intent(this, RegistrationImportReviewActivity.class);
        intent.setDataAndType(uri, MimeTypes.REGISTRATION.getType());
        registrationImportReview.launch(intent);
    }

    private final ActivityResultLauncher<Void> registrationImport = registerForActivityResult(new RegistrationImportContract(), uri -> {
        if (uri != null) {
            reviewRegistrationImport(uri);
        }
    });

    private final ActivityResultLauncher<Intent> registrationImportReview = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result != null && result.getResultCode() == RESULT_OK) {
            loadProfilePicture();
            Toast.makeText(this, R.string.registration_imported, Toast.LENGTH_SHORT).show();
        }
    });

    private void importRegistration() {
        registrationImport.launch(null);
    }

    private final ActivityResultLauncher<String> registrationExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), uri -> {
        if (uri != null) {
            executeInBackground(() -> {
                try {
                    registrationExport.writeToUri(uri);
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return;
                        }
                        Toast.makeText(this, "Registration exported", Toast.LENGTH_SHORT).show();
                    });
                } catch (VerIDCoreException | IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            return;
                        }
                        Toast.makeText(this, "Failed to create registration file", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    });

    private void exportRegistration() {
        registrationExportLauncher.launch("Faces.registration");
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
    public void onSessionFinished(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        if (session.getSettings() instanceof RegistrationSessionSettings && !result.getError().isPresent()) {
            result.getFirstFaceCapture(Bearing.STRAIGHT).ifPresent(faceCapture -> {
                try {
                    profilePhotoHelper.setProfilePhoto(faceCapture.getFaceImage());
                    loadProfilePicture();
                    Toast.makeText(this, "Registration succeeded", Toast.LENGTH_SHORT).show();
                } catch (Exception ignore) {
                }
            });
        } else if (BuildConfig.DEBUG && !(session.getSettings() instanceof RegistrationSessionSettings)) {
            Toast.makeText(this, "Session finished", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSessionCanceled(@NonNull IVerIDSession<?> session) {
        if (BuildConfig.DEBUG) {
            Toast.makeText(this, "Session cancelled", Toast.LENGTH_SHORT).show();
        }
    }

//    @NonNull
//    @Override
//    public <V extends View & ISessionView> Function<Context, V> createSessionViewFactory(@NonNull IVerIDSession<?> session) {
//        return context -> {
//            SessionView sessionView = new SessionView(context);
//            sessionView.addCancelButton(Color.BLUE);
//            return (V) sessionView;
//        };
//    }

    @Override
    public boolean shouldSessionDisplayResult(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        return !(session.getSettings() instanceof RegistrationSessionSettings && !result.getError().isPresent());
    }

    @Override
    public boolean shouldRetrySessionAfterFailure(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionException exception) {
        return true;
    }

    @NonNull
    @Override
    public <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(@NonNull IVerIDSession<?> session, @NonNull VerIDSessionResult result) {
        return (Class<A>) SessionResultActivity.class;
    }

    @Override
    public boolean shouldSessionSpeakPrompts(@NonNull IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.SPEAK_PROMPTS, false);
    }

    @NonNull
    @Override
    public CameraLocation getSessionCameraLocation(@NonNull IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.USE_BACK_CAMERA, false) ? CameraLocation.BACK : CameraLocation.FRONT;
    }

    @Override
    public boolean shouldSessionRecordVideo(@NonNull IVerIDSession<?> session) {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.RECORD_SESSION_VIDEO, false);
    }

//    // To use alternative session UI
//    @Override
//    public <V extends View & ISessionView> Function<Context, V> createSessionViewFactory(IVerIDSession<?> session) {
//        return context -> {
//            SessionViewWithSeparateFaceAnd3DHead sessionView = new SessionViewWithSeparateFaceAnd3DHead(context);
//            sessionView.setAngleBearingEvaluation(new AngleBearingEvaluation(session.getSettings(), 5f, 5f));
//            return (V) sessionView;
//        };
//    }

    private void executeInBackground(Runnable runnable) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(runnable);
        }
    }
}
