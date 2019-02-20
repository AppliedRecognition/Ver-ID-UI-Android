package com.appliedrec.verid.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Parcelable;
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
import com.appliedrec.verid.core.SessionResult;
import com.appliedrec.verid.core.SessionSettings;
import com.appliedrec.verid.core.SessionTask;
import com.appliedrec.verid.core.SessionTaskDelegate;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDImage;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VerIDSessionActivity<T extends SessionSettings & Parcelable, U extends Fragment & IVerIDSessionFragment> extends AppCompatActivity implements IImageProviderServiceFactory, IImageProviderService, SessionTaskDelegate, VerIDSessionFragmentDelegate, ResultFragmentListener {

    //region Public constants
    public static final String EXTRA_SETTINGS = "com.appliedrec.verid.ui.EXTRA_SETTINGS";
    public static final String EXTRA_VERID_INSTANCE_ID = "com.appliedrec.verid.ui.EXTRA_VERID_INSTANCE_ID";
    public static final String EXTRA_RESULT = "com.appliedrec.verid.ui.EXTRA_RESULT";
    public static final String EXTRA_ERROR = "com.appliedrec.verid.ui.EXTRA_ERROR";
    public static final int REQUEST_CAMERA_PERMISSION = 1;
    //endregion

    //region Private constants
    private static final int REQUEST_CODE_TIPS = 0;
    private static final String FRAGMENT_DIALOG = "fragmentDialog";
    private static final String FRAGMENT_TAG = "verid";
    //endregion

    //region Private fields
    private VerID environment;
    private T sessionSettings;
    private long startTime;
    private U sessionFragment;
    private IFaceDetectionService faceDetectionService;
    private ThreadPoolExecutor executor;
    private int retryCount = 0;
    //endregion

    //region Activity lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ver_id);
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        T settings = intent.getParcelableExtra(EXTRA_SETTINGS);
        int instanceId = intent.getIntExtra(EXTRA_VERID_INSTANCE_ID, -1);
        if (settings == null || instanceId == -1) {
            return;
        }
        try {
            this.environment = VerID.getInstance(instanceId);
            this.sessionSettings = settings;
            if (savedInstanceState == null) {
                sessionFragment = makeVerIDSessionFragment();
                getSupportFragmentManager().beginTransaction().add(R.id.container, sessionFragment, FRAGMENT_TAG).commit();
            } else {
                //noinspection unchecked
                sessionFragment = (U) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
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
            startSessionTask();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public void onBackPressed() {
        finishCancel();
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
                CameraPermissionErrorDialog.newInstance(getString(R.string.access_to_camera)).show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (sessionFragment != null) {
                sessionFragment.startCamera();
                startSessionTask();
            }
        }
    }

    //endregion

    //region Session lifecycle

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

    private void showResult(SessionResult sessionResult) {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (sessionSettings.getShowResult()) {
            Fragment resultFragment = makeResultFragment(sessionResult);
            sessionFragment = null;
            getSupportFragmentManager().beginTransaction().replace(R.id.container, resultFragment, FRAGMENT_TAG).commitAllowingStateLoss();
        } else {
            finishWithResult(sessionResult);
        }
    }

    private void finishWithResult(SessionResult sessionResult) {
        if (sessionResult.isCanceled()) {
            finishCancel();
            return;
        }
        if (sessionResult.getError() != null) {
            finishWithError(sessionResult.getError());
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT, sessionResult);
        setResult(RESULT_OK, result);
        finish();
    }

    private void finishWithError(Exception error) {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_ERROR, error);
        setResult(RESULT_OK, result);
        finish();
    }

    private void finishCancel() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    //endregion

    //region Ver-ID session task delegate

    @Override
    public void onProgress(SessionTask sessionTask, SessionResult sessionResult, FaceDetectionResult faceDetectionResult) {
        if (faceDetectionService == null) {
            return;
        }
        RectF defaultFaceBounds = faceDetectionService.getDefaultFaceBounds(faceDetectionResult.getImageSize());
        if (defaultFaceBounds == null) {
            return;
        }
        @Nullable String labelText;
        boolean isHighlighted;
        RectF ovalBounds;
        @Nullable RectF cutoutBounds;
        @Nullable EulerAngle faceAngle;
        boolean showArrow;
        @Nullable EulerAngle offsetAngleFromBearing;
        sessionFragment.didProduceSessionResultFromFaceDetectionResult(sessionResult, faceDetectionResult);
        if (sessionResult.isProcessing()) {
            labelText = "Please wait";
            isHighlighted = true;
            ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
            cutoutBounds = null;
            faceAngle = null;
            showArrow = false;
            offsetAngleFromBearing = null;
        } else {
            switch (faceDetectionResult.getStatus()) {
                case FACE_FIXED:
                case FACE_ALIGNED:
                    labelText = getString(R.string.great_hold_it);
                    isHighlighted = true;
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = null;
                    faceAngle = null;
                    showArrow = false;
                    offsetAngleFromBearing = null;
                    break;
                case FACE_MISALIGNED:
                    labelText = getString(R.string.slowly_turn_to_follow_arror);
                    isHighlighted = false;
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = null;
                    faceAngle = faceDetectionResult.getFaceAngle();
                    showArrow = true;
                    offsetAngleFromBearing = faceDetectionService.offsetFromAngleToBearing(faceAngle != null ? faceAngle : new EulerAngle(), faceDetectionResult.getRequestedBearing());
                    break;
                case FACE_TURNED_TOO_FAR:
                    labelText = null;
                    isHighlighted = false;
                    ovalBounds = faceDetectionResult.getFaceBounds() != null ? faceDetectionResult.getFaceBounds() : defaultFaceBounds;
                    cutoutBounds = null;
                    faceAngle = null;
                    showArrow = false;
                    offsetAngleFromBearing = null;
                    break;
                default:
                    labelText = getString(R.string.move_face_into_oval);
                    isHighlighted = false;
                    ovalBounds = defaultFaceBounds;
                    cutoutBounds = faceDetectionResult.getFaceBounds();
                    faceAngle = null;
                    showArrow = false;
                    offsetAngleFromBearing = null;
            }
        }
        Matrix matrix = sessionFragment.imageScaleTransformAtImageSize(faceDetectionResult.getImageSize());
        matrix.mapRect(ovalBounds);
        if (cutoutBounds != null) {
            matrix.mapRect(cutoutBounds);
        }
        sessionFragment.drawCameraOverlay(faceDetectionResult.getRequestedBearing(), labelText, isHighlighted, ovalBounds, cutoutBounds, faceAngle, showArrow, offsetAngleFromBearing);
        if (sessionResult.getError() != null) {
            if (retryCount < sessionSettings.getMaxRetryCount()) {
                showFailureDialog(faceDetectionResult, sessionResult);
            }
        }
    }

    @Override
    public void onComplete(SessionTask sessionTask, SessionResult sessionResult) {
        if (sessionResult.isCanceled()) {
            return;
        }
        showResult(sessionResult);
    }

    //endregion

    //region Failure dialog

    protected ISessionFailureDialogFactory makeSessionFailureDialogFactory() {
        return new SessionFailureDialogFactory();
    }

    protected void showFailureDialog(FaceDetectionResult faceDetectionResult, SessionResult sessionResult) {
        String message;
        if (faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_TURNED_TOO_FAR) {
            message = getString(R.string.you_may_have_turned_too_far);
        } else if (faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_TURNED_OPPOSITE || faceDetectionResult.getStatus() == FaceDetectionStatus.FACE_LOST) {
            message = getString(R.string.turn_your_head_in_the_direction_of_the_arrow);
        } else {
            return;
        }
        ISessionFailureDialogFactory dialogFactory = makeSessionFailureDialogFactory();
        if (dialogFactory == null) {
            return;
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
                startActivityForResult(intent, REQUEST_CODE_TIPS);
            }

            @Override
            public void onRetry() {
                retryCount ++;
                startSessionTask();
            }
        }, sessionSettings);
        if (dialog == null) {
            return;
        }
        executor.shutdownNow();
        dialog.show();
    }

    //endregion

    //region Result evaluation service factory

    protected IResultEvaluationServiceFactory<T> makeResultEvaluationServiceFactory() {
        return new ResultEvaluationServiceFactory<>(this, environment);
    }

    //endregion


    //region Image provider service factory

    protected IImageProviderServiceFactory makeImageProviderServiceFactory() {
        return this;
    }

    @Override
    public IImageProviderService makeImageProviderService() {
        return this;
    }

    //endregion

    //region Image provider service

    @Override
    public VerIDImage dequeueImage() throws Exception {
        if (startTime + sessionSettings.getExpiryTime() < System.currentTimeMillis()) {
            throw new Exception("Session expired");
        }
        if (sessionFragment != null) {
            return sessionFragment.dequeueImage();
        }
        throw new Exception("Image provider is null");
    }

    //endregion

    //region Fragment creation

    @SuppressWarnings("unchecked")
    protected U makeVerIDSessionFragment() {
        if (sessionSettings instanceof RegistrationSessionSettings) {
            return (U)VerIDRegistrationSessionFragment.newInstance((RegistrationSessionSettings)sessionSettings);
        } else {
            return (U) VerIDSessionFragment.newInstance();
        }
    }

    protected Fragment makeResultFragment(SessionResult sessionResult) {
        int resourceId;
        if (sessionSettings instanceof AuthenticationSessionSettings) {
            if (sessionResult.getError() == null) {
                resourceId = R.string.auth_result;
            } else {
                resourceId = R.string.auth_failed;
            }
        } else if (sessionSettings instanceof RegistrationSessionSettings) {
            if (sessionResult.getError() == null) {
                resourceId = R.string.registration_result;
            } else {
                resourceId = R.string.registration_failed;
            }
        } else if (sessionResult.getError() == null) {
            resourceId = R.string.liveness_result;
        } else {
            resourceId = R.string.liveness_failure;
        }
        if (getSupportActionBar() != null) {
            if (sessionResult.getError() == null) {
                getSupportActionBar().setTitle(R.string.success);
            } else {
                getSupportActionBar().setTitle(R.string.failed);
            }
        }
        return ResultFragment.newInstance(sessionResult, getString(resourceId));
    }

    //endregion

    //region Face detection service factory

    protected IFaceDetectionServiceFactory makeFaceDetectionServiceFactory() {
        return new FaceDetectionServiceFactory(environment);
    }

    //endregion

    //region Image writer service factory

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
    public void veridSessionFragmentDidCancel(IVerIDSessionFragment fragment) {
        finishCancel();
    }

    @Override
    public void veridSessionFragmentDidCaptureImage(YuvImage image, int exifOrientation) {

    }

    //endregion

    //region Result fragment listener

    @Override
    public void onResultFragmentDismissed(ResultFragment resultFragment) {
        finishWithResult(resultFragment.getSessionResult());
    }

    //endregion
}