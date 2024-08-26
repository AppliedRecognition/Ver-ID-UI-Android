package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.test.espresso.IdlingResource;

import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.SessionFunctions;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ver-ID session class
 * @since 2.0.0
 */
@Keep
public class VerIDSession implements IVerIDSession<VerIDSessionDelegate>, Application.ActivityLifecycleCallbacks {

    private static class StartRunnable implements Runnable {
        private final WeakReference<VerIDSession> session;

        StartRunnable(VerIDSession session) {
            this.session = new WeakReference<>(session);
        }

        @Override
        public void run() {
            VerIDSession session = this.session.get();
            if (session == null) {
                return;
            }
            try {
                if (session.isStarted.get()) {
                    throw new VerIDUISessionException(VerIDUISessionException.Code.SESSION_ALREADY_STARTED);
                }
                Context context = session.getVerID().getContext().orElseThrow(() -> new VerIDUISessionException(VerIDUISessionException.Code.CONTEXT_UNAVAILABLE));
                session.isStarted.set(true);
                session.registerActivityCallbacks();
                session.startSessionActivity(context);
            } catch (VerIDUISessionException e) {
                session.isStarted.set(false);
                session.onSessionFinished();
                long now = System.currentTimeMillis();
                session.getDelegate().ifPresent(listener -> listener.onSessionFinished(session, new VerIDSessionResult(new VerIDSessionException(e), now, now, null)));
            }
        }
    }

    private final VerID verID;
    private final VerIDSessionSettings settings;
    private final IStringTranslator stringTranslator;
    private final long sessionId;
    private WeakReference<VerIDSessionDelegate> delegateReference;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private ITextSpeaker textSpeaker;
    private final AtomicReference<VerIDSessionResult> sessionResult = new AtomicReference<>();
    private final SessionPrompts sessionPrompts;
    private ISessionVideoRecorder videoRecorder;
    private final AtomicInteger faceCaptureCount = new AtomicInteger(0);

    static final AtomicLong lastSessionId = new AtomicLong(0);

    /**
     * Session constructor
     * @param verID Instance of VerID to use for face detection, recognition and user management
     * @param settings Session settings
     * @since 2.0.0
     */
    @Keep
    public VerIDSession(@NonNull VerID verID, @NonNull VerIDSessionSettings settings) {
        this(verID, settings, new TranslatedStrings(verID.getContext().orElseThrow(RuntimeException::new), null));
    }

    /**
     * Session constructor
     * @param verID Instance of VerID to use for face detection, recognition and user management
     * @param settings Session settings
     * @param stringTranslator Translator for strings used in the session
     * @since 2.0.0
     */
    @Keep
    public VerIDSession(@NonNull VerID verID, @NonNull VerIDSessionSettings settings, @NonNull IStringTranslator stringTranslator) {
        this.verID = verID;
        this.settings = settings;
        this.stringTranslator = stringTranslator;
        this.sessionId = lastSessionId.getAndIncrement();
        this.sessionPrompts = new SessionPrompts(stringTranslator);
        verID.getContext().ifPresent(context -> this.textSpeaker = new TextSpeaker(context));
    }

    /**
     * Start the session
     * @since 2.0.0
     */
    @Keep
    public void start() {
        faceCaptureCount.set(0);
        new Handler(Looper.getMainLooper()).post(new StartRunnable(this));
    }

    /**
     * Session identifier – can be used to distinguish between different session instances
     * @return Identifier for the session
     * @since 2.0.0
     */
    @Keep
    public long getSessionIdentifier() {
        return sessionId;
    }

    /**
     * @return Instance of VerID being used by the session for face detection, recognition and user management
     * @since 2.0.0
     */
    @NonNull
    @Keep
    public VerID getVerID() {
        return verID;
    }

    /**
     * @return Session settings
     * @since 2.0.0
     */
    @NonNull
    @Keep
    public VerIDSessionSettings getSettings() {
        return settings;
    }

    /**
     * Get session delegate
     * @return Optional holding the session delegate or empty optional if delegate not set
     * @since 2.0.0
     */
    @NonNull
    @Keep
    public Optional<VerIDSessionDelegate> getDelegate() {
        return Optional.ofNullable(delegateReference != null ? delegateReference.get() : null);
    }

    /**
     * Set session delegate
     * @param delegate Session delegate
     * @since 2.0.0
     */
    @Keep
    public void setDelegate(@Nullable VerIDSessionDelegate delegate) {
        this.delegateReference = delegate != null ? new WeakReference<>(delegate) : null;
        if (delegate != null && delegate.shouldSessionRecordVideo(this)) {
            videoRecorder = new SessionVideoRecorder();
        }
    }

    @Keep
    protected Optional<ISessionVideoRecorder> getVideoRecorder() {
        return Optional.ofNullable(videoRecorder);
    }

    /**
     * Get session activity class
     * @return Class of activity that implements the {@link ISessionActivity} interface
     * @since 2.0.0
     */
    @NonNull
    private <A extends Activity & ISessionActivity> Class<A> getSessionActivityClass() {
        if (getDelegate().map(delegate -> delegate.getSessionActivityClass(this)).isPresent()) {
            return (Class<A>) getDelegate().map(delegate -> delegate.getSessionActivityClass(this)).get();
        } else {
            return (Class<A>) SessionActivity.class;
        }
    }

    /**
     * Get session result activity class
     * @param sessionResult Result of the session the activity will display
     * @return Class of activity that implements the {@link ISessionActivity} interface
     * @since 2.0.0
     */
    @NonNull
    private  <A extends Activity & ISessionActivity> Class<A> getSessionResultActivityClass(@NonNull VerIDSessionResult sessionResult) {
        if (getDelegate().isPresent()) {
            return (Class<A>) getDelegate().map(delegate -> delegate.getSessionResultActivityClass(this, sessionResult)).get();
        } else if (sessionResult.getError().isPresent()){
            return (Class<A>) SessionFailureActivity.class;
        } else {
            return (Class<A>) SessionSuccessActivity.class;
        }
    }

    private CameraLocation getCameraLens() {
        if (getDelegate().isPresent()) {
            return getDelegate().get().getSessionCameraLocation(this);
        }
        return CameraLocation.FRONT;
    }

    @UiThread
    private void startSessionActivity(@NonNull Context context) {
        Intent intent = new Intent(context, getSessionActivityClass());
        intent.putExtra(com.appliedrec.verid.ui2.SessionActivity.EXTRA_SESSION_ID, sessionId);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
    }

    @UiThread
    private void registerActivityCallbacks() {
        try {
            Context context = verID.getContext().orElseThrow(() -> new Exception("Context unavailable"));
            ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(this);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            sessionResult.set(new VerIDSessionResult(new VerIDSessionException(e), now, now, null));
            onSessionFinished();
        }
    }

    @UiThread
    private void unregisterActivityCallbacks() {
        verID.getContext().ifPresent(context -> ((Application)context.getApplicationContext()).unregisterActivityLifecycleCallbacks(this));
    }

    private void onSessionResult(@NonNull VerIDSessionResult result) {
        sessionResult.set(result);
    }

    private void onSessionFinished() {
        unregisterActivityCallbacks();
        getDelegate().ifPresent(verIDSessionDelegate -> {
            VerIDSessionResult result = sessionResult.getAndSet(null);
            if (result == null) {
                verIDSessionDelegate.onSessionCanceled(this);
            } else {
                verIDSessionDelegate.onSessionFinished(this, result);
            }
        });
        sessionResult.set(null);
        textSpeaker = null;
        videoRecorder = null;
    }

    private void onSessionCancelled() {
        sessionResult.set(null);
        onSessionFinished();
    }

    @NonNull
    private <A extends Activity & ISessionActivity> Optional<A> sessionActivity(Activity activity) {
        if ((activity instanceof ISessionActivity) && activity.getIntent() != null && activity.getIntent().getLongExtra(com.appliedrec.verid.ui2.SessionActivity.EXTRA_SESSION_ID, -1) == sessionId) {
            //noinspection unchecked
            return Optional.of((A)activity);
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    private void onFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        String labelText = sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null);
        getTextSpeaker().ifPresent(speaker -> speaker.speak(labelText, stringTranslator.getLocale(), false));
    }

    private Optional<ITextSpeaker> getTextSpeaker() {
        if (getDelegate().isPresent() && getDelegate().get().shouldSessionSpeakPrompts(this)) {
            return Optional.ofNullable(textSpeaker);
        }
        return Optional.empty();
    }

    //region Activity lifecycle callbacks

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        sessionActivity(activity).ifPresent(sessionActivity -> {
            SessionParameters sessionParameters = new SessionParameters(verID, settings, getCameraLens(), getDelegate().map(delegate -> delegate.createSessionViewFactory(this)).orElse(SessionView::new), stringTranslator, getDelegate().map(delegate -> delegate.createSessionFunctions(this, getVerID(), getSettings())).orElse(new SessionFunctions(getVerID(), getSettings())));
            getVideoRecorder().ifPresent(sessionParameters::setVideoRecorder);
            sessionParameters.setSessionResultObserver(this::onSessionResult);
            sessionParameters.setFaceDetectionResultObserver(this::onFaceDetectionResult);
            sessionParameters.setImageIteratorFactory(getDelegate().map(delegate -> delegate.createImageIteratorFactory(this)).orElse((VerIDImageIterator::new)));
            sessionParameters.setSessionFailureDialogFactory(getDelegate().map(delegate -> delegate.createSessionFailureDialogFactory(this)).orElse(new DefaultSessionFailureDialogFactory()));
            sessionParameters.setOnSessionFinishedRunnable(this::onSessionFinished);
            sessionParameters.setOnSessionCancelledRunnable(this::onSessionCancelled);
            sessionParameters.shouldRetryOnFailure(exception -> getDelegate().map(delegate -> delegate.shouldRetrySessionAfterFailure(this, exception)).orElse(false));
            sessionParameters.setResultIntentSupplier((result,context) -> {
                Intent intent = new Intent(context, getSessionResultActivityClass(result));
                intent.putExtra(com.appliedrec.verid.ui2.SessionActivity.EXTRA_SESSION_ID, sessionId);
                return intent;
            });
            sessionParameters.setTipsIntentSupplier(context -> {
                Intent intent = new Intent(context, TipsActivity.class);
                intent.putExtra(com.appliedrec.verid.ui2.SessionActivity.EXTRA_SESSION_ID, sessionId);
                return intent;
            });
            sessionParameters.setSessionResultDisplayIndicator(result -> {
                if (getDelegate().isPresent()) {
                    return getDelegate().get().shouldSessionDisplayResult(this, result);
                } else {
                    return false;
                }
            });
            sessionParameters.setSessionResult(sessionResult.get());
            getDelegate().map(VerIDSessionInViewDelegate::getCapturedImageMinimumArea).ifPresent(sessionParameters::setMinImageArea);
            getTextSpeaker().ifPresent(sessionParameters::setTextSpeaker);
            sessionActivity.setSessionParameters(sessionParameters);
        });
    }

    //region Unused

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    //endregion

    //endregion
}
