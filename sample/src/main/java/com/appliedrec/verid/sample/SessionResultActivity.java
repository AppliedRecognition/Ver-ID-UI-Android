package com.appliedrec.verid.sample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.appliedrec.verid.core.FaceDetectionRecognitionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.sharing.EnvironmentSettings;
import com.appliedrec.verid.sample.sharing.SessionExport;
import com.appliedrec.verid.ui.VerIDSessionActivity;

public class SessionResultActivity extends AppCompatActivity implements IVerIDLoadObserver {

    private static final int REQUEST_CODE_SHARE = 1;
    private VerIDSessionResult sessionResult;
    private VerIDSessionSettings sessionSettings;
    private EnvironmentSettings environmentSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_result);
        getIntent().setExtrasClassLoader(VerIDSessionResult.class.getClassLoader());
        sessionResult = getIntent().getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
        sessionSettings = getIntent().getParcelableExtra(VerIDSessionActivity.EXTRA_SETTINGS);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (sessionResult != null) {
            if (sessionResult.getVideoUri() != null) {
                transaction.add(R.id.content, SessionVideoFragment.newInstance(sessionResult.getVideoUri()));
            }
            if (sessionResult.getAttachments().length > 0) {
                transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Faces"));
                transaction.add(R.id.content, SessionFacesFragment.newInstance(sessionResult.getAttachments()));
            }
            transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Session Result"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Succeeded", sessionResult.getError() == null ? "Yes" : "No"));
            if (sessionResult.getError() != null) {
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Error", sessionResult.getError().getLocalizedMessage()));
            }
        }
        if (sessionSettings != null) {
            transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Session Settings"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Expiry time", String.format("%d seconds", sessionSettings.getExpiryTime()/1000)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Number of results to collect", String.format("%d", sessionSettings.getNumberOfResultsToCollect())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Using back camera", sessionSettings.getFacingOfCameraLens() == VerIDSessionSettings.LensFacing.BACK ? "Yes" : "No"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Maximum retry count", String.format("%d", sessionSettings.getMaxRetryCount())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Yaw threshold", String.format("%.01f", sessionSettings.getYawThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Pitch threshold", String.format("%.01f", sessionSettings.getPitchThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Speak prompts", sessionSettings.shouldSpeakPrompts() ? "Yes" : "No"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Required initial face width", String.format("%.0f %%", sessionSettings.getFaceBoundsFraction().x * 100)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Required initial face height", String.format("%.0f %%", sessionSettings.getFaceBoundsFraction().y * 100)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Pause duration", String.format("%.01f seconds", (float)sessionSettings.getPauseDuration()/1000f)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face buffer size", String.format("%d", sessionSettings.getFaceBufferSize())));
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
        AsyncTask.execute(() -> {
            try {
                Intent shareIntent = sessionExport.createShareIntent(this);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    startActivityForResult(Intent.createChooser(shareIntent, "Share session"), REQUEST_CODE_SHARE);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    Toast.makeText(this, "Failed to create session archive", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
