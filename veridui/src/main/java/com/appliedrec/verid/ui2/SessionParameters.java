package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.SessionFunctions;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Parameters required by session activities
 * @since 2.0.0
 */
@Keep
public class SessionParameters {

    private final VerID verID;
    private final VerIDSessionSettings sessionSettings;
    private final CameraLocation cameraLocation;
    private final Function<Context, ? extends ISessionView> sessionViewFactory;
    private final IStringTranslator stringTranslator;
    private final SessionFunctions sessionFunctions;
    private SessionFailureDialogFactory sessionFailureDialogFactory;
    private Observer<FaceDetectionResult> faceDetectionResultObserver;
    private Observer<FaceCapture> faceCaptureObserver;
    private Observer<VerIDSessionResult> sessionResultObserver;
    private Function<Context, IImageIterator> imageIteratorFactory = VerIDImageIterator::new;
    private BiFunction<VerIDSessionResult, Activity, Intent> resultIntentSupplier;
    private Function<Activity, Intent> tipsIntentSupplier;
    private Runnable onSessionFinishedRunnable;
    private Runnable onSessionCancelledRunnable;
    private ISessionVideoRecorder videoRecorder;
    private Function<VerIDSessionResult,Boolean> sessionResultDisplayIndicator = result -> false;
    private VerIDSessionResult sessionResult;
    private Function<VerIDSessionException, Boolean> shouldRetryOnFailure;
    private ITextSpeaker textSpeaker;
    private int minImageArea = CameraPreviewHelper.getInstance().getMinImageArea();

    /**
     * Constructor
     * @param verID Instance of {@link VerID} to use for face detetion, recognition and user management
     * @param sessionSettings Session settings
     * @param cameraLocation Camera location
     * @param sessionViewFactory Session view factory
     * @param stringTranslator String translator
     * @param sessionFunctions Session functions
     * @param <V> Session view type
     * @since 2.0.0
     */
    @Keep
    public <V extends View & ISessionView> SessionParameters(@NonNull VerID verID, @NonNull VerIDSessionSettings sessionSettings, @NonNull CameraLocation cameraLocation, @NonNull Function<Context, V> sessionViewFactory, @NonNull IStringTranslator stringTranslator, @NonNull SessionFunctions sessionFunctions) {
        this.verID = verID;
        this.sessionSettings = sessionSettings;
        this.cameraLocation = cameraLocation;
        this.sessionViewFactory = sessionViewFactory;
        this.stringTranslator = stringTranslator;
        this.sessionFunctions = sessionFunctions;
    }

    @Keep
    public VerID getVerID() {
        return verID;
    }

    @Keep
    public VerIDSessionSettings getSessionSettings() {
        return sessionSettings;
    }

    @Keep
    public CameraLocation getCameraLocation() {
        return cameraLocation;
    }

    @Keep
    public Optional<ISessionVideoRecorder> getVideoRecorder() {
        return Optional.ofNullable(videoRecorder);
    }

    @Keep
    public <V extends View & ISessionView> Function<Context, V> getSessionViewFactory() {
        return (Function<Context, V>) sessionViewFactory;
    }

    @Keep
    public IStringTranslator getStringTranslator() {
        return stringTranslator;
    }

    @Keep
    public SessionFunctions getSessionFunctions() {
        return sessionFunctions;
    }

    @Keep
    public SessionFailureDialogFactory getSessionFailureDialogFactory() {
        return sessionFailureDialogFactory;
    }

    @Keep
    public void setSessionFailureDialogFactory(SessionFailureDialogFactory sessionFailureDialogFactory) {
        this.sessionFailureDialogFactory = sessionFailureDialogFactory;
    }

    @Keep
    public void setFaceDetectionResultObserver(Observer<FaceDetectionResult> faceDetectionResultObserver) {
        this.faceDetectionResultObserver = faceDetectionResultObserver;
    }

    @Keep
    public void setFaceCaptureObserver(Observer<FaceCapture> faceCaptureObserver) {
        this.faceCaptureObserver = faceCaptureObserver;
    }

    @Keep
    public void setSessionResultObserver(Observer<VerIDSessionResult> sessionResultObserver) {
        this.sessionResultObserver = sessionResultObserver;
    }

    @Keep
    public Optional<Observer<FaceDetectionResult>> getFaceDetectionResultObserver() {
        return Optional.ofNullable(faceDetectionResultObserver);
    }

    @Keep
    public Optional<Observer<FaceCapture>> getFaceCaptureObserver() {
        return Optional.ofNullable(faceCaptureObserver);
    }

    @Keep
    public Optional<Observer<VerIDSessionResult>> getSessionResultObserver() {
        return Optional.ofNullable(sessionResultObserver);
    }

    @Keep
    public Function<Context, IImageIterator> getImageIteratorFactory() {
        return imageIteratorFactory;
    }

    @Keep
    public void setImageIteratorFactory(@NonNull Function<Context, IImageIterator> imageIteratorFactory) {
        this.imageIteratorFactory = imageIteratorFactory;
    }

    @Keep
    public BiFunction<VerIDSessionResult, Activity, Intent> getResultIntentSupplier() {
        return resultIntentSupplier;
    }

    @Keep
    public Function<Activity, Intent> getTipsIntentSupplier() {
        return tipsIntentSupplier;
    }

    @Keep
    public void setResultIntentSupplier(BiFunction<VerIDSessionResult, Activity, Intent> resultIntentSupplier) {
        this.resultIntentSupplier = resultIntentSupplier;
    }

    @Keep
    public void setTipsIntentSupplier(Function<Activity, Intent> tipsIntentSupplier) {
        this.tipsIntentSupplier = tipsIntentSupplier;
    }

    @Keep
    public Optional<Runnable> getOnSessionFinishedRunnable() {
        return Optional.of(onSessionFinishedRunnable);
    }

    @Keep
    public void setOnSessionFinishedRunnable(Runnable onSessionFinishedRunnable) {
        this.onSessionFinishedRunnable = onSessionFinishedRunnable;
    }

    @Keep
    public Optional<Runnable> getOnSessionCancelledRunnable() {
        return Optional.of(onSessionCancelledRunnable);
    }

    @Keep
    public void setOnSessionCancelledRunnable(Runnable onSessionCancelledRunnable) {
        this.onSessionCancelledRunnable = onSessionCancelledRunnable;
    }

    @Keep
    public void setVideoRecorder(ISessionVideoRecorder videoRecorder) {
        this.videoRecorder = videoRecorder;
    }

    @Keep
    public Function<VerIDSessionResult, Boolean> getSessionResultDisplayIndicator() {
        return sessionResultDisplayIndicator;
    }

    @Keep
    public void setSessionResultDisplayIndicator(Function<VerIDSessionResult, Boolean> sessionResultDisplayIndicator) {
        this.sessionResultDisplayIndicator = sessionResultDisplayIndicator;
    }

    @Keep
    public Optional<VerIDSessionResult> getSessionResult() {
        return Optional.ofNullable(sessionResult);
    }

    @Keep
    public void setSessionResult(VerIDSessionResult sessionResult) {
        this.sessionResult = sessionResult;
    }

    @Keep
    public Optional<Function<VerIDSessionException, Boolean>> shouldRetryOnFailure() {
        return Optional.ofNullable(shouldRetryOnFailure);
    }

    @Keep
    public void shouldRetryOnFailure(Function<VerIDSessionException, Boolean> shouldRetryOnFailure) {
        this.shouldRetryOnFailure = shouldRetryOnFailure;
    }

    @Keep
    public Optional<ITextSpeaker> getTextSpeaker() {
        return Optional.ofNullable(textSpeaker);
    }

    @Keep
    public void setTextSpeaker(ITextSpeaker textSpeaker) {
        this.textSpeaker = textSpeaker;
    }

    @Keep
    public int getMinImageArea() {
        return minImageArea;
    }

    @Keep
    public void setMinImageArea(int minImageArea) {
        this.minImageArea = minImageArea;
    }
}
