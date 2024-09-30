package com.appliedrec.verid.sample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.AuthenticationSessionResult;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.sample.databinding.DiagnosticUploadConsentBinding;
import com.appliedrec.verid.sample.preferences.PreferenceKeys;
import com.appliedrec.verid.sample.sharing.SampleAppFileProvider;
import com.appliedrec.verid.sample.sharing.SessionDiagnosticUpload;
import com.appliedrec.verid.sample.sharing.SessionDiagnosticUploadWorker;
import com.appliedrec.verid.ui2.ISessionActivity;
import com.appliedrec.verid.ui2.SessionParameters;
import com.appliedrec.verid.ui2.sharing.SessionResultPackage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class SessionResultActivity extends AppCompatActivity implements ISessionActivity {

    private Disposable createIntentDisposable;
    private VerID verID;
    private VerIDSessionResult sessionResult;
    private VerIDSessionSettings sessionSettings;
    private SessionResultPackage sessionResultPackage;
    private boolean areViewsAdded = false;
    private boolean diagnosticUploadDone = false;

    private SessionDiagnosticUpload diagnosticUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_result);
        prepareSharing();
        diagnosticUploadDone = savedInstanceState != null && savedInstanceState.getBoolean("diagnosticUploadDone", false);
        diagnosticUpload = new SessionDiagnosticUpload(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("diagnosticUploadDone", diagnosticUploadDone);
    }

    @Override
    protected void onResume() {
        super.onResume();
        uploadDiagnostics();
    }

    private void uploadDiagnostics() {
        if (diagnosticUploadDone) {
            return;
        }
        diagnosticUpload.askForUserConsent(granted -> {
            diagnosticUploadDone = true;
            if (granted) {
                diagnosticUpload.upload(sessionResultPackage);
            }
            return null;
        });
    }

    private void prepareSharing() {
        if (areViewsAdded) {
            return;
        }
        if (verID == null || sessionResult == null || sessionSettings == null) {
            return;
        }
        try {
            sessionResultPackage = new SessionResultPackage(verID, sessionSettings, sessionResult, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME);
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
            ArrayList<String> faceCoveringScores = new ArrayList<>();
            ArrayList<String> glassesScores = new ArrayList<>();
            ArrayList<String> sunglassesScores = new ArrayList<>();
            for (FaceCapture faceCapture : sessionResult.getFaceCaptures()) {
                if (!faceCapture.getDiagnosticInfo().isEmpty()) {
                    if (faceCapture.getDiagnosticInfo().getFaceCoveringScore() != null) {
                        faceCoveringScores.add(String.format(Locale.ROOT, "%.02f", faceCapture.getDiagnosticInfo().getFaceCoveringScore()));
                    }
                    if (faceCapture.getDiagnosticInfo().getGlassesScore() != null) {
                        glassesScores.add(String.format(Locale.ROOT, "%.02f", faceCapture.getDiagnosticInfo().getGlassesScore()));
                    }
                    if (faceCapture.getDiagnosticInfo().getSunglassesScore() != null) {
                        sunglassesScores.add(String.format(Locale.ROOT, "%.02f", faceCapture.getDiagnosticInfo().getSunglassesScore()));
                    }
                }
            }
            if (!faceCoveringScores.isEmpty() || !glassesScores.isEmpty() || !sunglassesScores.isEmpty()) {
                if (!faceCoveringScores.isEmpty()) {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance(faceCoveringScores.size() > 1 ? "Face covering scores" : "Face covering score", String.join(", ", faceCoveringScores)));
                }
                if (!glassesScores.isEmpty()) {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance(glassesScores.size() > 1 ? "Glasses scores" : "Glasses score", String.join(", ", glassesScores)));
                }
                if (!sunglassesScores.isEmpty()) {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance(sunglassesScores.size() > 1 ? "Sunglasses scores" : "Sunglasses score", String.join(", ", sunglassesScores)));
                }
            }
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
            List<Map<String,Float>> spoofDetectorScoreList = Arrays.stream(sessionResult.getFaceCaptures()).map(faceCapture -> faceCapture.getDiagnosticInfo().getSpoofConfidenceScores()).filter(confidenceScores -> !confidenceScores.isEmpty()).collect(Collectors.toList());
            if (!spoofDetectorScoreList.isEmpty()) {
                transaction.add(R.id.content, SessionResultHeadingFragment.newInstance("Passive Liveness Detection"));
                HashMap<String,ArrayList<String>> spoofDetectorScores = new HashMap<>();
                for (Map<String,Float> map : spoofDetectorScoreList) {
                    for (Map.Entry<String,Float> entry : map.entrySet()) {
                        if (!spoofDetectorScores.containsKey(entry.getKey())) {
                            spoofDetectorScores.put(entry.getKey(), new ArrayList<>());
                        }
                        spoofDetectorScores.get(entry.getKey()).add(String.format(Locale.ROOT, "%.02f", entry.getValue()));
                    }
                }
                for (Map.Entry<String,ArrayList<String>> entry : spoofDetectorScores.entrySet()) {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance(entry.getKey().substring(0, Math.min(entry.getKey().length(), 30)), String.join(", ", entry.getValue())));
                }
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
                if (sessionResultPackage.getEnvironmentSettings().getFaceDetectorVersion() == -1) {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face detector version", "MediaPipe landmarker"));
                } else {
                    transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Face detector version", String.format(Locale.ROOT, "%d", sessionResultPackage.getEnvironmentSettings().getFaceDetectorVersion())));
                }
                transaction.add(R.id.content, SessionResultEntryFragment.newInstance("Confidence threshold", String.format(Locale.ROOT, "%.01f", sessionResultPackage.getEnvironmentSettings().getConfidenceThreshold())));
            }
            transaction.commit();
            areViewsAdded = true;
            invalidateOptionsMenu();
        } catch (Exception ignore) {
            Log.e("SessionResultActivity", "Failed to prepare session result for sharing", ignore);
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
