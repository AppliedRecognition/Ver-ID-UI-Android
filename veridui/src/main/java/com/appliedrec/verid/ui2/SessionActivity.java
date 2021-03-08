package com.appliedrec.verid.ui2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Keep;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.appliedrec.verid.core2.Bearing;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceCapture;
import com.appliedrec.verid.core2.session.FaceDetectionResult;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.RegistrationSessionSettings;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.core2.session.VerIDSessionSettings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Activity used to control Ver-ID sessions
 * @since 2.0.0
 */
@Keep
public class SessionActivity<T extends View & ISessionView> extends AppCompatActivity implements ISessionActivity, ISessionView.SessionViewListener, Iterable<FaceBounds>, CameraWrapper.Listener {

    /**
     * The constant EXTRA_SESSION_ID.
     */
    @Keep
    public static final String EXTRA_SESSION_ID = "com.appliedrec.verid.EXTRA_SESSION_ID";
    /**
     * The constant REQUEST_CODE_CAMERA_PERMISSION.
     */
    protected static final int REQUEST_CODE_CAMERA_PERMISSION = 10;
    private static final int REQUEST_CODE_TIPS = 11;
    private static final int REQUEST_CODE_SESSION_RESULT = 12;
    private ExecutorService backgroundExecutor;
    private final ArrayList<Bitmap> faceImages = new ArrayList<>();
    private CameraWrapper cameraWrapper;
    private T sessionView;
    private final Object sessionViewLock = new Object();
    private Session<?> session;
    private SessionPrompts sessionPrompts;
    private final AtomicBoolean isSessionRunning = new AtomicBoolean(false);
    private SessionParameters sessionParameters;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        faceImages.clear();
        backgroundExecutor = Executors.newSingleThreadExecutor();
        synchronized (sessionViewLock) {
            sessionView = (T) getSessionParameters().map(SessionParameters::getSessionViewFactory).orElseThrow(RuntimeException::new).apply(this);
            sessionView.setDefaultFaceExtents(getDefaultFaceExtents());
            sessionView.addListener(this);
            sessionViewLock.notifyAll();
        }
        setContentView(sessionView);
        getCameraWrapper().ifPresent(wrapper -> {
            wrapper.setPreviewClass(sessionView.getPreviewClass());
            wrapper.addListener(this);
        });
        sessionPrompts = new SessionPrompts(getSessionParameters().map(SessionParameters::getStringTranslator).orElse(null));
        getSession().ifPresent(session1 -> {
            session1.getFaceDetectionLiveData().observe(this, this::onFaceDetection);
            session1.getFaceCaptureLiveData().observe(this, this::onFaceCapture);
            session1.getSessionResultLiveData().observe(this, this::onSessionResult);
        });
        executeInBackground(() -> {
            List<? extends Drawable> drawables = createFaceDrawables();
            runOnUiThread(() -> drawFaces(drawables));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        backgroundExecutor = null;
        faceImages.clear();
        getCameraWrapper().ifPresent(wrapper -> wrapper.removeListener(this));
        cameraWrapper = null;
        if (sessionView != null) {
            sessionView.removeListener(this);
        }
        sessionView = null;
        getSessionVideoRecorder().ifPresent(getLifecycle()::removeObserver);
        getSession().ifPresent(session1 -> {
            session1.getFaceDetectionLiveData().removeObservers(this);
            session1.getFaceCaptureLiveData().removeObservers(this);
            session1.getSessionResultLiveData().removeObservers(this);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSession();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            cancelSession();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SESSION_RESULT) {
            getSessionParameters().flatMap(SessionParameters::getOnSessionFinishedRunnable).ifPresent(Runnable::run);
            finish();
        }
    }

    /**
     * Start Ver-ID session
     * @since 2.0.0
     */
    @Keep
    protected void startSession() {
        if (isSessionRunning.compareAndSet(false, true)) {
            getSession().ifPresent(Session::start);
        }
    }

    /**
     * Cancel Ver-ID session
     * @since 2.0.0
     */
    @Keep
    protected void cancelSession() {
        if (isSessionRunning.compareAndSet(true, false)) {
            getSession().ifPresent(Session::cancel);
            getSessionParameters().flatMap(SessionParameters::getOnSessionCancelledRunnable).ifPresent(Runnable::run);
        }
    }

    /**
     * Get Ver-ID session
     * @return Ver-ID session running in this activity
     * @since 2.0.0
     */
    @Keep
    protected Optional<Session<?>> getSession() {
        return Optional.ofNullable(session);
    }

    //region Session view listener

    @Override
    public void onPreviewSurfaceCreated(Surface surface) {
        try {
            CameraWrapper cameraWrapper = getCameraWrapper().orElseThrow(() -> new Exception("Camera wrapper unavailable"));
            cameraWrapper.setPreviewSurface(surface);
            startCamera();
        } catch (Exception e) {
            fail(new VerIDSessionException(e));
        }
    }

    @Override
    public void onPreviewSurfaceDestroyed() {
        getCameraWrapper().ifPresent(CameraWrapper::stop);
    }

    //endregion

    /**
     * Fail the session
     * @param error Session exception to return in the session result
     * @since 2.0.0
     */
    @Keep
    protected void fail(VerIDSessionException error) {
        if (isSessionRunning.compareAndSet(true, false)) {
            getSession().ifPresent(Session::cancel);
            long now = System.currentTimeMillis();
            getSessionParameters().ifPresent(sessionParams -> sessionParams.setSessionResult(new VerIDSessionResult(error, now, now, null)));
            getSessionParameters().flatMap(SessionParameters::getOnSessionFinishedRunnable).ifPresent(Runnable::run);
            finish();
        }
    }

    @Keep
    @NonNull
    protected Optional<T> getSessionView() {
        return Optional.ofNullable(sessionView);
    }

    @Keep
    protected final Optional<CameraWrapper> getCameraWrapper() {
        return Optional.ofNullable(cameraWrapper);
    }

    @Keep
    protected Size getViewSize() {
        return getSessionView().map(view -> new Size(view.getWidth(), view.getHeight())).orElse(new Size(0, 0));
    }

    @Keep
    protected int getDisplayRotation() {
        return getSessionView().map(ISessionView::getDisplayRotation).orElse(0);
    }

    @Keep
    protected int getFaceImageHeight() {
        return getSessionView().map(ISessionView::getCapturedFaceImageHeight).orElse(0);
    }

    @Keep
    protected void drawFaces(List<? extends Drawable> faceImages) {
        if (sessionView == null) {
            return;
        }
        sessionView.drawFaces(faceImages);
    }

    //region ISessionActivity

    @Keep
    @Override
    public void setSessionParameters(SessionParameters sessionParameters) {
        this.sessionParameters = sessionParameters;
        IImageIterator imageIterator = sessionParameters.getImageIteratorFactory().apply(sessionParameters.getVerID());
        cameraWrapper = new CameraWrapper(this, sessionParameters.getCameraLocation(), imageIterator, sessionParameters.getVideoRecorder().orElse(null));
        Session.Builder<?> sessionBuilder = new Session.Builder<>(sessionParameters.getVerID(), sessionParameters.getSessionSettings(), imageIterator, this);
        sessionBuilder.setSessionFunctions(sessionParameters.getSessionFunctions());
        sessionBuilder.bindToLifecycle(this.getLifecycle());
        session = sessionBuilder.build();
        sessionParameters.getVideoRecorder().ifPresent(videoRecorder -> getLifecycle().addObserver(videoRecorder));
    }

    @Keep
    protected void onFaceDetection(@NonNull FaceDetectionResult faceDetectionResult) {
        getSessionParameters().flatMap(SessionParameters::getFaceDetectionResultObserver).ifPresent(observer -> observer.onChanged(faceDetectionResult));
        runOnUiThread(() -> {
            if (sessionView == null) {
                return;
            }
            sessionView.setFaceDetectionResult(faceDetectionResult, sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null));
        });
    }

    @Keep
    protected void onFaceCapture(@NonNull FaceCapture faceCapture) {
        getSessionParameters().flatMap(SessionParameters::getFaceCaptureObserver).ifPresent(faceCaptureObserver -> faceCaptureObserver.onChanged(faceCapture));
        if (getSessionParameters().map(SessionParameters::getSessionSettings).orElse(null) instanceof RegistrationSessionSettings) {
            executeInBackground(() -> {
                float targetHeight = (float)getFaceImageHeight();
                float scale = targetHeight / (float) faceCapture.getFaceImage().getHeight();
                Bitmap bitmap = Bitmap.createScaledBitmap(faceCapture.getFaceImage(), Math.round((float) faceCapture.getFaceImage().getWidth() * scale), Math.round((float) faceCapture.getFaceImage().getHeight() * scale), true);
                if (getSessionParameters().map(SessionParameters::getCameraLocation).orElse(CameraLocation.FRONT) == CameraLocation.FRONT) {
                    Matrix matrix = new Matrix();
                    matrix.setScale(-1, 1);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                }
                faceImages.add(bitmap);
                List<? extends Drawable> drawables = createFaceDrawables();
                runOnUiThread(() -> drawFaces(drawables));
            });
        }
    }

    @Keep
    protected void onSessionResult(@NonNull VerIDSessionResult result) {
        if (!isSessionRunning.compareAndSet(true, false)) {
            return;
        }
        getSessionVideoRecorder().flatMap(recorder -> {
            recorder.stop();
            return recorder.getVideoFile();
        }).ifPresent(videoFile -> {
            result.setVideoUri(Uri.fromFile(videoFile));
        });
        getSessionParameters().flatMap(SessionParameters::getSessionResultObserver).ifPresent(observer -> observer.onChanged(result));

        if (result.getError().isPresent() && getSessionParameters().flatMap(SessionParameters::shouldRetryOnFailure).orElse(exception -> false).apply(result.getError().get()) && getSessionParameters().map(SessionParameters::getSessionFailureDialogFactory).isPresent()) {
            AlertDialog alertDialog = getSessionParameters().map(SessionParameters::getSessionFailureDialogFactory).get().makeDialog(this, onDismissAction -> {
                switch (onDismissAction) {
                    case RETRY:
                        startSession();
                        break;
                    case CANCEL:
                        getSessionParameters().flatMap(SessionParameters::getOnSessionCancelledRunnable).ifPresent(Runnable::run);
                        finish();
                        break;
                    case SHOW_TIPS:
                        Intent tipsActivityIntent = getSessionParameters().map(SessionParameters::getTipsIntentSupplier).map(supplier -> supplier.apply(this)).orElseThrow(RuntimeException::new);
                        startActivityForResult(tipsActivityIntent, REQUEST_CODE_TIPS);
                        break;
                }
            }, result.getError().get(), getSessionParameters().map(SessionParameters::getStringTranslator).orElse(null));
            if (alertDialog != null) {
                alertDialog.show();
                return;
            }
        }
        if (getSessionParameters().map(SessionParameters::getSessionResultDisplayIndicator).orElse(result1 -> false).apply(result) && getSessionParameters().map(SessionParameters::getResultIntentSupplier).isPresent()) {
            Intent intent = getSessionParameters().map(SessionParameters::getResultIntentSupplier).get().apply(result, this);
            startActivityForResult(intent, REQUEST_CODE_SESSION_RESULT);
        } else if (getSessionParameters().flatMap(SessionParameters::getOnSessionFinishedRunnable).isPresent()) {
            getSessionParameters().flatMap(SessionParameters::getOnSessionFinishedRunnable).get().run();
            finish();
        }
    }

    @Keep
    public Optional<SessionParameters> getSessionParameters() {
        return Optional.ofNullable(sessionParameters);
    }

    private List<? extends Drawable> createFaceDrawables() {
        ArrayList<RoundedBitmapDrawable> bitmapDrawables = new ArrayList<>();
        getSessionParameters().map(SessionParameters::getSessionSettings).ifPresent(sessionSettings -> {
            if (sessionSettings instanceof RegistrationSessionSettings) {
                Bearing[] bearingsToRegister = ((RegistrationSessionSettings) sessionSettings).getBearingsToRegister();
                float targetHeight = (float) getFaceImageHeight();
                for (int i = 0; i < sessionSettings.getFaceCaptureCount(); i++) {
                    RoundedBitmapDrawable drawable;
                    Bitmap bitmap;
                    if (i < faceImages.size()) {
                        bitmap = faceImages.get(i);
                    } else {
                        int bearingIndex = i % bearingsToRegister.length;
                        Bearing bearing = bearingsToRegister[bearingIndex];
                        bitmap = BitmapFactory.decodeResource(getResources(), placeholderImageForBearing(bearing));
                        if (getSessionParameters().map(SessionParameters::getCameraLocation).orElse(CameraLocation.FRONT) == CameraLocation.FRONT) {
                            Matrix matrix = new Matrix();
                            matrix.setScale(-1, 1);
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                        }
                    }
                    drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                    drawable.setCornerRadius(targetHeight / 6f);
                    bitmapDrawables.add(drawable);
                }
            }
        });
        return bitmapDrawables;
    }

    @DrawableRes
    private int placeholderImageForBearing(Bearing bearing) {
        switch (bearing) {
            case STRAIGHT:
                return R.mipmap.head_straight;
            case LEFT:
                return R.mipmap.head_left;
            case RIGHT:
                return R.mipmap.head_right;
            case UP:
                return R.mipmap.head_up;
            case DOWN:
                return R.mipmap.head_down;
            case LEFT_UP:
                return R.mipmap.head_left_up;
            case RIGHT_UP:
                return R.mipmap.head_right_up;
            case LEFT_DOWN:
                return R.mipmap.head_left_down;
            case RIGHT_DOWN:
                return R.mipmap.head_right_down;
            default:
                return R.mipmap.head_straight;
        }
    }

    //endregion

    //region Video recording

    @Keep
    protected Optional<ISessionVideoRecorder> getSessionVideoRecorder() {
        return getSessionParameters().flatMap(SessionParameters::getVideoRecorder);
    }

    //endregion

    /**
     * Start camera.
     */
    @SuppressLint("MissingPermission")
    @MainThread
    private void startCamera() throws Exception {
        if (hasCameraPermission()) {
            Size viewSize = getViewSize();
            int displayRotation = getDisplayRotation();
            CameraWrapper cameraWrapper = getCameraWrapper().orElseThrow(() -> new Exception("Camera wrapper unavailable"));
            cameraWrapper.start(viewSize.width, viewSize.height, displayRotation);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    /**
     * Execute in background.
     *
     * @param runnable the runnable
     */
    @Keep
    protected final void executeInBackground(Runnable runnable) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(runnable);
        }
    }

    /**
     * Gets default face extents.
     *
     * @return the default face extents
     */
    @Keep
    protected final FaceExtents getDefaultFaceExtents() {
        return getSessionParameters().map(SessionParameters::getSessionSettings).map(VerIDSessionSettings::getExpectedFaceExtents).orElse(new LivenessDetectionSessionSettings().getExpectedFaceExtents());
    }

    //region Camera permissions

    @Override
    public final void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            for (int i=0; i<permissions.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        try {
                            startCamera();
                        } catch (Exception e ) {
                            fail(new VerIDSessionException(e));
                        }
                    } else {
                        fail(new VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED));
                    }
                    return;
                }
            }
        }
    }

    /**
     * Has camera permission boolean.
     *
     * @return the boolean
     */
    @Keep
    protected final boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    //endregion

    //region Face bounds iterator

    @NonNull
    @Override
    @Keep
    public Iterator<FaceBounds> iterator() {
        synchronized (sessionViewLock) {
            while (sessionView == null) {
                try {
                    sessionViewLock.wait();
                } catch (InterruptedException ignore) {
                    return new Iterator<FaceBounds>() {
                        @Override
                        public boolean hasNext() {
                            return false;
                        }

                        @Override
                        public FaceBounds next() {
                            return null;
                        }
                    };
                }
            }
            return sessionView;
        }
    }

    //endregion

    //region Camera wrapper listener

    @Override
    public void onCameraPreviewSize(int width, int height, int sensorOrientation) {
        sessionView.setPreviewSize(width, height, sensorOrientation);
    }

    @Override
    public void onCameraError(VerIDSessionException error) {
        fail(error);
    }

    //endregion
}
