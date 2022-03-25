/**
 * This activity demonstrates how Ver-ID can be used in a kiosk situation.
 *
 * The activity overlays the camera preview with a semi-transparent background and waits until
 * an user steps in front of the camera. As soon as a face is detected the activity launches
 * a Ver-ID liveness detection session. When the session finishes the activity asks the user to
 * step away from the camera to make room for the next user.
 *
 * The liveness detection session can easily be substituted for an authentication session.
 * Alternatively the collected faces can be passed to UserIdentification's identifyUsersInFace
 * function to identify the user in the face.
 */


package com.appliedrec.verid.sample;

import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.FaceDetectionImage;
import com.appliedrec.verid.core2.IFaceTracking;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivityContinuousLivenessBinding;
import com.appliedrec.verid.ui2.CameraLocation;
import com.appliedrec.verid.ui2.CameraWrapper;
import com.appliedrec.verid.ui2.ISessionView;
import com.appliedrec.verid.ui2.IStringTranslator;
import com.appliedrec.verid.ui2.SessionView;
import com.appliedrec.verid.ui2.TranslatedStrings;
import com.appliedrec.verid.ui2.VerIDImageIterator;
import com.appliedrec.verid.ui2.VerIDSessionInView;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ContinuousLivenessActivity extends AppCompatActivity implements IVerIDLoadObserver, Iterable<FaceBounds>, ISessionView.SessionViewListener, CameraWrapper.Listener {

    ActivityContinuousLivenessBinding viewBinding;
    private Disposable faceDetectionDisposable;
    private final LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
    private VerID verID;
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);
    private CameraWrapper cameraWrapper;
    private VerIDImageIterator imageIterator;
    private IStringTranslator stringTranslator;
    private VerIDSessionInView<SessionView> verIDSessionInView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityContinuousLivenessBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        stringTranslator = new TranslatedStrings(this, null);

        viewBinding.retryButton.setOnClickListener(view -> startSession());
        viewBinding.idle.setVisibility(View.VISIBLE);
        viewBinding.sessionResult.setVisibility(View.VISIBLE);
        viewBinding.sessionView.setDefaultFaceExtents(sessionSettings.getExpectedFaceExtents());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewBinding = null;
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        faceDetectionDisposable = null;
        verID = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        cameraWrapper = new CameraWrapper(this, CameraLocation.FRONT, imageIterator, null);
        cameraWrapper.addListener(this);
        cameraWrapper.setPreviewClass(viewBinding.sessionView.getPreviewClass());
        runFaceDetectionUntil(hasFace -> hasFace, this::startSession);
    }

    @Override
    protected void onStop() {
        super.onStop();
        cameraWrapper.removeListener(this);
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        verID = verid;
        imageIterator = new VerIDImageIterator(verID);
    }

    @Override
    public void onVerIDUnloaded() {
        verID = null;
    }

    @SuppressWarnings("unchecked")
    private void runFaceDetectionUntil(Predicate<Boolean> predicate, Action onComplete) {
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        IFaceTracking<FaceDetectionImage> faceTracking = startFaceTracking();
        faceDetectionDisposable = Flowable.fromObservable(Observable.fromIterable(imageIterator), BackpressureStrategy.LATEST)
                .map(image -> {
                    VerIDImage<FaceDetectionImage> verIDImage = (VerIDImage<FaceDetectionImage>) image;
                    Face face = faceTracking.trackFaceInImage(verIDImage.createFaceDetectionImage());
                    return face != null;
                })
                .takeUntil(predicate)
                .ignoreElements()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> {
                    cameraWrapper.stop();
                    imageIterator.deactivate();
                    if (viewBinding == null) {
                        return;
                    }
                    viewBinding.sessionView.removeListener(this);
                    viewBinding.sessionView.setFaceDetectionResult(null, null);
                })
                .doOnSubscribe(disposable -> {
                    if (viewBinding == null) {
                        return;
                    }
                    viewBinding.sessionView.addListener(this);
                    imageIterator.activate();
                })
                .subscribe(
                        onComplete,
                        error -> Log.e("Ver-ID", Objects.requireNonNull(error.getLocalizedMessage()))
                );
    }

    @SuppressWarnings("unchecked")
    private <DetectionImage> IFaceTracking<DetectionImage> startFaceTracking() {
        return (IFaceTracking<DetectionImage>) verID.getFaceDetection().startFaceTracking();
    }

    private void startSession() {
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        faceDetectionDisposable = null;
        if (viewBinding == null) {
            return;
        }
        if (isSessionRunning.compareAndSet(false, true)) {
            viewBinding.sessionResult.setVisibility(View.GONE);
            verIDSessionInView = new VerIDSessionInView<>(verID, sessionSettings, viewBinding.sessionView, stringTranslator);
            verIDSessionInView.getSessionResultLiveData().observe(this, this::onSessionResult);
            verIDSessionInView.start();
        }
    }

    private void onSessionResult(VerIDSessionResult sessionResult) {
        if (!isSessionRunning.compareAndSet(true, false)) {
            return;
        }
        if (viewBinding == null) {
            return;
        }
        if (verIDSessionInView != null) {
            verIDSessionInView.getSessionResultLiveData().removeObservers(this);
            verIDSessionInView.stop();
        }
        viewBinding.sessionView.setFaceDetectionResult(null, null);
        viewBinding.sessionResult.setVisibility(View.VISIBLE);
        viewBinding.idle.setVisibility(View.GONE);
        viewBinding.success.setVisibility(sessionResult.getError().isPresent() ? View.GONE : View.VISIBLE);
        viewBinding.failure.setVisibility(sessionResult.getError().isPresent() ? View.VISIBLE : View.GONE);
        runFaceDetectionUntil(hasFace -> !hasFace, () -> {
            if (viewBinding == null) {
                return;
            }
            viewBinding.idle.setVisibility(View.VISIBLE);
            viewBinding.success.setVisibility(View.GONE);
            viewBinding.failure.setVisibility(View.GONE);
            runFaceDetectionUntil(hasFace -> hasFace, this::startSession);
        });
    }

    @NonNull
    @Override
    public Iterator<FaceBounds> iterator() {
        return viewBinding.sessionView;
    }

    @Override
    public void onPreviewSurfaceCreated(Surface surface) {
        cameraWrapper.setPreviewSurface(surface);
        startCamera();
    }

    @Override
    public void onPreviewSurfaceDestroyed() {
        cameraWrapper.stop();
    }

    private void startCamera() {
        if (viewBinding == null) {
            return;
        }
        cameraWrapper.start(viewBinding.sessionView.getWidth(), viewBinding.sessionView.getHeight(), viewBinding.sessionView.getDisplayRotation());
    }

    @Override
    public void onCameraPreviewSize(int width, int height, int sensorOrientation) {
        if (viewBinding == null) {
            return;
        }
        viewBinding.sessionView.setPreviewSize(width, height, sensorOrientation);
    }

    @Override
    public void onCameraError(VerIDSessionException error) {
        long now = System.currentTimeMillis();
        onSessionResult(new VerIDSessionResult(error, now, now, null));
    }
}