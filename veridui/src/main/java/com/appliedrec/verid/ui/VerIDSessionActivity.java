package com.appliedrec.verid.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import com.appliedrec.verid.core.AuthenticationSessionSettings;
import com.appliedrec.verid.core.EulerAngle;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.FaceDetectionServiceFactory;
import com.appliedrec.verid.core.FaceDetectionStatus;
import com.appliedrec.verid.core.IFaceDetectionService;
import com.appliedrec.verid.core.IFaceDetectionServiceFactory;
import com.appliedrec.verid.core.IImageProviderService;
import com.appliedrec.verid.core.IImageProviderServiceFactory;
import com.appliedrec.verid.core.IImageWriterServiceFactory;
import com.appliedrec.verid.core.IResultEvaluationService;
import com.appliedrec.verid.core.IResultEvaluationServiceFactory;
import com.appliedrec.verid.core.ImageWriterServiceFactory;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.ResultEvaluationServiceFactory;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.core.SessionTask;
import com.appliedrec.verid.core.SessionTaskDelegate;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDImage;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Activity that runs a Ver-ID session
 * @param <T> Session settings type
 * @param <U> Fragment type
 * @since 1.0.0
 */
public class VerIDSessionActivity<T extends VerIDSessionSettings & Parcelable, U extends Fragment & IVerIDSessionFragment> extends AppCompatActivity implements IImageProviderServiceFactory, IImageProviderService, SessionTaskDelegate, VerIDSessionFragmentDelegate, ResultFragmentListener, IStringTranslator {

    //region Public constants
    /**
     * Intent extra name for settings
     * @since 1.0.0
     */
    public static final String EXTRA_SETTINGS = "com.appliedrec.verid.ui.EXTRA_SETTINGS";
    /**
     * Intent extra name for Ver-ID instance
     * @since 1.0.0
     */
    public static final String EXTRA_VERID_INSTANCE_ID = "com.appliedrec.verid.ui.EXTRA_VERID_INSTANCE_ID";
    /**
     * Intent extra name for session result
     * @since 1.0.0
     */
    public static final String EXTRA_RESULT = "com.appliedrec.verid.ui.EXTRA_RESULT";
    /**
     * Intent extra name for error
     * @since 1.0.0
     */
    public static final String EXTRA_ERROR = "com.appliedrec.verid.ui.EXTRA_ERROR";

    public static final String EXTRA_TRANSLATION_FILE_PATH = "com.appliedrec.verid.ui.EXTRA_TRANSLATION_FILE_PATH";

    public static final String EXTRA_TRANSLATION_ASSET_PATH = "com.appliedrec.verid.ui.EXTRA_TRANSLATION_ASSET_PATH";
    //endregion

    //region Other constants
    static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_CODE_TIPS = 0;
    private static final String FRAGMENT_DIALOG = "fragmentDialog";
    private static final String FRAGMENT_VERID = "verid";
    //endregion

    //region Private fields
    private VerID environment;
    private T sessionSettings;
    private long startTime;
    private U sessionFragment;
    private IFaceDetectionService faceDetectionService;
    private ThreadPoolExecutor executor;
    private int retryCount = 0;
    private TranslatedStrings translatedStrings;
    //endregion

    //region Activity lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewId());
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        T settings = intent.getParcelableExtra(EXTRA_SETTINGS);
        int instanceId = intent.getIntExtra(EXTRA_VERID_INSTANCE_ID, -1);
        if (settings == null || instanceId == -1) {
            return;
        }
        translatedStrings = new TranslatedStrings(this, intent);
        try {
            this.environment = VerID.getInstance(instanceId);
            this.sessionSettings = settings;
            if (savedInstanceState == null) {
                sessionFragment = makeVerIDSessionFragment();
                addVerIDSessionFragment();
            } else {
                sessionFragment = getVerIDSessionFragment();
            }
        } catch (Exception e) {
            finishWithError(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_TIPS) {
            startSessionTask();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionFragment != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission();
                return;
            }
            sessionFragment.startCamera();
            startSessionTask();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        shutDownExecutor();
        clearCameraOverlays();
    }

    @Override
    public void onBackPressed() {
        finishCancel();
    }

    //endregion

    //region Cleanup
    /**
     * Shut down the Ver-ID session executor
     */
    protected void shutDownExecutor() {
        if (executor != null) {
            ArrayList<Runnable> tasks = new ArrayList<>();
            executor.getQueue().drainTo(tasks);
            for (Runnable task : tasks) {
                if (task instanceof SessionTask) {
                    ((SessionTask)task).cancel(true);
                }
            }
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Clear face oval and text laid over the camera fragment
     */
    protected void clearCameraOverlays() {
        if (sessionFragment != null) {
            sessionFragment.clearCameraOverlay();
        }
    }
    //endregion

    //region Camera permissions

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new CameraPermissionConfirmationDialog().show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new CameraPermissionErrorDialog().show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            } else if (sessionFragment != null) {
                sessionFragment.startCamera();
                startSessionTask();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    //endregion

    //region Session lifecycle

    /**
     * Start Ver-ID session
     *
     * <p>Begin executing Ver-ID session task. The task will report its progress by calling the {@link #onProgress(SessionTask, VerIDSessionResult, FaceDetectionResult) onProgress} method. When the session completes it will call the {@link #onComplete(SessionTask, VerIDSessionResult) onComplete} method.</p>
     * @since 1.0.0
     */
    protected void startSessionTask() {
        try {
            startTime = System.currentTimeMillis();
            faceDetectionService = makeFaceDetectionServiceFactory().makeFaceDetectionService(sessionSettings);
            IResultEvaluationService resultEvaluationService = makeResultEvaluationServiceFactory().makeResultEvaluationService(sessionSettings);
            SessionTask sessionTask = new SessionTask(makeImageProviderService(), faceDetectionService, resultEvaluationService, makeImageWriterServiceFactory().makeImageWriterService());
            if (executor == null || executor.isShutdown()) {
                executor = new ThreadPoolExecutor(0, 1, Integer.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            }
            sessionTask.executeOnExecutor(executor, this);
        } catch (Exception e) {
            finishWithError(e);
        }
    }

    /**
     * Finish the session with a result
     *
     * <p>If the result is canceled this method will call {@link #finishCancel()}.</p>
     * <p>If the result contains an error this method will call {@link #finishWithError(Exception)}.</p>
     * <p>Otherwise this method sets the activity result and finishes the activity.</p>
     * <p>Override this method if wish to handle the result in this activity instead of passing it to the parent activity.</p>
     * @param sessionResult
     * @since 1.0.0
     */
    protected void finishWithResult(VerIDSessionResult sessionResult) {
        shutDownExecutor();
        clearCameraOverlays();
        Intent result = new Intent();
        if (sessionResult.getError() != null) {
            result.putExtra(EXTRA_ERROR, sessionResult.getError());
        }
        result.putExtra(EXTRA_RESULT, sessionResult);
        setResult(RESULT_OK, result);
        finish();
    }

    /**
     * Finish the session with error
     *
     * <p>This method sets the activity result and finishes the activity.</p>
     * <p>Override this method if you wish to handle the error in this activity instead of passing it to the parent activity.</p>
     * @param error
     * @since 1.0.0
     */
    protected void finishWithError(Exception error) {
        shutDownExecutor();
        clearCameraOverlays();
        Intent result = new Intent();
        result.putExtra(EXTRA_ERROR, error);
        result.putExtra(EXTRA_RESULT, new VerIDSessionResult(error));
        setResult(RESULT_OK, result);
        finish();
    }

    /**
     * Cancel the session
     *
     * <p>This method sets the activity result to {@code Activity.RESULT_CENCELED} and finishes the activity.</p>
     * <p>Override this method if you wish to handle the session cancellation in this activity instead of finishing the activity.</p>
     * @since 1.0.0
     */
    protected void finishCancel() {
        shutDownExecutor();
        clearCameraOverlays();
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * @return Session settings
     * @since 1.0.0
     */
    @Override
    public T getSessionSettings() {
        return sessionSettings;
    }

    /**
     * @return Ver-ID environment used to run the Ver-ID session
     * @since 1.0.0
     */
    protected VerID getEnvironment() {
        return environment;
    }

    //endregion

    //region Ver-ID session task delegate

    /**
     * Called by {@link SessionTask} when the task generates a face detection result
     * @param sessionTask The task that progressed
     * @param sessionResult The session result at the point of progress
     * @param faceDetectionResult Face detection result that was used to generate the session result
     * @since 1.0.0
     */
    @Override
    @MainThread
    public void onProgress(SessionTask sessionTask, VerIDSessionResult sessionResult, FaceDetectionResult faceDetectionResult) {
        if (faceDetectionService == null) {
            return;
        }
        RectF defaultFaceBounds = faceDetectionService.getFaceAlignmentDetection().getDefaultFaceBounds(faceDetectionResult.getImageSize());
        if (defaultFaceBounds == null) {
            return;
        }
        EulerAngle offsetAngleFromBearing = null;
        if (faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_MISALIGNED) {
            offsetAngleFromBearing = faceDetectionService.getAngleBearingEvaluation().offsetFromAngleToBearing(faceDetectionResult.getFaceAngle() != null ? faceDetectionResult.getFaceAngle() : new EulerAngle(), faceDetectionResult.getRequestedBearing());
        }
        if (sessionFragment != null) {
            sessionFragment.drawFaceFromResult(faceDetectionResult, sessionResult, defaultFaceBounds, offsetAngleFromBearing);
        }
        if (sessionResult.getError() != null && retryCount < sessionSettings.getMaxRetryCount() && showFailureDialog(faceDetectionResult, sessionResult)) {
            shutDownExecutor();
            clearCameraOverlays();
        }
    }

    /**
     * Called by {@link SessionTask} when the session completes.
     * <p>The default implementation shows the session result if the session settings requested it. Otherwise the method calls {@link #finishWithResult(VerIDSessionResult)}.</p>
     * @param sessionTask Task that completed
     * @param sessionResult Result of the task
     * @since 1.0.0
     */
    @Override
    public void onComplete(final SessionTask sessionTask, final VerIDSessionResult sessionResult) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sessionFragment != null) {
                    sessionFragment.clearCameraPreview();
                }
                if (executor == null || executor.isShutdown()) {
                    return;
                }
                if (sessionSettings.getShowResult()) {
                    shutDownExecutor();
                    clearCameraOverlays();
                    Fragment resultFragment = makeResultFragment(sessionResult);
                    sessionFragment = null;
                    addResultFragment(resultFragment);
                } else {
                    finishWithResult(sessionResult);
                }
            }
        });
    }

    //endregion

    //region Failure dialog

    /**
     * @return Instance of {@link ISessionFailureDialogFactory}
     * @since 1.0.0
     */
    protected ISessionFailureDialogFactory makeSessionFailureDialogFactory() {
        return new SessionFailureDialogFactory();
    }

    /**
     * Shows a dialog when the Ver-ID session fails due to the user not failing liveness detection and the user tried fewer than the {@link VerIDSessionSettings#getMaxRetryCount() maximum number of tries} set in the session settings.
     * @param faceDetectionResult
     * @param sessionResult
     * @return {@literal true} if the dialog was shown
     * @since 1.0.0
     */
    protected boolean showFailureDialog(FaceDetectionResult faceDetectionResult, VerIDSessionResult sessionResult) {
        String message;
        if (faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_TURNED_TOO_FAR) {
            message = translatedStrings.getTranslatedString("You may have turned too far");
        } else if (faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_TURNED_OPPOSITE || faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_LOST) {
            message = translatedStrings.getTranslatedString("Turn your head in the direction of the arrow");
        } else if (faceDetectionResult.getStatus() == FaceDetectionStatus.MOVED_TOO_FAST) {
            message = translatedStrings.getTranslatedString("Please turn slowly");
        } else {
            return false;
        }
        ISessionFailureDialogFactory dialogFactory = makeSessionFailureDialogFactory();
        if (dialogFactory == null) {
            return false;
        }
        AlertDialog dialog = dialogFactory.makeDialog(this, message, new SessionFailureDialogListener() {
            @Override
            public void onCancel() {
                finishCancel();
            }

            @Override
            public void onShowTips() {
                retryCount ++;
                Intent intent = new Intent(VerIDSessionActivity.this, TipsActivity.class);
                intent.putExtras(getIntent());
                startActivityForResult(intent, REQUEST_CODE_TIPS);
            }

            @Override
            public void onRetry() {
                retryCount ++;
                startSessionTask();
            }
        }, sessionSettings);
        if (dialog == null) {
            return false;
        }
        shutDownExecutor();
        dialog.show();
        return true;
    }

    //endregion

    //region Result evaluation service factory

    /**
     * Override this if you wish to supply your own instance of {@link IResultEvaluationServiceFactory}.
     * @return Result evaluation service factory
     * @since 1.0.0
     */
    protected IResultEvaluationServiceFactory<T> makeResultEvaluationServiceFactory() {
        return new ResultEvaluationServiceFactory<>(this, environment);
    }

    //endregion


    //region Image provider service factory

    /**
     * Override this if you want to supply your own instance of {@link IImageProviderServiceFactory}.
     * <p>The default implementation uses this activity as the image provider service factory.</p>
     * @return Image provider service factory
     * @since 1.0.0
     */
    protected IImageProviderServiceFactory makeImageProviderServiceFactory() {
        return this;
    }

    /**
     * Override this if you want to supply your own {@link IImageProviderService}.
     * <p>The default implementation uses this activity as the image provider service.</p>
     * @return Image provider service
     * @since 1.0.0
     */
    @Override
    public IImageProviderService makeImageProviderService() {
        return this;
    }

    //endregion

    //region Image provider service

    /**
     * Implementation of {@link IImageProviderService}.
     * @return Image obtained from {@link VerIDSessionFragment}
     * @throws Exception If the session fragment is {@literal null} or if the session expired
     * @since 1.0.0
     */
    @Override
    public VerIDImage dequeueImage() throws Exception {
        if (startTime + sessionSettings.getExpiryTime() < System.currentTimeMillis()) {
            throw new TimeoutException("Session expired");
        }
        if (sessionFragment != null) {
            return sessionFragment.dequeueImage();
        }
        throw new Exception("Image provider is null");
    }

    //endregion

    //region Fragment creation

    /**
     * Create an instance of {@code Fragment} that implements the {@link IVerIDSessionFragment} interface.
     * @return Fragment that implements {@link IVerIDSessionFragment}
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    protected U makeVerIDSessionFragment() {
        if (sessionSettings instanceof RegistrationSessionSettings) {
            return (U)new VerIDRegistrationSessionFragment();
        } else {
            return (U)new VerIDSessionFragment();
        }
    }

    /**
     * Create an instance of a {@code Fragment} that shows the result of the session.
     * <p>This fragment will only be shown if {@link VerIDSessionSettings#getShowResult()} is set to {@literal true}.</p>
     * <p>The fragment must call {@link ResultFragmentListener#onResultFragmentDismissed(IResultFragment)} on the activity it's attached to when finished.</p>
     * @param sessionResult The result to display in the fragment
     * @return Fragment
     * @since 1.0.0
     */
    protected Fragment makeResultFragment(VerIDSessionResult sessionResult) {
        String result;
        if (sessionSettings instanceof AuthenticationSessionSettings) {
            if (sessionResult.getError() == null) {
                result = translatedStrings.getTranslatedString("Great. You authenticated using your face.");
            } else {
                result = translatedStrings.getTranslatedString("Authentication failed");
            }
        } else if (sessionSettings instanceof RegistrationSessionSettings) {
            if (sessionResult.getError() == null) {
                result = translatedStrings.getTranslatedString("Great. You are now registered.");
            } else {
                result = translatedStrings.getTranslatedString("Registration failed");
            }
        } else if (sessionResult.getError() == null) {
            result = translatedStrings.getTranslatedString("Great. Session succeeded.");
        } else {
            result = translatedStrings.getTranslatedString("Session failed");
        }
        if (getSupportActionBar() != null) {
            if (sessionResult.getError() == null) {
                getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Success"));
            } else {
                getSupportActionBar().setTitle(translatedStrings.getTranslatedString("Failed"));
            }
        }
        return ResultFragment.newInstance(sessionResult, result);
    }

    //endregion

    //region Controlling layout

    protected int getContentViewId() {
        return R.layout.activity_ver_id;
    }

    protected void addVerIDSessionFragment() {
        getSupportFragmentManager().beginTransaction().add(R.id.container, sessionFragment, FRAGMENT_VERID).commit();
    }

    protected U getVerIDSessionFragment() {
        //noinspection unchecked
        return (U) getSupportFragmentManager().findFragmentByTag(FRAGMENT_VERID);
    }

    protected void addResultFragment(Fragment resultFragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, resultFragment, FRAGMENT_VERID).commitAllowingStateLoss();
    }

    //endregion

    //region Face detection service factory

    /**
     * Override this if you wish to supply your own instance of {@link IFaceDetectionServiceFactory}.
     * <p>The default implementation returns an instance of {@link FaceDetectionServiceFactory}.</p>
     * @return Instance of {@link IFaceDetectionServiceFactory}
     * @since 1.0.0
     */
    protected IFaceDetectionServiceFactory makeFaceDetectionServiceFactory() {
        return new FaceDetectionServiceFactory(environment);
    }

    //endregion

    //region Image writer service factory

    /**
     * Override this method if you wish to supply your own instance of {@link IImageWriterServiceFactory}.
     * <p>The default implementation returns an instance of {@link ImageWriterServiceFactory}.</p>
     * @return Instance of {@link IImageWriterServiceFactory}
     * @since 1.0.0
     */
    protected IImageWriterServiceFactory makeImageWriterServiceFactory() {
        return new ImageWriterServiceFactory(this);
    }

    //endregion

    //region Ver-ID session fragment delegate

    @Override
    public void veridSessionFragmentDidFailWithError(IVerIDSessionFragment fragment, Exception error) {
        finishWithError(error);
    }

    @Override
    public String getTranslatedString(String original, Object... args) {
        return translatedStrings.getTranslatedString(original, args);
    }

    //endregion

    //region Result fragment listener

    /**
     * Called when the user dismisses the fragment showing the session result.
     * @param resultFragment The fragment that was dismissed
     * @since 1.0.0
     */
    @Override
    public void onResultFragmentDismissed(IResultFragment resultFragment) {
        finishWithResult(resultFragment.getSessionResult());
    }

    //endregion
}