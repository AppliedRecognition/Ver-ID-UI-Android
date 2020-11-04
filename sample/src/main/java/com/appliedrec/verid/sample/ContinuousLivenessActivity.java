/**
 * This activity demonstrates how Ver-ID can be used in a kiosk situation.
 *
 * The activity overlays the camera preview with a semi-transparent background and waits until
 * an user steps in front of the camera. As soon as a face is detected the activity launches
 * a Ver-ID liveness detection session. When the session finishes the activity asks the user to
 * step away from the camera to make room for the next user.
 *
 * The liveness detection session can easily be substituted for an authentication session.
 * Alternatively the collected faces can be passed to UserIdentification's identifyUsersInFace
 * function to identify the user in the face.
 */


package com.appliedrec.verid.sample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.appliedrec.verid.core2.Face;
import com.appliedrec.verid.core2.FaceDetectionImage;
import com.appliedrec.verid.core2.IFaceTracking;
import com.appliedrec.verid.core2.Size;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.VerIDImage;
import com.appliedrec.verid.core2.session.FaceBounds;
import com.appliedrec.verid.core2.session.FaceExtents;
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core2.session.Session;
import com.appliedrec.verid.core2.session.VerIDSessionResult;
import com.appliedrec.verid.sample.databinding.ActivityContinuousLivenessBinding;
import com.appliedrec.verid.ui2.AbstractSessionFragment;
import com.appliedrec.verid.ui2.CameraLocation;
import com.appliedrec.verid.ui2.CameraWrapper;
import com.appliedrec.verid.ui2.SessionPrompts;
import com.appliedrec.verid.ui2.TranslatedStrings;
import com.appliedrec.verid.ui2.VerIDImageAnalyzer;
import com.appliedrec.verid.ui2.VerIDSessionFragmentWithTextureView;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ContinuousLivenessActivity extends AppCompatActivity implements IVerIDLoadObserver, Iterable<FaceBounds>, Iterator<FaceBounds>, TextureView.SurfaceTextureListener, CameraWrapper.Listener {

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 0;
    ActivityContinuousLivenessBinding viewBinding;
    private final VerIDImageAnalyzer imageAnalyzer = new VerIDImageAnalyzer(this);
    private ThreadPoolExecutor imageProcessingExecutor;
    private ExecutorService backgroundExecutor;
    private Session<LivenessDetectionSessionSettings> session;
    private Disposable faceDetectionDisposable;
    private final LivenessDetectionSessionSettings sessionSettings = new LivenessDetectionSessionSettings();
    private VerIDSessionFragmentWithTextureView sessionFragment;
    private VerID verID;
    private SessionPrompts sessionPrompts;
    private SynchronousQueue<Size> viewFinderSizeQueue = new SynchronousQueue<>();
    private View.OnLayoutChangeListener onLayoutChangeListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        Size newSize = new Size(right-left, bottom-top);
        if (newSize.width > 0 && newSize.height > 0) {
            executeInBackground(() -> {
                try {
                    viewFinderSizeQueue.put(newSize);
                } catch (InterruptedException ignore) {
                }
            });
        }
    };
    private CameraWrapper cameraWrapper;
    private AtomicBoolean isSessionRunning = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityContinuousLivenessBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        sessionPrompts  = new SessionPrompts(new TranslatedStrings(this, null));
        imageProcessingExecutor = new ThreadPoolExecutor(0, 1, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Ver-ID image processing");
            return thread;
        });
        backgroundExecutor = Executors.newSingleThreadExecutor();
        viewBinding.retryButton.setOnClickListener(view -> startSession());
        viewBinding.idle.setVisibility(View.VISIBLE);
        viewBinding.sessionResult.setVisibility(View.VISIBLE);
        sessionFragment = (VerIDSessionFragmentWithTextureView)getSupportFragmentManager().findFragmentById(R.id.sessionFragment);

        cameraWrapper = new CameraWrapper(this, CameraLocation.FRONT, imageAnalyzer, null, SurfaceTexture.class);
        cameraWrapper.setListener(this);

        session.getFaceDetectionLiveData().observe(this, faceDetectionResult -> {
            if (isSessionRunning.get()) {
                sessionFragment.accept(faceDetectionResult, sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null));
            }
        });
        session.getSessionResultLiveData().observe(this, this::onSessionResult);
        runFaceDetectionUntil(hasFace -> hasFace, this::startSession);
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSessionFragment().flatMap(sessionFragment -> Optional.ofNullable(sessionFragment.getView())).ifPresent(view -> {
            Size newSize = new Size(view.getWidth(), view.getHeight());
            if (newSize.width > 0 && newSize.height > 0) {
                executeInBackground(() -> {
                    try {
                        viewFinderSizeQueue.put(newSize);
                    } catch (InterruptedException ignore) {
                    }
                });
            }
            view.addOnLayoutChangeListener(onLayoutChangeListener);
        });
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(textureView -> textureView.setSurfaceTextureListener(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraWrapper != null) {
            cameraWrapper.stop();
            cameraWrapper = null;
        }
        getSessionFragment().flatMap(AbstractSessionFragment::getViewFinder).ifPresent(textureView -> textureView.setSurfaceTextureListener(null));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getSessionFragment().flatMap(sessionFragment -> Optional.ofNullable(sessionFragment.getView())).ifPresent(view -> view.removeOnLayoutChangeListener(onLayoutChangeListener));
        viewBinding = null;
        sessionPrompts = null;
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        faceDetectionDisposable = null;
        if (session != null) {
            session.cancel();
            session = null;
        }
        if (imageProcessingExecutor != null) {
            imageProcessingExecutor.shutdown();
            imageProcessingExecutor = null;
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
            backgroundExecutor = null;
        }
        sessionFragment = null;
        verID = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (hasCameraPermission()) {
                startCamera();
            } else {
                Toast.makeText(this, "Failed to obtain camera permission", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private Optional<VerIDSessionFragmentWithTextureView> getSessionFragment() {
        return Optional.ofNullable(sessionFragment);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        getSessionFragment().ifPresent(fragment -> {
            if (fragment.getView() == null) {
                return;
            }
            int width = fragment.getView().getWidth();
            int height = fragment.getView().getHeight();
            int displayRotation = getDisplayRotation();

            cameraWrapper.start(width, height, displayRotation);
        });
    }

    @Override
    public void onVerIDLoaded(VerID verid) {
        verID = verid;
        imageAnalyzer.setVerID(verID);
        session = new Session.Builder<>(verid, sessionSettings, imageAnalyzer, this).bindToLifecycle(this.getLifecycle()).build();
    }

    @Override
    public void onVerIDUnloaded() {
        verID = null;
    }

    private void runFaceDetectionUntil(Predicate<Boolean> predicate, Action onComplete) {
        if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
            faceDetectionDisposable.dispose();
        }
        IFaceTracking<FaceDetectionImage> faceTracking = startFaceTracking();
        faceDetectionDisposable = Flowable.create(imageAnalyzer, BackpressureStrategy.LATEST)
                .map(image -> {
                    VerIDImage<FaceDetectionImage> verIDImage = (VerIDImage<FaceDetectionImage>)image;
                    Face face = faceTracking.trackFaceInImage(verIDImage.createFaceDetectionImage());
                    return face != null;
                })
                .takeUntil(predicate)
                .ignoreElements()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        onComplete,
                        error -> {
                            Log.e("Ver-ID", Objects.requireNonNull(error.getLocalizedMessage()));
                        }
                );
    }

    private <DetectionImage> IFaceTracking<DetectionImage> startFaceTracking() {
        return (IFaceTracking<DetectionImage>) verID.getFaceDetection().startFaceTracking();
    }

    private void startSession() {
        runOnUiThread(() -> {
            if (faceDetectionDisposable != null && !faceDetectionDisposable.isDisposed()) {
                faceDetectionDisposable.dispose();
            }
            faceDetectionDisposable = null;
            if (viewBinding == null) {
                return;
            }
            viewBinding.sessionResult.setVisibility(View.GONE);
            if (session != null && isSessionRunning.compareAndSet(false, true)) {
                session.start();
            }
        });
    }

    private void onSessionResult(VerIDSessionResult sessionResult) {
        getSessionFragment().ifPresent(sessionFragment -> sessionFragment.accept(null, null));
        isSessionRunning.set(false);
        if (viewBinding == null) {
            return;
        }
        viewBinding.sessionResult.setVisibility(View.VISIBLE);
        viewBinding.idle.setVisibility(View.GONE);
        viewBinding.success.setVisibility(sessionResult.getError().isPresent() ? View.GONE : View.VISIBLE);
        viewBinding.failure.setVisibility(sessionResult.getError().isPresent() ? View.VISIBLE : View.GONE);
        runFaceDetectionUntil(hasFace -> !hasFace, () -> {
            if (viewBinding == null) {
                return;
            }
            viewBinding.idle.setVisibility(View.VISIBLE);
            viewBinding.success.setVisibility(View.GONE);
            viewBinding.failure.setVisibility(View.GONE);
            runFaceDetectionUntil(hasFace -> hasFace, this::startSession);
        });
    }

    //region Iterable<FaceBounds>

    @NonNull
    @Override
    public Iterator<FaceBounds> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public FaceBounds next() {
        try {
            return new FaceBounds(viewFinderSizeQueue.take(), getDefaultFaceExtents());
        } catch (InterruptedException e) {
            return new FaceBounds(Size.ZERO, getDefaultFaceExtents());
        }
    }

    //endregion

    private void executeInBackground(Runnable runnable) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(runnable);
        }
    }

    private FaceExtents getDefaultFaceExtents() {
        return sessionSettings.getExpectedFaceExtents();
    }

    @Override
    public void onPreviewSize(int width, int height, int sensorOrientation) {
        runOnUiThread(() -> {
            if (viewBinding == null) {
                return;
            }
            getSessionFragment().ifPresent(sessionFragment -> {
                View fragmentView = sessionFragment.getView();
                if (fragmentView == null) {
                    return;
                }
                float fragmentWidth = fragmentView.getWidth();
                float fragmentHeight = fragmentView.getHeight();
                sessionFragment.getViewFinder().ifPresent(viewFinder -> {
                    int rotationDegrees = getDisplayRotation();
                    float w, h;
                    if ((sensorOrientation - rotationDegrees) % 180 == 0) {
                        w = width;
                        h = height;
                    } else {
                        w = height;
                        h = width;
                    }
                    float viewAspectRatio = fragmentWidth/fragmentHeight;
                    float imageAspectRatio = w/h;
                    final float scale;
                    if (viewAspectRatio > imageAspectRatio) {
                        scale = fragmentWidth/w;
                    } else {
                        scale = fragmentHeight/h;
                    }
                    ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(viewFinder.getLayoutParams());
                    layoutParams.width = (int) (scale * w);
                    layoutParams.height = (int) (scale * h);
                    layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                    layoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
                    layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                    layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                    viewFinder.setLayoutParams(layoutParams);
                });
            });
        });
    }

    @MainThread
    protected int getDisplayRotation() {
        if (viewBinding == null) {
            return 0;
        }
        switch (viewBinding.getRoot().getDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                return 0;
            case Surface.ROTATION_90:
                return  90;
            case Surface.ROTATION_180:
                return  180;
            case Surface.ROTATION_270:
                return 270;
        }
    }



    //region Surface callback

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        if (hasCameraPermission()) {
            if (cameraWrapper != null) {
                cameraWrapper.setPreviewSurface(new Surface(surfaceTexture));
            }
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (cameraWrapper != null) {
            cameraWrapper.stop();
            cameraWrapper = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    //endregion
}