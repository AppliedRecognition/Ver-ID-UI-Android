package com.appliedrec.verid.ui2;

import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

@Keep
public class VerIDSessionInView implements ISessionView.SessionViewListener, Iterable<FaceBounds>, CameraWrapper.Listener {

    private final SessionView sessionView;
    private final CameraWrapper cameraWrapper;
    private final Session<?> session;
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);
    private final VerIDSessionSettings sessionSettings;
    private final FaceExtents originalFaceExtents;

    @Keep
    public VerIDSessionInView(VerID verID, VerIDSessionSettings sessionSettings, SessionView sessionView, CameraLocation cameraLocation, IStringTranslator stringTranslator) {
        this.sessionView = sessionView;
        this.sessionSettings = sessionSettings;
        IImageIterator imageIterator = new VerIDImageIterator(verID);
        cameraWrapper = new CameraWrapper(sessionView.getContext(), cameraLocation, imageIterator, null);
        cameraWrapper.setPreviewClass(sessionView.getPreviewClass());
        SessionPrompts sessionPrompts = new SessionPrompts(stringTranslator);
        session = new Session.Builder<>(verID, sessionSettings, cameraWrapper.getImageIterator(), this)
                .setFaceDetectionCallback(faceDetectionResult -> sessionView.setFaceDetectionResult(faceDetectionResult, sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null)))
                .setFinishCallback(this::onSessionResult)
                .build();
        originalFaceExtents = sessionView.getDefaultFaceExtents();
    }

    @Keep
    public VerIDSessionInView(VerID verID, VerIDSessionSettings sessionSettings, SessionView sessionView) {
        this(verID, sessionSettings, sessionView, CameraLocation.FRONT, new TranslatedStrings(sessionView.getContext(), null));
    }

    @Keep
    public VerIDSessionInView(VerID verID, VerIDSessionSettings sessionSettings, SessionView sessionView, CameraLocation cameraLocation) {
        this(verID, sessionSettings, sessionView, cameraLocation, new TranslatedStrings(sessionView.getContext(), null));
    }

    @Keep
    public VerIDSessionInView(VerID verID, VerIDSessionSettings sessionSettings, SessionView sessionView, IStringTranslator stringTranslator) {
        this(verID, sessionSettings, sessionView, CameraLocation.FRONT, stringTranslator);
    }

    @Keep
    public void start() {
        if (isSessionRunning.compareAndSet(false, true)) {
            sessionView.setDefaultFaceExtents(sessionSettings.getExpectedFaceExtents());
            sessionView.addListener(this);
        }
    }

    private void onSessionResult(VerIDSessionResult result) {
        stop();
    }

    @Keep
    public void stop() {
        if (isSessionRunning.compareAndSet(true, false)) {
            session.cancel();
            cameraWrapper.removeListener(this);
            cameraWrapper.stop();
            sessionView.setDefaultFaceExtents(originalFaceExtents);
            sessionView.removeListener(this);
        }
    }

    @Override
    public void onPreviewSurfaceCreated(Surface surface) {
        cameraWrapper.addListener(this);
        cameraWrapper.setPreviewSurface(surface);
        cameraWrapper.start(sessionView.getWidth(), sessionView.getHeight(), sessionView.getDisplayRotation());
        session.start();
    }

    @Override
    public void onPreviewSurfaceDestroyed() {
        stop();
    }

    @NonNull
    @Override
    public Iterator<FaceBounds> iterator() {
        return sessionView;
    }

    @Keep
    public SessionView getSessionView() {
        return sessionView;
    }

    @Keep
    public CameraWrapper getCameraWrapper() {
        return cameraWrapper;
    }

    @Keep
    public LiveData<VerIDSessionResult> getSessionResultLiveData() {
        return session.getSessionResultLiveData();
    }

    @Keep
    public LiveData<FaceDetectionResult> getFaceDetectionLiveData() {
        return session.getFaceDetectionLiveData();
    }

    @Keep
    public LiveData<FaceCapture> getFaceCaptureLiveData() {
        return session.getFaceCaptureLiveData();
    }

    @Override
    public void onCameraPreviewSize(int width, int height, int sensorOrientation) {
        sessionView.setPreviewSize(width, height, sensorOrientation);
    }

    @Override
    public void onCameraError(VerIDSessionException error) {
        stop();
        long now = System.currentTimeMillis();
        ((MutableLiveData<VerIDSessionResult>)session.getSessionResultLiveData()).postValue(new VerIDSessionResult(error, now, now, null));
    }
}
