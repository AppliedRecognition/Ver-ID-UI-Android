package com.appliedrec.verid.ui2;

import android.content.Context;
import android.view.Surface;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionStatus;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;
import com.appliedrec.verid.core2.util.Log;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.functions.Consumer;

/**
 * Session that uses a session view to render the camera preview and detected face
 * @since 2.0.0
 */
@Keep
public class VerIDSessionInView<T extends View & ISessionView> implements IVerIDSession<VerIDSessionInViewDelegate>, ISessionView.SessionViewListener, Iterable<FaceBounds>, CameraWrapper.Listener {

    private static class FaceDetectionCallback implements Consumer<FaceDetectionResult> {

        private final WeakReference<VerIDSessionInView<?>> sessionRef;
        private final SessionPrompts sessionPrompts;
        private final WeakReference<TextSpeaker> textSpeakerRef;

        FaceDetectionCallback(VerIDSessionInView<?> session) {
            this.sessionRef = new WeakReference<>(session);
            this.sessionPrompts = new SessionPrompts(session.stringTranslator);
            Context context = session.getSessionView().getContext();
            if (session.getDelegate().map(delegate -> delegate.shouldSessionSpeakPrompts(session)).orElse(false)) {
                textSpeakerRef = new WeakReference<>(new TextSpeaker(context));
            } else {
                textSpeakerRef = new WeakReference<>(null);
            }
        }

        @Override
        public void accept(FaceDetectionResult faceDetectionResult) throws Throwable {
            VerIDSessionInView<?> session = sessionRef.get();
            if (session == null || sessionPrompts == null) {
                return;
            }
            TextSpeaker textSpeaker = textSpeakerRef.get();
            String prompt = sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null);
            session.sessionView.setFaceDetectionResult(faceDetectionResult, prompt);
            if (textSpeaker != null && prompt != null) {
                textSpeaker.speak(prompt, session.stringTranslator.getLocale(), false);
            }
            session.faceDetectionLiveData.postValue(faceDetectionResult);
        }
    }

    private static class FaceCaptureCallback implements Consumer<FaceCapture> {

        private final WeakReference<VerIDSessionInView<?>> sessionRef;

        FaceCaptureCallback(VerIDSessionInView<?> session) {
            this.sessionRef = new WeakReference<>(session);
        }

        @Override
        public void accept(FaceCapture faceCapture) throws Throwable {
            VerIDSessionInView<?> session = sessionRef.get();
            if (session == null) {
                return;
            }
            session.faceCaptureLiveData.postValue(faceCapture);
        }
    }

    private T sessionView;
    private CameraWrapper cameraWrapper;
    private Session<?> session;
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);
    private final WeakReference<VerID> verIDRef;
    private final VerIDSessionSettings sessionSettings;
    private final FaceExtents originalFaceExtents;
    private final IStringTranslator stringTranslator;
    private final long sessionId;
    private WeakReference<VerIDSessionInViewDelegate> delegateRef;
    private MutableLiveData<FaceDetectionResult> faceDetectionLiveData = new MutableLiveData<>();
    private MutableLiveData<FaceCapture> faceCaptureLiveData = new MutableLiveData<>();
    private MutableLiveData<VerIDSessionResult> sessionResultLiveData = new MutableLiveData<>();
    private final AtomicInteger faceCaptureCount = new AtomicInteger(0);

    /**
     * Constructor
     * @param verID Instance of VerID
     * @param sessionSettings Session settings
     * @param sessionView Session view
     * @param stringTranslator String translator
     * @since 2.0.0
     */
    @Keep
    public VerIDSessionInView(@NonNull VerID verID, @NonNull VerIDSessionSettings sessionSettings, @NonNull T sessionView, @NonNull IStringTranslator stringTranslator) {
        this.verIDRef = new WeakReference<>(verID);
        this.sessionView = sessionView;
        this.sessionView.setSessionSettings(sessionSettings);
        this.sessionView.setVisibility(View.GONE);
        this.sessionSettings = sessionSettings;
        this.stringTranslator = stringTranslator;
        originalFaceExtents = sessionView.getDefaultFaceExtents();
        sessionId = VerIDSession.lastSessionId.getAndIncrement();
    }

    /**
     * Constructor
     * @param verID Instance of VerID
     * @param sessionSettings Session settings
     * @param sessionView Session view
     * @since 2.0.0
     */
    @Keep
    public VerIDSessionInView(VerID verID, VerIDSessionSettings sessionSettings, T sessionView) {
        this(verID, sessionSettings, sessionView, new TranslatedStrings(sessionView.getContext(), null));
    }

    /**
     * @return Unique identifier for the session
     * @since 2.0.0
     */
    @Keep
    public long getSessionIdentifier() {
        return sessionId;
    }

    /**
     * Start the session
     * @since 2.0.0
     */
    @Keep
    public void start() {
        Log.v("Start session");
        if (isSessionRunning.compareAndSet(false, true)) {
            Log.d("Starting session");
            VerID verID = verIDRef.get();
            if (verID == null) {
                Log.e("VerID instance is null");
                onSessionResult(new VerIDSessionResult(new VerIDSessionException(new Exception("Ver-ID is null")), System.currentTimeMillis(), System.currentTimeMillis(), null));
                return;
            }
            faceCaptureCount.set(0);
            IImageIterator imageIterator = getDelegate().map(delegate -> delegate.createImageIteratorFactory(this).apply(getSessionView().getContext())).orElse(new VerIDImageIterator(getSessionView().getContext()));
            cameraWrapper = new CameraWrapper(sessionView.getContext(), getDelegate().map(delegate -> delegate.getSessionCameraLocation(this)).orElse(CameraLocation.FRONT), imageIterator, getDelegate().map(delegate -> delegate.shouldSessionRecordVideo(this)).orElse(false) ? new SessionVideoRecorder() : null);
            cameraWrapper.setPreviewClass(sessionView.getPreviewClass());
            session = new Session.Builder<>(verID, sessionSettings, cameraWrapper.getImageIterator(), this)
                    .setFaceDetectionCallback(new FaceDetectionCallback(this))
                    .setFaceCaptureCallback(new FaceCaptureCallback(this))
                    .setFinishCallback(this::onSessionResult)
                    .build();
            sessionView.setVisibility(View.VISIBLE);
            sessionView.setDefaultFaceExtents(sessionSettings.getExpectedFaceExtents());
            sessionView.setCameraPreviewMirrored(getDelegate().map(delegate -> delegate.getSessionCameraLocation(this) == CameraLocation.FRONT).orElse(true));
            sessionView.onSessionStarted();
            sessionView.addListener(this);
            Log.d("Session started");
        }
    }

    private void onSessionResult(VerIDSessionResult result) {
        sessionResultLiveData.postValue(result);
        sessionView.setVisibility(View.GONE);
        stop();
        getDelegate().ifPresent(delegate -> delegate.onSessionFinished(this, result));
    }

    /**
     * Stop the session
     * @since 2.0.0
     */
    @Keep
    public void stop() {
        Log.v("Stop session");
        if (isSessionRunning.compareAndSet(true, false)) {
            Log.d("Stopping session");
            if (session != null) {
                session.cancel();
                session = null;
            }
            if (cameraWrapper != null) {
                cameraWrapper.removeListener(this);
                cameraWrapper.stop();
                cameraWrapper = null;
            }
            sessionView.setDefaultFaceExtents(originalFaceExtents);
            sessionView.removeListener(this);
            sessionView = null;
            sessionResultLiveData = null;
            faceDetectionLiveData = null;
            faceCaptureLiveData = null;
            Log.d("Session stopped");
        }
    }

    /**
     * @return Session delegate
     * @since 2.0.0
     */
    public Optional<VerIDSessionInViewDelegate> getDelegate() {
        if (delegateRef != null) {
            return Optional.ofNullable(delegateRef.get());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Set session delegate
     * @param delegate Session delegate
     * @since 2.0.0
     */
    public void setDelegate(VerIDSessionInViewDelegate delegate) {
        this.delegateRef = new WeakReference<>(delegate);
    }

    /**
     * @return Instance of {@link VerID} used for face detection and recognition
     * @since 2.0.0
     */
    @Keep
    public VerID getVerID() {
        return verIDRef.get();
    }

    /**
     * @return Session settings
     * @since 2.0.0
     */
    @Keep
    public VerIDSessionSettings getSettings() {
        return sessionSettings;
    }

    /**
     * @return Session view
     * @since 2.0.0
     */
    @Keep
    public T getSessionView() {
        return sessionView;
    }

    /**
     * @return Camera wrapper
     * @since 2.0.0
     */
    @Keep
    public CameraWrapper getCameraWrapper() {
        return cameraWrapper;
    }

    /**
     * @return Session result live data
     * @since 2.0.0
     */
    @Keep
    public LiveData<VerIDSessionResult> getSessionResultLiveData() {
        return sessionResultLiveData;
    }

    /**
     * @return Face detection live data
     * @since 2.0.0
     */
    @Keep
    public LiveData<FaceDetectionResult> getFaceDetectionLiveData() {
        return faceDetectionLiveData;
    }

    /**
     * @return Face capture live data
     * @since 2.0.0
     */
    @Keep
    public LiveData<FaceCapture> getFaceCaptureLiveData() {
        return faceCaptureLiveData;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onPreviewSurfaceCreated(Surface surface) {
        getDelegate().map(VerIDSessionInViewDelegate::getCapturedImageMinimumArea).ifPresent(size -> cameraWrapper.setCapturedImageMinimumArea(size));
        cameraWrapper.addListener(this);
        cameraWrapper.setPreviewSurface(surface);
        cameraWrapper.start(sessionView.getWidth(), sessionView.getHeight(), sessionView.getDisplayRotation());
        session.start();
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onPreviewSurfaceDestroyed() {
        stop();
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    @Override
    public Iterator<FaceBounds> iterator() {
        return sessionView;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onCameraPreviewSize(int width, int height, int sensorOrientation) {
        sessionView.setPreviewSize(width, height, sensorOrientation);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onCameraError(VerIDSessionException error) {
        stop();
        long now = System.currentTimeMillis();
        sessionResultLiveData.postValue(new VerIDSessionResult(error, now, now, null));
    }
}
