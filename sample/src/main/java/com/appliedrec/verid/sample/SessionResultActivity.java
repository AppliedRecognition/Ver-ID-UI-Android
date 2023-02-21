package com.appliedrec.verid.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.TextUtilsCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.AuthenticationSessionResult;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.sharing.SampleAppFileProvider;
import com.appliedrec.verid.ui2.ISessionActivity;
import com.appliedrec.verid.ui2.SessionParameters;
import com.appliedrec.verid.ui2.sharing.SessionResultPackage;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SessionResultActivity extends AppCompatActivity implements ISessionActivity {

    private Disposable createIntentDisposable;
    private VerID verID;
    private VerIDSessionResult sessionResult;
    private VerIDSessionSettings sessionSettings;
    private SessionResultPackage sessionResultPackage;
    private boolean areViewsAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_result);
        prepareSharing();
    }

    private void prepareSharing() {
        if (areViewsAdded) {
            return;
        }
        if (verID == null || sessionResult == null || sessionSettings == null) {
            return;
        }
        try {
            sessionResultPackage = new SessionResultPackage(verID, sessionSettings, sessionResult);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
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
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Session duration", String.format(Locale.ROOT, "%d seconds", sessionResult.getSessionDuration(TimeUnit.SECONDS))));
            sessionResult.getSessionDiagnostics().ifPresent(diagnostics -> transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face detection rate", String.format(Locale.ROOT, "%.01f faces/second", (float)diagnostics.getDiagnosticImages().length/(float)sessionResult.getSessionDuration(TimeUnit.MILLISECONDS)*1000f))));
            if (sessionResult instanceof AuthenticationSessionResult) {
                ((AuthenticationSessionResult)sessionResult).getComparisonScore().ifPresent(score -> {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Comparison score", String.format(Locale.ROOT, "%.02f", score)));
                });
                ((AuthenticationSessionResult)sessionResult).getAuthenticationScoreThreshold().ifPresent(score -> {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Authentication score threshold", String.format(Locale.ROOT, "%.02f", score)));
                });
                ((AuthenticationSessionResult)sessionResult).getComparisonFaceTemplateVersion().ifPresent(faceTemplateVersion -> {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face template version", String.format(Locale.ROOT, "%d", faceTemplateVersion.getValue())));
                });
            }
            ArrayList<String> spoofDeviceConfidenceScores = new ArrayList<>();
            ArrayList<String> moireConfidenceScores = new ArrayList<>();
            for (FaceCapture faceCapture : sessionResult.getFaceCaptures()) {
                Float spoofDeviceConfidence = faceCapture.getDiagnosticInfo().getSpoofDeviceConfidence();
                if (spoofDeviceConfidence != null) {
                    spoofDeviceConfidenceScores.add(String.format(Locale.ROOT, "%.02f", spoofDeviceConfidence));
                } else if (faceCapture.getDiagnosticInfo().getSpoofDevices() != null) {
                    spoofDeviceConfidenceScores.add(String.format(Locale.ROOT, "%.02f", 0f));
                }
                Float moireConfidence = faceCapture.getDiagnosticInfo().getMoireConfidence();
                if (moireConfidence != null) {
                    moireConfidenceScores.add(String.format(Locale.ROOT, "%.02f", moireConfidence));
                }
            }
            if (!spoofDeviceConfidenceScores.isEmpty()) {
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Spoof confidence (object)", TextUtils.join(", ", spoofDeviceConfidenceScores)));
            }
            if (!moireConfidenceScores.isEmpty()) {
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Spoof confidence (moire)", TextUtils.join(", ", moireConfidenceScores)));
            }
            transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Session Settings"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Expiry time", String.format(Locale.ROOT, "%d seconds", sessionSettings.getMaxDuration(TimeUnit.SECONDS))));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Number of results to collect", String.format(Locale.ROOT, "%d", sessionSettings.getFaceCaptureCount())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Using back camera", PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.USE_BACK_CAMERA, false) ? "Yes" : "No"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Yaw threshold", String.format(Locale.ROOT, "%.01f", sessionSettings.getYawThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Pitch threshold", String.format(Locale.ROOT, "%.01f", sessionSettings.getPitchThreshold())));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Speak prompts", PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferenceKeys.SPEAK_PROMPTS, false) ? "Yes" : "No"));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Required initial face width", String.format(Locale.ROOT, "%.0f %%", sessionSettings.getExpectedFaceExtents().getProportionOfViewWidth() * 100)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Required initial face height", String.format(Locale.ROOT, "%.0f %%", sessionSettings.getExpectedFaceExtents().getProportionOfViewHeight() * 100)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Pause duration", String.format(Locale.ROOT, "%.01f seconds", (float)sessionSettings.getPauseDuration(TimeUnit.MILLISECONDS)/1000f)));
            transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face buffer size", String.format(Locale.ROOT, "%d", sessionSettings.getFaceCaptureFaceCount())));

            if (sessionResultPackage != null) {
                transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Environment"));
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Ver-ID version", sessionResultPackage.getEnvironmentSettings().getVeridVersion()));
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face template extraction threshold", String.format(Locale.ROOT, "%.01f", sessionResultPackage.getEnvironmentSettings().getFaceTemplateExtractionThreshold())));
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face detector version", String.format(Locale.ROOT, "%d", sessionResultPackage.getEnvironmentSettings().getFaceDetectorVersion())));
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Confidence threshold", String.format(Locale.ROOT, "%.01f", sessionResultPackage.getEnvironmentSettings().getConfidenceThreshold())));
            }
            transaction.commit();
            areViewsAdded = true;
            invalidateOptionsMenu();
        } catch (Exception ignore) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        areViewsAdded = false;
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
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_share).setEnabled(sessionResultPackage != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_share) {
            shareSession();
            return true;
        }
        return false;
    }

    private void shareSession() {
        Consumer<String> onFailure = message -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        };
        try {
            createIntentDisposable = Single.<Intent>create(emitter -> {
                try {
                    File shareFile = new File(getCacheDir(), "sessions");
                    shareFile.mkdirs();
                    String fileName = "Ver-ID session " + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT).format(sessionResultPackage.getResult().getSessionStartTime()) + ".zip";
                    Uri shareUri = SampleAppFileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID+".fileprovider", new File(shareFile, fileName));
                    try (OutputStream outputStream = getContentResolver().openOutputStream(shareUri)) {
                        sessionResultPackage.archiveToStream(outputStream);
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setDataAndType(shareUri, getContentResolver().getType(shareUri));
                        intent.putExtra(Intent.EXTRA_STREAM, shareUri);
                        emitter.onSuccess(intent);
                    }
                } catch (IOException e) {
                    emitter.onError(e);
                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(intent -> {
                Intent chooser = Intent.createChooser(intent, "Share session");
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(chooser);
                } else {
                    onFailure.accept("None of your applications can handle the shared session file");
                }
            }, error -> {
                onFailure.accept("Failed to create session archive");
            });
        } catch (Exception e) {
            onFailure.accept("Failed to create session package");
        }
    }

    @Override
    public void setSessionParameters(SessionParameters sessionParameters) {
        verID = sessionParameters.getVerID();
        sessionResult = sessionParameters.getSessionResult().orElse(null);
        sessionSettings = sessionParameters.getSessionSettings();
        prepareSharing();
    }
}
