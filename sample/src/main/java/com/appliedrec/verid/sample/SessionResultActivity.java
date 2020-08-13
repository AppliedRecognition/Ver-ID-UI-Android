package com.appliedrec.verid.sample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.FaceDetectionRecognitionSettings;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.sharing.EnvironmentSettings;
import com.appliedrec.verid.sample.sharing.SessionExport;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SessionResultActivity extends AppCompatActivity implements IVerIDLoadObserver {

    public static final String EXTRA_RESULT = "com.appliedrec.verid.EXTRA_RESULT";
    public static final String EXTRA_SETTINGS = "com.appliedrec.verid.EXTRA_SETTINGS";

    private static final int REQUEST_CODE_SHARE = 1;
    private VerIDSessionResult sessionResult;
    private VerIDSessionSettings sessionSettings;
    protected EnvironmentSettings environmentSettings;
    private Disposable createIntentDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_result);
        sessionResult = getIntent().getParcelableExtra(EXTRA_RESULT);
        sessionSettings = getIntent().getParcelableExtra(EXTRA_SETTINGS);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (sessionResult != null) {
            sessionResult.getVideoUri().ifPresent(videoUri -> transaction.add(R.id.content, SessionVideoFragment.newInstance(videoUri)));
            if (sessionResult.getFaceCaptures().length > 0) {
                transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Faces"));
                transaction.add(R.id.content, SessionFacesFragment.newInstance(sessionResult));
            }
            transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Session Result"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Succeeded", sessionResult.getError().isPresent() ? "No" : "Yes"));
            sessionResult.getError().ifPresent(error -> {
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Error", error.toString()));
            });
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Started", sessionResult.getSessionStartTime().toString()));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Session duration", String.format("%d seconds", sessionResult.getSessionDuration(TimeUnit.SECONDS))));
            sessionResult.getSessionDiagnostics().ifPresent(diagnostics -> transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face detection rate", String.format("%.01f faces/second", (float)diagnostics.getDiagnosticImages().length/(float)sessionResult.getSessionDuration(TimeUnit.MILLISECONDS)*1000f))));
        }
        if (sessionSettings != null) {
            transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Session Settings"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Expiry time", String.format("%d seconds", sessionSettings.getMaxDuration(TimeUnit.SECONDS))));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Number of results to collect", String.format("%d", sessionSettings.getFaceCaptureCount())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Using back camera", PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.USE_BACK_CAMERA, false) ? "Yes" : "No"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Yaw threshold", String.format("%.01f", sessionSettings.getYawThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Pitch threshold", String.format("%.01f", sessionSettings.getPitchThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Speak prompts", PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.SPEAK_PROMPTS, false) ? "Yes" : "No"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Required initial face width", String.format("%.0f %%", sessionSettings.getExpectedFaceExtents().getProportionOfViewWidth() * 100)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Required initial face height", String.format("%.0f %%", sessionSettings.getExpectedFaceExtents().getProportionOfViewHeight() * 100)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Pause duration", String.format("%.01f seconds", (float)sessionSettings.getPauseDuration(TimeUnit.MILLISECONDS)/1000f)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face buffer size", String.format("%d", sessionSettings.getFaceCaptureFaceCount())));
        }
        if (environmentSettings != null) {
            transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Environment"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Ver-ID version", environmentSettings.getVeridVersion()));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Authentication threshold", String.format("%.01f", environmentSettings.getAuthenticationThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face template extraction threshold", String.format("%.01f", environmentSettings.getFaceTemplateExtractionThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Confidence threshold", String.format("%.01f", environmentSettings.getConfidenceThreshold())));
        }
        transaction.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (createIntentDisposable != null && !createIntentDisposable.isDisposed()) {
            createIntentDisposable.dispose();
        }
        createIntentDisposable = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session_result, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareSession();
            return true;
        }
        return false;
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        FaceDetectionRecognitionSettings defaultSettings = new FaceDetectionRecognitionSettings(null);
        environmentSettings = new EnvironmentSettings(
                Float.parseFloat(preferences.getString(PreferenceKeys.CONFIDENCE_THRESHOLD, Float.toString(defaultSettings.getConfidenceThreshold()))),
                Float.parseFloat(preferences.getString(PreferenceKeys.FACE_TEMPLATE_EXTRACTION_THRESHOLD, Float.toString(defaultSettings.getFaceExtractQualityThreshold()))),
                verid.getFaceRecognition().getAuthenticationThreshold(),
                VerID.getVersion());
    }

    @Override
    public void onVerIDUnloaded() {

    }

    private void shareSession() {
        SessionExport sessionExport = new SessionExport(sessionSettings, sessionResult, environmentSettings);
        createIntentDisposable = sessionExport.createShareIntent(this)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        shareIntent -> {
                            Intent chooser = Intent.createChooser(shareIntent, "Share session");
                            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                                startActivityForResult(chooser, REQUEST_CODE_SHARE);
                            }
                        },
                        error -> {
                            Toast.makeText(this, "Failed to create session archive", Toast.LENGTH_SHORT).show();
                        }
                );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
