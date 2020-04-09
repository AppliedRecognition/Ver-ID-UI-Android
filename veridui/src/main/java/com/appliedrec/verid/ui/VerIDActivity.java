package com.appliedrec.verid.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.OperationCanceledException;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.appliedrec.verid.core.Face;
import com.appliedrec.verid.core.FaceCapture;
import com.appliedrec.verid.core.FaceDetectionResult;
import com.appliedrec.verid.core.IFaceCaptureListener;
import com.appliedrec.verid.core.IFaceDetectionListener;
import com.appliedrec.verid.core.IImageProviderService;
import com.appliedrec.verid.core.RegistrationSessionSettings;
import com.appliedrec.verid.core.VerIDImage;
import com.appliedrec.verid.core.VerIDSessionSettings;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VerIDActivity<T extends Fragment & IFaceDetectionListener & IFaceCaptureListener<Face>> extends AppCompatActivity implements IImageProviderService, IFaceDetectionListener, IVerIDSessionSettingsSettable, VerIDSessionFragment.Listener, IFaceCaptureListener<Face>, ITranslationSettable, IStringTranslator, IOnDoneListenerSettable {

    private static final String FRAGMENT_TAG = "veridFragment";
    private final Object imageQueueThreadLock = new Object();
    private Thread imageQueueThread;
    private SynchronousQueue<VerIDImage> imageQueue = new SynchronousQueue<>();
    private final ThreadPoolExecutor imageProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private VerIDSessionSettings sessionSettings;
    private Throwable sessionException;
    private T fragment;
    private TranslatedStrings translatedStrings;
    private IOnDoneListener onDoneListener;
    private boolean isProvidingImages = false;
    private final Object isProvidingImagesLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verid);
        if (translatedStrings == null) {
            translatedStrings = new TranslatedStrings(this, getIntent());
        }
        if (savedInstanceState == null) {
            fragment = createFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.root, fragment, FRAGMENT_TAG).commit();
        } else {
            fragment = (T) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        }
    }

    @Override
    public void onBackPressed() {
        if (onDoneListener != null) {
            getIntent().putExtra(VerIDSession.EXTRA_ERROR, new OperationCanceledException());
            onDoneListener.onDone(this);
        } else {
            super.onBackPressed();
        }
    }

    protected T createFragment() {
        T fragment;
        if (sessionSettings != null && sessionSettings instanceof RegistrationSessionSettings) {
            fragment = (T) new VerIDRegistrationSessionFragment();
        } else {
            fragment = (T) new VerIDSessionFragment();
        }
        Bundle arguments = new Bundle();
        if (sessionSettings != null && sessionSettings instanceof Parcelable) {
            arguments.putParcelable(VerIDSessionFragment.ARG_SESSION_SETTINGS, (Parcelable) sessionSettings);
        }
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void setOnDoneListener(@Nullable IOnDoneListener onDoneListener) {
        this.onDoneListener = onDoneListener;
    }

    @Override
    public void onImage(Bitmap bitmap, int orientation) {
        if (imageProcessingExecutor.getActiveCount() == 0 && imageProcessingExecutor.getQueue().isEmpty()) {
            imageProcessingExecutor.execute(() -> {
                synchronized (isProvidingImagesLock) {
                    if (!isProvidingImages) {
                        return;
                    }
                }
                VerIDImage verIDImage = new VerIDImage(bitmap, orientation);
                try {
                    imageQueue.put(verIDImage);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void onError(Throwable throwable) {
        sessionException = throwable;
    }

    //region IImageProviderService

    @Override
    public final void startCollectingImages() {
        synchronized (isProvidingImagesLock) {
            isProvidingImages = true;
        }
        if (fragment != null && fragment instanceof ICameraOverlayShowable) {
            ((ICameraOverlayShowable)fragment).showCameraOverlay();
        }
    }

    @Override
    public final void stopCollectingImages() {
        synchronized (isProvidingImagesLock) {
            isProvidingImages = false;
        }
        if (fragment != null && fragment instanceof ICameraOverlayShowable) {
            ((ICameraOverlayShowable)fragment).hideCameraOverlay();
        }
    }

    @Override
    public VerIDImage dequeueImage() throws Exception {
        synchronized (imageQueueThreadLock) {
            imageQueueThread = Thread.currentThread();
        }
        if (sessionException != null) {
            throw new Exception(sessionException);
        }
        try {
            return imageQueue.take();
        } catch (InterruptedException e) {
            if (sessionException != null) {
                throw new Exception(sessionException);
            }
            throw new InterruptedException();
        }
    }

    //endregion

    public void setVerIDSessionSettings(VerIDSessionSettings settings) {
        this.sessionSettings = settings;
    }

    private void interruptImageQueueThread() {
        synchronized (imageQueueThreadLock) {
            if (imageQueueThread != null) {
                imageQueueThread.interrupt();
            }
        }
    }

    @Override
    public void onFaceDetectionResult(FaceDetectionResult faceDetectionResult) {
        if (fragment != null) {
            fragment.onFaceDetectionResult(faceDetectionResult);
        }
    }

    @Override
    public void onFaceCapture(FaceCapture<Face> faceCapture) {
        if (fragment != null) {
            fragment.onFaceCapture(faceCapture);
        }
    }

    @Override
    public void setTranslatedStrings(TranslatedStrings translatedStrings) {
        if (translatedStrings == null) {
            this.translatedStrings = new TranslatedStrings(this, null);
        } else {
            this.translatedStrings = translatedStrings;
        }
    }

    @Override
    public String getTranslatedString(String original, Object... args) {
        return translatedStrings.getTranslatedString(original, args);
    }

    //region IVerIDSessionSettingsSettable

    @Override
    public void setSessionSettings(VerIDSessionSettings sessionSettings) {
        this.sessionSettings = sessionSettings;
    }

    //endregion
}
