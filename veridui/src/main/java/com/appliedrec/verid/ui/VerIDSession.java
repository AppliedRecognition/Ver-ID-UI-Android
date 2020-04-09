package com.appliedrec.verid.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appliedrec.verid.core.AntiSpoofingException;
import com.appliedrec.verid.core.Bearing;
import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceCapture;
import com.appliedrec.verid.core.FaceDetectionServiceFactory;
import com.appliedrec.verid.core.FacePresenceException;
import com.appliedrec.verid.core.IFaceCaptureListener;
import com.appliedrec.verid.core.IFaceDetectionFactory;
import com.appliedrec.verid.core.IFaceDetectionListener;
import com.appliedrec.verid.core.IFaceDetectionServiceFactory;
import com.appliedrec.verid.core.IFaceRecognitionFactory;
import com.appliedrec.verid.core.IImageProviderService;
import com.appliedrec.verid.core.IResultEvaluationServiceFactory;
import com.appliedrec.verid.core.IUserManagementFactory;
import com.appliedrec.verid.core.ResultEvaluationServiceFactory;
import com.appliedrec.verid.core.SessionPublisher;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDSessionSettings;
import com.appliedrec.verid.identity.VerIDIdentity;

import java.lang.ref.WeakReference;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class VerIDSession<U extends Face, T extends VerIDSessionSettings<U>> implements Application.ActivityLifecycleCallbacks, IOnDoneListener {

    public static class Configuration<U extends Face, T extends VerIDSessionSettings<U>> {
        private VerIDIdentity identity;
        private IFaceDetectionFactory faceDetectionFactory;
        private IFaceRecognitionFactory faceRecognitionFactory;
        private IUserManagementFactory userManagementFactory;
        private TranslatedStrings translatedStrings;
        private final T settings;
        private final Context context;

        private Configuration(Context context, T settings) {
            this.context = context;
            this.settings = settings;
        }
    }

    private final WeakReference<Context> contextRef;
    private final T settings;
    private final VerID verID;
    private Disposable sessionDisposable;
    private Disposable disposable;
    private ObservableEmitter<FaceCapture<U>> emitter;
    private final long sessionId;
    private WeakReference<VerIDSessionDelegate<U,T>> delegateRef;
    private Bitmap faceImage;
    private TranslatedStrings translatedStrings;
    private boolean isActivityStarted = false;
    public static final ISessionActivityClassFactory DEFAULT_ACTIVITY_CLASS_FACTORY = new ISessionActivityClassFactory() {
        @Override
        public Class<? extends Activity> getFaceRecognitionActivityClass() {
            return VerIDActivity.class;
        }

        @Override
        public Class<? extends Activity> getResultActivityClass(@Nullable Throwable error) {
            if (error instanceof AntiSpoofingException || error instanceof FacePresenceException) {
                return AntispoofingFailureActivity.class;
            } else if (error != null) {
                return FailureActivity.class;
            } else {
                return SuccessActivity.class;
            }
        }
    };
    private ISessionActivityClassFactory activityClassFactory = DEFAULT_ACTIVITY_CLASS_FACTORY;
    public static final String EXTRA_SESSION_ID = "com.appliedrec.EXTRA_SESSION_ID";
    private static final String EXTRA_IS_SESSION_RESULT_ACTIVITY = "com.appliedrec.EXTRA_IS_SESSION_RESULT_ACTIVITY";
    private static final String EXTRA_IS_VERID_ACTIVITY = "com.appliedrec.EXTRA_IS_VERID_ACTIVITY";
    public static final String EXTRA_ERROR = "com.appliedrec.EXTRA_ERROR";
    private static final Object SESSION_ID_LOCK = new Object();
    private static long lastSessionId = 0;

    public VerIDSession(Context context, VerID verID, T settings) {
        this(context, verID, settings, null);
    }

    public VerIDSession(Context context, VerID verID, T settings, TranslatedStrings translatedStrings) {
        this.contextRef = new WeakReference<>(context);
        this.verID = verID;
        this.settings = settings;
        if (translatedStrings == null) {
            this.translatedStrings = new TranslatedStrings(context, null);
        } else {
            this.translatedStrings = translatedStrings;
        }
        synchronized (SESSION_ID_LOCK) {
            this.sessionId = lastSessionId++;
        }
    }

    public T getSettings() {
        return settings;
    }

    public VerID getVerID() {
        return verID;
    }

    public VerIDSessionDelegate<U, T> getDelegate() {
        return delegateRef.get();
    }

    public void setDelegate(VerIDSessionDelegate<U, T> delegate) {
        this.delegateRef = new WeakReference<>(delegate);
    }

    public TranslatedStrings getTranslatedStrings() {
        return translatedStrings;
    }

    public void setTranslatedStrings(TranslatedStrings translatedStrings) {
        this.translatedStrings = translatedStrings;
    }

    public static <U extends Face, T extends VerIDSessionSettings<U>> Single<Configuration<U,T>> configure(Context context, T settings) {
        Configuration<U,T> configuration = new Configuration<>(context, settings);
        return Single.just(configuration);
    }

    public static Single<Configuration> setVerIDIdentity(Configuration configuration, VerIDIdentity identity) {
        configuration.identity = identity;
        return Single.just(configuration);
    }

    public static Single<Configuration> setFaceDetectionFactory(Configuration configuration, IFaceDetectionFactory faceDetectionFactory) {
        configuration.faceDetectionFactory = faceDetectionFactory;
        return Single.just(configuration);
    }

    public static Single<Configuration> setFaceRecognitionFactory(Configuration configuration, IFaceRecognitionFactory faceRecognitionFactory) {
        configuration.faceRecognitionFactory = faceRecognitionFactory;
        return Single.just(configuration);
    }

    public static Single<Configuration> setUserManagementFactory(Configuration configuration, IUserManagementFactory userManagementFactory) {
        configuration.userManagementFactory = userManagementFactory;
        return Single.just(configuration);
    }

    public static Single<Configuration> setTranslation(Configuration configuration, TranslatedStrings translatedStrings) {
        configuration.translatedStrings = translatedStrings;
        return Single.just(configuration);
    }

    public static <U extends Face, T extends VerIDSessionSettings<U>> Single<VerIDSession<U,T>> create(Context context, VerID verID, T settings, TranslatedStrings translatedStrings) {
        VerIDSession<U,T> verIDSession = new VerIDSession<>(context, verID, settings, translatedStrings);
        return Single.just(verIDSession);
    }

    public static <U extends Face, T extends VerIDSessionSettings<U>> Single<VerIDSession<U,T>> create(Configuration<U,T> configuration) {
        return Single.create(emitter -> {
            try {
                VerIDFactory verIDFactory = new VerIDFactory(configuration.context);
                if (configuration.identity != null) {
                    verIDFactory.setIdentity(configuration.identity);
                }
                if (configuration.faceDetectionFactory != null) {
                    verIDFactory.setFaceDetectionFactory(configuration.faceDetectionFactory);
                }
                if (configuration.faceRecognitionFactory != null) {
                    verIDFactory.setFaceRecognitionFactory(configuration.faceRecognitionFactory);
                }
                if (configuration.userManagementFactory != null) {
                    verIDFactory.setUserManagementFactory(configuration.userManagementFactory);
                }
                VerID verID = verIDFactory.createVerIDSync();
                emitter.onSuccess(new VerIDSession<>(configuration.context, verID, configuration.settings, configuration.translatedStrings));
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    public static <U extends Face, T extends VerIDSessionSettings<U>> Observable<FaceCapture<U>> startSession(VerIDSession<U,T> verIDSession) {
        Context context = verIDSession.contextRef.get();
        if (context == null) {
            return Observable.error(new Exception("Context is null"));
        }
        Observable<FaceCapture<U>> observable = Observable.create(emitter -> verIDSession.emitter = emitter);
        return observable.doOnSubscribe(consumer -> {
            if (consumer.isDisposed() || verIDSession.isActivityStarted) {
                return;
            }
            verIDSession.isActivityStarted = true;
            Intent intent = verIDSession.createFaceRecognitionIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.putExtra(EXTRA_SESSION_ID, verIDSession.sessionId);
            intent.putExtra(EXTRA_IS_VERID_ACTIVITY, true);
            ((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(verIDSession);
            context.startActivity(intent);
        });
    }

    public void start() throws Exception {
        VerIDSessionDelegate<U,T> delegate = delegateRef.get();
        if (delegate == null) {
            throw new Exception("Delegate is not set");
        }
        Context context = contextRef.get();
        if (context == null) {
            throw new Exception("Context is null");
        }
        disposable = VerIDSession.startSession(this).toList().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                faceCaptures -> {
                    FaceCapture<U>[] faceCapturesArray = new FaceCapture[faceCaptures.size()];
                    faceCaptures.toArray(faceCapturesArray);
                    delegate.onSessionFinished(this, faceCapturesArray);
                    if (disposable != null) {
                        disposable.dispose();
                        disposable = null;
                    }
                },
                error -> {
                    delegate.onSessionFailed(this, error);
                    if (disposable != null) {
                        disposable.dispose();
                        disposable = null;
                    }
                }
        );
    }

    public synchronized void setActivityClassFactory(ISessionActivityClassFactory factory) {
        this.activityClassFactory = factory;
    }

    public synchronized ISessionActivityClassFactory getActivityClassFactory() {
        return activityClassFactory;
    }

    protected Intent createFaceRecognitionIntent() {
        return new Intent(contextRef.get(), getActivityClassFactory().getFaceRecognitionActivityClass());
    }

    @NonNull
    protected Intent createResultIntent(Throwable error) {
        return new Intent(contextRef.get(), getActivityClassFactory().getResultActivityClass(error));
    }

    protected IFaceDetectionServiceFactory getFaceDetectionServiceFactory() {
        return new FaceDetectionServiceFactory(getVerID());
    }

    protected IResultEvaluationServiceFactory<T, U> getResultEvaluationServiceFactory() {
        return new ResultEvaluationServiceFactory<>(getVerID());
    }

    protected IImageProviderService createImageProviderService(Activity activity) {
        if (activity instanceof IImageProviderService) {
            return (IImageProviderService) activity;
        }
        return null;
    }

    protected IFaceDetectionListener createFaceDetectionListener(Activity activity) {
        if (activity instanceof IFaceDetectionListener) {
            return (IFaceDetectionListener) activity;
        }
        return null;
    }

    protected void onSessionFinished(Activity activity, Throwable error) {
        if (getSettings().getShowResult()) {
            Intent resultIntent = createResultIntent(error);
            resultIntent.putExtra(EXTRA_SESSION_ID, sessionId);
            resultIntent.putExtra(EXTRA_IS_SESSION_RESULT_ACTIVITY, true);
            if (error != null) {
                resultIntent.putExtra(EXTRA_ERROR, error);
            }
            activity.startActivity(resultIntent);
            activity.finish();
            return;
        }
        onDone(activity);
    }

    private long getSessionIdFromActivity(Activity activity) {
        return activity.getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
    }

    private boolean isVerIDActivity(Activity activity) {
        return activity.getIntent().getBooleanExtra(EXTRA_IS_VERID_ACTIVITY, false);
    }

    private boolean isResultActivity(Activity activity) {
        return activity.getIntent().getBooleanExtra(EXTRA_IS_SESSION_RESULT_ACTIVITY, false);
    }

    //region IOnDoneListener

    @Override
    public void onDone(@NonNull Activity activity) {
        isActivityStarted = false;
        onFinished(activity);
        activity.finish();
    }

    private void onFinished(@NonNull Activity activity) {
        if (emitter != null) {
            Throwable error = (Throwable) activity.getIntent().getSerializableExtra(EXTRA_ERROR);
            if (error != null) {
                emitter.onError(error);
            } else {
                emitter.onComplete();
            }
            emitter = null;
        }
    }

    //endregion

    //region ActivityLifecycleCallbacks

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        long activitySessionId = getSessionIdFromActivity(activity);
        if (activitySessionId == sessionId) {
            if (activity instanceof IVerIDSessionSettingsSettable) {
                ((IVerIDSessionSettingsSettable)activity).setSessionSettings(getSettings());
            }
            if (isVerIDActivity(activity)) {
                IImageProviderService imageProviderService = createImageProviderService(activity);
                IFaceDetectionListener faceDetectionListener = createFaceDetectionListener(activity);
                if (imageProviderService == null || faceDetectionListener == null) {
                    return;
                }
                try {
                    faceImage = null;
                    sessionDisposable = Observable.fromPublisher(new SessionPublisher<>(getVerID(), getSettings(), imageProviderService, faceDetectionListener, getFaceDetectionServiceFactory(), getResultEvaluationServiceFactory()))
                            .doOnNext(faceCapture -> {
                                if (faceCapture.getBearing() == Bearing.STRAIGHT && faceImage == null) {
                                    Rect cropRect = new Rect();
                                    faceCapture.getFace().getBounds().round(cropRect);
                                    cropRect.intersect(new Rect(0, 0, faceCapture.getImage().getWidth(), faceCapture.getImage().getHeight()));
                                    try {
                                        faceImage = Bitmap.createBitmap(faceCapture.getImage(), cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
                                    } catch (Exception ignore) {
                                    }
                                }
                            })
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(faceCapture -> {
                                if (activity instanceof IFaceCaptureListener) {
                                    ((IFaceCaptureListener<U>) activity).onFaceCapture(faceCapture);
                                }
                                if (emitter != null) {
                                    emitter.onNext(faceCapture);
                                }
                            }, error -> {
                                onSessionFinished(activity, error);
                            }, () -> {
                                onSessionFinished(activity, null);
                            });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (faceImage != null && activity instanceof IFaceImageSettable) {
                ((IFaceImageSettable) activity).setFaceImage(faceImage);
            }
            if (activity instanceof IOnDoneListenerSettable) {
                ((IOnDoneListenerSettable) activity).setOnDoneListener(this);
            }
            if (activity instanceof ITranslationSettable) {
                ((ITranslationSettable)activity).setTranslatedStrings(this.translatedStrings);
            }
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {}

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof IImageProviderService) {
            ((IImageProviderService)activity).startCollectingImages();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity instanceof IImageProviderService) {
            ((IImageProviderService)activity).stopCollectingImages();
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        long activitySessionId = getSessionIdFromActivity(activity);
        if (activitySessionId == sessionId) {
            if (isVerIDActivity(activity) && sessionDisposable != null) {
                sessionDisposable.dispose();
                sessionDisposable = null;
            }
            if (activity instanceof IOnDoneListenerSettable) {
                ((IOnDoneListenerSettable)activity).setOnDoneListener(null);
            }
        }
    }

    //endregion
}
