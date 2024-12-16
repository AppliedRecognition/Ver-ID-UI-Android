package com.appliedrec.verid.ui2;

import android.content.Context;
import android.view.Surface;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.util.Consumer;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.CoreSession;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceExtents;
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

/**
 * Session that uses a session view to render the camera preview and detected face
 * @since 2.0.0
 */
@Keep
public class VerIDSessionInView<T extends View & ISessionView> implements IVerIDSession<VerIDSessionInViewDelegate>, ISessionView.SessionViewListener, Iterable<FaceBounds>, CameraWrapper.Listener {

    @Override
    public void onCameraStarted() {

    }

    @Override
    public void onCameraStopped() {

    }

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
        public void accept(FaceDetectionResult faceDetectionResult) {
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
        public void accept(FaceCapture faceCapture) {
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
    private CoreSession coreSession;
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);
    private final WeakReference<VerID> verIDRef;
    private final VerIDSessionSettings sessionSettings;
    private final FaceExtents originalFaceExtents;
    private final IStringTranslator stringTranslator;
    private final long sessionId;
    private WeakReference<VerIDSessionInViewDelegate> delegateRef;
    private WeakReference<LifecycleOwner> lifecycleOwnerRef;
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
    public VerIDSessionInView(@NonNull VerID verID, @NonNull VerIDSessionSettings sessionSettings, @NonNull T sessionView, @NonNull IStringTranslator stringTranslator, LifecycleOwner lifecycleOwner) {
        this.verIDRef = new WeakReference<>(verID);
        this.sessionView = sessionView;
        this.sessionView.setSessionSettings(sessionSettings);
        this.sessionView.setVisibility(View.GONE);
        this.sessionSettings = sessionSettings;
        this.stringTranslator = stringTranslator;
        originalFaceExtents = sessionView.getDefaultFaceExtents();
        sessionId = VerIDSession.lastSessionId.getAndIncrement();
        this.lifecycleOwnerRef = new WeakReference<>(lifecycleOwner);
    }

    /**
     * Constructor
     * @param verID Instance of VerID
     * @param sessionSettings Session settings
     * @param sessionView Session view
     * @since 2.0.0
     */
    @Keep
    public VerIDSessionInView(VerID verID, VerIDSessionSettings sessionSettings, T sessionView, LifecycleOwner lifecycleOwner) {
        this(verID, sessionSettings, sessionView, new TranslatedStrings(sessionView.getContext(), null), lifecycleOwner);
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
            LifecycleOwner lifecycleOwner = lifecycleOwnerRef.get();
            if (verID == null || lifecycleOwner == null) {
                Log.e("VerID instance or lifecycleOwner is null");
                onSessionResult(new VerIDSessionResult(new VerIDSessionException(new Exception("Ver-ID is null")), System.currentTimeMillis(), System.currentTimeMillis(), null));
                return;
            }
            faceCaptureCount.set(0);
            coreSession = new CoreSession(verID, sessionSettings, sessionView.getFaceBounds(), lifecycleOwner);
            coreSession.setFaceDetectionCallback(new FaceDetectionCallback(this));
            coreSession.setFaceCaptureCallback(new FaceCaptureCallback(this));
            coreSession.setOnFinish(this::onSessionResult);
//            session = new Session.Builder<>(verID, sessionSettings, cameraWrapper.getImageIterator(), this)
//                    .setFaceDetectionCallback(new FaceDetectionCallback(this))
//                    .setFaceCaptureCallback(new FaceCaptureCallback(this))
//                    .setFinishCallback(this::onSessionResult)
//                    .build();
            cameraWrapper = new CameraWrapper(
                    sessionView.getContext(),
                    getDelegate().map(
                            delegate -> delegate.getSessionCameraLocation(this)
                    ).orElse(CameraLocation.FRONT),
                    coreSession,
                    coreSession.getExifOrientation(),
                    coreSession.isMirrored(),
                    getDelegate().map(
                            delegate -> delegate.shouldSessionRecordVideo(this)
                    ).orElse(false) ? new SessionVideoRecorder() : null
            );
            cameraWrapper.setPreviewClass(sessionView.getPreviewClass());
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
            if (coreSession != null) {
                coreSession.cancel();
                coreSession = null;
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
//        session.start();
        coreSession.start();
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
