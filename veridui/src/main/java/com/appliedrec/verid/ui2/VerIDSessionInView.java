package com.appliedrec.verid.ui2;

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
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session that uses a session view to render the camera preview and detected face
 * @since 2.0.0
 */
@Keep
public class VerIDSessionInView<T extends View & ISessionView> implements IVerIDSession<VerIDSessionInViewDelegate>, ISessionView.SessionViewListener, Iterable<FaceBounds>, CameraWrapper.Listener {

    private final T sessionView;
    private CameraWrapper cameraWrapper;
    private Session<?> session;
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);
    private final VerID verID;
    private final VerIDSessionSettings sessionSettings;
    private final FaceExtents originalFaceExtents;
    private final IStringTranslator stringTranslator;
    private final long sessionId;
    private WeakReference<VerIDSessionInViewDelegate> delegateRef;
    private final MutableLiveData<FaceDetectionResult> faceDetectionLiveData = new MutableLiveData<>();
    private final MutableLiveData<FaceCapture> faceCaptureLiveData = new MutableLiveData<>();
    private final MutableLiveData<VerIDSessionResult> sessionResultLiveData = new MutableLiveData<>();

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
        this.verID = verID;
        this.sessionView = sessionView;
        this.sessionView.setSessionSettings(sessionSettings);
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
        if (isSessionRunning.compareAndSet(false, true)) {
            IImageIterator imageIterator = getDelegate().map(delegate -> delegate.createImageIteratorFactory(this).apply(getSessionView().getContext())).orElse(new VerIDImageIterator(getSessionView().getContext()));
            cameraWrapper = new CameraWrapper(sessionView.getContext(), getDelegate().map(delegate -> delegate.getSessionCameraLocation(this)).orElse(CameraLocation.FRONT), imageIterator, getDelegate().map(delegate -> delegate.shouldSessionRecordVideo(this)).orElse(false) ? new SessionVideoRecorder() : null);
            cameraWrapper.setPreviewClass(sessionView.getPreviewClass());
            SessionPrompts sessionPrompts = new SessionPrompts(stringTranslator);
            TextSpeaker textSpeaker;
            if (getDelegate().map(delegate -> delegate.shouldSessionSpeakPrompts(this)).orElse(false) && verID.getContext().isPresent()) {
                textSpeaker = new TextSpeaker(verID.getContext().get());
            } else {
                textSpeaker = null;
            }
            session = new Session.Builder<>(verID, sessionSettings, cameraWrapper.getImageIterator(), this)
                    .setFaceDetectionCallback(faceDetectionResult -> {
                        String prompt = sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null);
                        sessionView.setFaceDetectionResult(faceDetectionResult, prompt);
                        if (textSpeaker != null && prompt != null) {
                            textSpeaker.speak(prompt, stringTranslator.getLocale(), false);
                        }
                        faceDetectionLiveData.postValue(faceDetectionResult);
                    })
                    .setFaceCaptureCallback(faceCaptureLiveData::postValue)
                    .setFinishCallback(this::onSessionResult)
                    .build();
            sessionView.setDefaultFaceExtents(sessionSettings.getExpectedFaceExtents());
            sessionView.setCameraPreviewMirrored(getDelegate().map(delegate -> delegate.getSessionCameraLocation(this) == CameraLocation.FRONT).orElse(true));
            sessionView.addListener(this);
        }
    }

    private void onSessionResult(VerIDSessionResult result) {
        stop();
        sessionResultLiveData.postValue(result);
        getDelegate().ifPresent(delegate -> delegate.onSessionFinished(this, result));
    }

    /**
     * Stop the session
     * @since 2.0.0
     */
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
        return verID;
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
