package com.appliedrec.verid.ui2;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.espresso.IdlingResource;

import com.appliedrec.verid.core2.AntiSpoofingException;
import com.appliedrec.verid.core2.FacePresenceException;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetection;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceDetectionResultEvaluation;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;

/**
 * Abstract Ver-ID session class
 * @param <Settings>
 * @param <T>
 * @param <U>
 * @since 2.0.0
 */
public abstract class AbstractVerIDSession<Settings extends VerIDSessionSettings, T extends AppCompatActivity & ISessionActivity, U extends AppCompatActivity & ISessionResultActivity> implements Application.ActivityLifecycleCallbacks, IdlingResource {

    private final VerID verID;
    private final Settings settings;
    private final IStringTranslator stringTranslator;
    private final long sessionId;
    private Disposable sessionDisposable;
    private WeakReference<VerIDSessionDelegate> delegateReference;
    private T sessionActivity;
    private AtomicBoolean isStarted = new AtomicBoolean(false);
    private AtomicBoolean isIdle = new AtomicBoolean(false);
    private ITextSpeaker textSpeaker;
    private AtomicReference<VerIDSessionResult> resultToShow = new AtomicReference<>();
    private AtomicInteger runCount = new AtomicInteger(0);
    private AtomicReference<Supplier<Function<VerIDImage, FaceDetectionResult>>> faceDetectionFunctionSupplier;
    private AtomicReference<Supplier<Function<FaceDetectionResult, FaceCapture>>> faceDetectionResultEvaluationFunctionSupplier;
    private ResourceCallback idlingResourceCallback;

    private static AtomicLong lastSessionId = new AtomicLong(0);

    /**
     * Session constructor
     * @param verID Instance of VerID to use for face detection, recognition and user management
     * @param settings Session settings
     * @since 2.0.0
     */
    public AbstractVerIDSession(@NonNull VerID verID, @NonNull Settings settings) {
        this(verID, settings, new TranslatedStrings(verID.getContext().get(), null));
    }

    /**
     * Session constructor
     * @param verID Instance of VerID to use for face detection, recognition and user management
     * @param settings Session settings
     * @param stringTranslator Translator for strings used in the session
     * @since 2.0.0
     */
    public AbstractVerIDSession(@NonNull VerID verID, @NonNull Settings settings, @NonNull IStringTranslator stringTranslator) {
        this.verID = verID;
        this.settings = settings;
        this.stringTranslator = stringTranslator;
        this.sessionId = lastSessionId.getAndIncrement();
        this.faceDetectionFunctionSupplier = new AtomicReference<>(() -> new FaceDetection(verID, settings));
        this.faceDetectionResultEvaluationFunctionSupplier = new AtomicReference<>(() -> new FaceDetectionResultEvaluation(verID, settings));
        verID.getContext().ifPresent(context -> this.textSpeaker = new TextSpeaker(context));
    }

    /**
     * Start the session
     * @since 2.0.0
     */
    public void start() {
        isIdle.set(false);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (isStarted.get()) {
                    throw new VerIDUISessionException(VerIDUISessionException.Code.SESSION_ALREADY_STARTED);
                }
                if (!getVerID().getContext().isPresent()) {
                    throw new VerIDUISessionException(VerIDUISessionException.Code.CONTEXT_UNAVAILABLE);
                }
                isStarted.set(true);
                registerActivityCallbacks();
                startSessionActivity(getVerID().getContext().get());
            } catch (VerIDUISessionException e) {
                isStarted.set(false);
                onSessionFinished();
                long now = System.currentTimeMillis();
                getDelegate().ifPresent(listener -> listener.sessionDidFinishWithResult(this, new VerIDSessionResult(new VerIDSessionException(e), now, now, 0)));
            }
        });
    }

    /**
     * Session identifier – can be used to distinguish between different session instances
     * @return Identifier for the session
     * @since 2.0.0
     */
    public long getSessionIdentifier() {
        return sessionId;
    }

    /**
     * @return Instance of VerID being used by the session for face detection, recognition and user management
     * @since 2.0.0
     */
    @NonNull
    public VerID getVerID() {
        return verID;
    }

    /**
     * @return Session settings
     * @since 2.0.0
     */
    @NonNull
    public Settings getSettings() {
        return settings;
    }

    /**
     * Get session delegate
     * @return Optional holding the session delegate or empty optional if delegate not set
     * @since 2.0.0
     */
    @NonNull
    public Optional<VerIDSessionDelegate> getDelegate() {
        return Optional.ofNullable(delegateReference != null ? delegateReference.get() : null);
    }

    /**
     * Set session delegate
     * @param delegate Session delegate
     * @since 2.0.0
     */
    public void setDelegate(@Nullable VerIDSessionDelegate delegate) {
        this.delegateReference = delegate != null ? new WeakReference<>(delegate) : null;
    }

    /**
     * Get session activity class
     * @return Class of activity that implements the {@link ISessionActivity} interface
     * @since 2.0.0
     */
    @NonNull
    protected abstract Class<? extends T> getSessionActivityClass();

    /**
     * Get session result activity class
     * @param sessionResult Result of the session the activity will display
     * @return Class of activity that implements the {@link ISessionResultActivity} interface
     * @since 2.0.0
     */
    @NonNull
    protected abstract Class<? extends U> getSessionResultActivityClass(@NonNull VerIDSessionResult sessionResult);

    /**
     * @return Face detection function supplier
     * @since 2.0.0
     */
    public Supplier<Function<VerIDImage, FaceDetectionResult>> getFaceDetectionFunctionSupplier() {
        return faceDetectionFunctionSupplier.get();
    }

    /**
     * Set instance that supplies the face detection function
     * @param faceDetectionFunctionSupplier Face detection function supplier
     * @since 2.0.0
     */
    public void setFaceDetectionFunctionSupplier(@NonNull Supplier<Function<VerIDImage, FaceDetectionResult>> faceDetectionFunctionSupplier) {
        this.faceDetectionFunctionSupplier.set(faceDetectionFunctionSupplier);
    }

    /**
     * Set instance that supplies the face result evaluation function
     * @param faceDetectionResultEvaluationFunction Face result evaluation function supplier
     * @since 2.0.0
     */
    public void setFaceDetectionResultEvaluationFunctionSupplier(@NonNull Supplier<Function<FaceDetectionResult, FaceCapture>> faceDetectionResultEvaluationFunction) {
        this.faceDetectionResultEvaluationFunctionSupplier.set(faceDetectionResultEvaluationFunction);
    }

    /**
     * @return Face detection function supplier
     * @since 2.0.0
     */
    @NonNull
    public Supplier<Function<FaceDetectionResult, FaceCapture>> getFaceDetectionResultEvaluationFunctionSupplier() {
        return faceDetectionResultEvaluationFunctionSupplier.get();
    }

    /**
     * @return Number of times the session was run
     * @since 2.0.0
     */
    protected int getRunCount() {
        return runCount.get();
    }

    private Session<Settings> createCoreSession(T sessionActivity) {
        return new Session.Builder<>(verID, settings, sessionActivity.getImageFlowable())
                .setFaceDetectionFunction(getFaceDetectionFunctionSupplier().get())
                .setFaceDetectionResultEvaluationFunction(getFaceDetectionResultEvaluationFunctionSupplier().get())
                .setFaceDetectionCallback(getFaceDetectionCallback())
                .setFaceCaptureCallback(sessionActivity)
                .bindToLifecycle(sessionActivity.getLifecycle())
                .setSuccessCallback(this::finishWithResult)
                .setErrorCallback(this::finishWithError)
                .build();
    }

    private void startSessionWithActivity(T sessionActivity) {
        this.sessionActivity = sessionActivity;
        sessionActivity.setSessionSettings(settings, getCameraLens());
        createCoreSession(sessionActivity).start();
    }

    private CameraLens getCameraLens() {
        if (getDelegate().isPresent()) {
            return getDelegate().get().getCameraLensForSession(this);
        }
        return CameraLens.FACING_FRONT;
    }

    @UiThread
    private void startSessionActivity(@NonNull Context context) {
        if (sessionActivity == null) {
            Intent intent = new Intent(context, getSessionActivityClass());
            intent.putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } else {
            startSessionWithActivity(sessionActivity);
        }
    }

    @UiThread
    private void registerActivityCallbacks() {
        verID.getContext().ifPresent(context -> ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(this));
    }

    @UiThread
    private void unregisterActivityCallbacks() {
        verID.getContext().ifPresent(context -> ((Application)context.getApplicationContext()).unregisterActivityLifecycleCallbacks(this));
    }

    @UiThread
    private void finishWithResult(@NonNull VerIDSessionResult result) {
        int runCount = this.runCount.incrementAndGet();
        onSessionFinished();
        if (sessionActivity != null) {
            sessionActivity.setFaceDetectionResult(null);
        }
        if (sessionActivity != null && ((getDelegate().isPresent() && getDelegate().get().shouldSessionShowResult(this, result)) || runCount <= settings.getMaxRetryCount() && result.getError().isPresent() && result.getError().get().getCode() == VerIDSessionException.Code.LIVENESS_FAILURE && result.getError().get().getCause() != null && (result.getError().get().getCause() instanceof AntiSpoofingException || result.getError().get().getCause() instanceof FacePresenceException))) {
            resultToShow.set(result);
            Intent intent = new Intent(sessionActivity, getSessionResultActivityClass(result));
            intent.putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId);
            sessionActivity.startActivity(intent);
        } else {
            if (isStarted.getAndSet(false)) {
                getDelegate().ifPresent(listener -> listener.sessionDidFinishWithResult(this, result));
            }
            if (sessionDisposable != null && !sessionDisposable.isDisposed()) {
                sessionDisposable.dispose();
            }
            sessionDisposable = null;
            unregisterActivityCallbacks();
        }
        finishSessionActivity();
    }

    @UiThread
    private void finishWithError(@NonNull Throwable throwable) {
        finishWithResult(new VerIDSessionResult(new VerIDSessionException(throwable), System.currentTimeMillis(), System.currentTimeMillis(), 0));
    }

    private void finishSessionActivity() {
        if (sessionActivity != null && !sessionActivity.isFinishing()) {
            sessionActivity.finish();
        }
        sessionActivity = null;
    }

    @NonNull
    private Optional<T> sessionActivity(Activity activity) {
        if (getSessionActivityClass().isInstance(activity) && activity.getIntent() != null && activity.getIntent().getLongExtra(SessionActivity.EXTRA_SESSION_ID, -1) == sessionId) {
            return Optional.of((T)activity);
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    private Optional<ISessionResultActivity> sessionResultActivity(Activity activity) {
        if (resultToShow.get() != null && getSessionResultActivityClass(resultToShow.get()).isInstance(activity) && activity.getIntent() != null && activity.getIntent().getLongExtra(SessionActivity.EXTRA_SESSION_ID, -1) == sessionId) {
            return Optional.of((ISessionResultActivity)activity);
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    private Optional<TipsActivity> tipsActivity(Activity activity) {
        if (activity instanceof TipsActivity && activity.getIntent() != null && activity.getIntent().getLongExtra(SessionActivity.EXTRA_SESSION_ID, -1) == sessionId) {
            TipsActivity tipsActivity = (TipsActivity)activity;
            return Optional.of(tipsActivity);
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    private Consumer<? super FaceDetectionResult> getFaceDetectionCallback() {
        return faceDetectionResult -> {
            if (sessionActivity != null) {
                sessionActivity.setFaceDetectionResult(faceDetectionResult);
                String labelText;
                switch (faceDetectionResult.getStatus()) {
                    case FACE_FIXED:
                    case FACE_ALIGNED:
                        labelText = stringTranslator.getTranslatedString("Great, hold it");
                        break;
                    case FACE_MISALIGNED:
                        labelText = stringTranslator.getTranslatedString("Slowly turn to follow the arrow");
                        break;
                    case FACE_TURNED_TOO_FAR:
                        labelText = null;
                        break;
                    default:
                        labelText = stringTranslator.getTranslatedString("Align your face with the oval");
                }
                sessionActivity.setLabelText(labelText);
                getTextSpeaker().ifPresent(speaker -> speaker.speak(labelText, stringTranslator.getLocale(), false));
            }
        };
    }

    private Optional<ITextSpeaker> getTextSpeaker() {
        if (getDelegate().isPresent() && getDelegate().get().shouldSpeakPromptsInSession(this)) {
            return Optional.ofNullable(textSpeaker);
        }
        return Optional.empty();
    }

    private void onSessionFinished() {
        if (!isIdle.getAndSet(true) && idlingResourceCallback != null) {
            idlingResourceCallback.onTransitionToIdle();
        }
    }

    //region Activity lifecycle callbacks

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        sessionActivity(activity).ifPresent(this::startSessionWithActivity);
        sessionResultActivity(activity).ifPresent(sessionResultActivity -> {
            sessionResultActivity.setSessionResult(resultToShow.get());
            sessionResultActivity.setTranslator(stringTranslator);
        });
        tipsActivity(activity).ifPresent(tipsActivity -> {
            tipsActivity.setStringTranslator(stringTranslator);
            getTextSpeaker().ifPresent(tipsActivity::setTextSpeaker);
        });
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (sessionActivity(activity).isPresent()) {
            if (sessionDisposable != null && !sessionDisposable.isDisposed()) {
                sessionDisposable.dispose();
            }
            sessionDisposable = null;
            sessionActivity = null;
            if (activity.isFinishing() && resultToShow.get() == null) {
                if (isStarted.getAndSet(false)) {
                    onSessionFinished();
                    getDelegate().ifPresent(listener -> listener.sessionWasCanceled(this));
                }
                unregisterActivityCallbacks();
            }
        }
        if (sessionResultActivity(activity).isPresent()) {
            if (activity.isFinishing()) {
                if (sessionResultActivity(activity).get().didTapRetryButtonInSessionResultActivity()) {
                    startSessionActivity(activity);
                    return;
                }
                VerIDSessionResult result = resultToShow.getAndSet(null);
                if (result != null) {
                    isStarted.set(false);
                    getDelegate().ifPresent(listener -> listener.sessionDidFinishWithResult(this, result));
                }
                unregisterActivityCallbacks();
            }
        }
    }

    //endregion

    //region Idling resource

    @Override
    public String getName() {
        return "Ver-ID session";
    }

    @Override
    public boolean isIdleNow() {
        return isIdle.get();
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        idlingResourceCallback = callback;
    }

    //endregion
}
