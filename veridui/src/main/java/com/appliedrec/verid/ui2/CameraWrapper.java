package com.appliedrec.verid.ui2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.appliedrec.verid.core2.ExifOrientation;
import com.appliedrec.verid.core2.VerID;
import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.VerIDSessionException;
import com.appliedrec.verid.core2.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interfaces with the device's camera
 * @since 2.0.0
 */
@Keep
public class CameraWrapper implements DefaultLifecycleObserver {

    /**
     * Camera wrapper listener
     * @since 2.0.0
     */
    @Keep
    public interface Listener {
        /**
         * Called when the camera determines its preview size based on the dimensions of the containing view
         * @param width Preview width
         * @param height Preview height
         * @param sensorOrientation Camera sensor orientation on the device
         * @since 2.0.0
         */
        @Keep
        @UiThread
        void onCameraPreviewSize(int width, int height, int sensorOrientation);

        /**
         * Called when opening the camera or starting a preview fails
         * @param error Session exception that caused the failure
         * @since 2.0.0
         */
        @Keep
        void onCameraError(VerIDSessionException error);
    }

    private final WeakReference<Context> contextWeakReference;
    private final CameraLocation cameraLocation;
    private IImageIterator imageIterator;
    private final AtomicReference<String> cameraId = new AtomicReference<>();
    private ImageReader imageReader;
    private ISessionVideoRecorder videoRecorder;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private final AtomicReference<CameraDevice> cameraDevice = new AtomicReference<>();
    private HandlerThread cameraProcessingThread;
    private Handler cameraProcessingHandler;
    private ExecutorService backgroundExecutor;
    private HandlerThread cameraPreviewThread;
    private Handler cameraPreviewHandler;
    private final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);
    private final AtomicInteger viewWidth = new AtomicInteger(0);
    private final AtomicInteger viewHeight = new AtomicInteger(0);
    private final AtomicInteger displayRotation = new AtomicInteger(0);
    private Class<?> previewClass;
    private CameraManager cameraManager;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final AtomicBoolean isCameraOpen = new AtomicBoolean(false);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final CameraPreviewHelper cameraPreviewHelper = new CameraPreviewHelper();
    private CameraCaptureSession cameraCaptureSession;

    private static final NormalizedSize SIZE_1080P = new NormalizedSize(1920, 1080);

    private static class NormalizedSize {
        final int shortSide;
        final int longSide;
        final int width;
        final int height;

        NormalizedSize(int width, int height) {
            this.width = width;
            this.height = height;
            longSide = Math.max(width, height);
            shortSide = Math.min(width, height);
        }
    }

    //region Public API

    /**
     * Constructor
     * @param context Context
     * @param cameraLocation Location of the camera to use
     * @param imageIterator Image iterator – reads images from the camera and provides them to a session
     * @param videoRecorder Video recorder to use to record a video of the session (optional)
     * @since 2.0.0
     */
    @Keep
    public CameraWrapper(@NonNull Context context, @NonNull CameraLocation cameraLocation, @NonNull IImageIterator imageIterator, @Nullable ISessionVideoRecorder videoRecorder) {
        contextWeakReference = new WeakReference<>(context.getApplicationContext());
        this.cameraLocation = cameraLocation;
        this.imageIterator = imageIterator;
        this.videoRecorder = videoRecorder;
        if (context instanceof LifecycleOwner) {
            if (((LifecycleOwner)context).getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                throw new IllegalStateException();
            }
            ((LifecycleOwner)context).getLifecycle().addObserver(this);
        }
    }

    /**
     * Set a surface on which to render the camera preview
     * @param surface Surface on which to render the camera preview
     * @since 2.0.0
     */
    @Keep
    public void setPreviewSurface(Surface surface) {
        surfaceRef.set(surface);
    }

    /**
     * Set preview class – will be used to determine the available preview sizes
     * @param previewClass Preview class
     * @since 2.0.0
     */
    @Keep
    public void setPreviewClass(Class<?> previewClass) {
        this.previewClass = previewClass;
    }

    /**
     * Add a listener
     * @param listener Listener to add
     * @since 2.0.0
     */
    @Keep
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener
     * @param listener Listener to remove
     * @since 2.0.0
     */
    @Keep
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Open the camera nad start a preview
     * @param width Width of the view in which the camera preview will be rendered
     * @param height Height of the view in which the camera preview will be rendered
     * @param displayRotation Display rotation of the view in which the camera preview will be rendered
     * @since 2.0.0
     */
    @Keep
    public void start(int width, int height, int displayRotation) {
        if (!isStarted.compareAndSet(false, true)) {
            return;
        }
        Log.v("Starting camera");
        startBackgroundThread();
        this.viewWidth.set(width);
        this.viewHeight.set(height);
        this.displayRotation.set(displayRotation);
        runInBackground(new StartRunnable(this, width, height, displayRotation));
    }

    /**
     * Close the camera
     * @since 2.0.0
     */
    @Keep
    public void stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return;
        }
        Log.v("Stopping camera");
        isCameraOpen.set(false);
        try {
            if (cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                Log.v("Acquired camera lock");
                if (imageIterator != null) {
                    imageIterator.deactivate();
                    imageIterator = null;
                }
                CameraDevice camera = cameraDevice.getAndSet(null);
                if (camera != null) {
                    if (cameraCaptureSession != null) {
                        Log.v("Closing camera capture session");
                        try {
                            cameraCaptureSession.stopRepeating();
                        } catch (CameraAccessException ignore) {
                        }
                        cameraCaptureSession.close();
                        Log.v("Closed camera capture session");
                        cameraCaptureSession = null;
                    }
                    Log.v("Closing camera");
                    camera.close();
                    Log.v("Closed camera");
                }
                if (imageReader != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        imageReader.discardFreeBuffers();
                    }
                    imageReader.setOnImageAvailableListener(null, null);
                    if (imageReader.getSurface() != null) {
                        imageReader.getSurface().release();
                    }
                    Log.v("Closing image reader");
                    imageReader.close();
                    Log.v("Closed image reader");
                    imageReader = null;
                }
                Surface previewSurface = surfaceRef.get();
                if (previewSurface != null) {
                    Log.v("Releasing preview surface");
                    previewSurface.release();
                    Log.v("Released preview surface");
                    surfaceRef.set(null);
                }
                cameraOpenCloseLock.release();
                Log.v("Released camera lock");
            }
        } catch (InterruptedException e) {
            Log.e("Exception when stopping camera", e);
        } finally {
            stopBackgroundThread();
        }
        Log.d("Camera stopped");
    }

    /**
     * Get image iterator
     * @return Image iterator used to read images from the camera
     * @since 2.0.0
     */
    @Keep
    public IImageIterator getImageIterator() {
        return imageIterator;
    }

    //endregion

    public void setCapturedImageMinimumArea(int area) {
        cameraPreviewHelper.setMinImageArea(area);
    }

    public int getCapturedImageMinimumArea() {
        return cameraPreviewHelper.getMinImageArea();
    }

    private void onError(VerIDSessionException exception) {
        runOnMainThread(new OnErrorRunnable(this, exception));
    }

    private void runInBackground(Runnable runnable) {
        if (backgroundExecutor != null) {
            backgroundExecutor.execute(runnable);
        }
    }

    private void runOnMainThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private static class OnErrorRunnable implements Runnable {

        private final WeakReference<CameraWrapper> cameraWrapperRef;
        private final WeakReference<VerIDSessionException> exception;

        OnErrorRunnable(CameraWrapper cameraWrapper, VerIDSessionException exception) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
            this.exception = new WeakReference<>(exception);
        }

        @Override
        public void run() {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            VerIDSessionException exception = this.exception.get();
            if (cameraWrapper == null || exception == null) {
                return;
            }
            for (Listener listener : cameraWrapper.listeners) {
                listener.onCameraError(exception);
            }
        }
    }

    private static class OnPreviewSizeRunnable implements Runnable {

        private final WeakReference<CameraWrapper> cameraWrapperRef;
        private final int width;
        private final int height;
        private final int sensorOrientation;

        OnPreviewSizeRunnable(CameraWrapper cameraWrapper, int width, int height, int sensorOrientation) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
            this.width = width;
            this.height = height;
            this.sensorOrientation = sensorOrientation;
        }

        @Override
        public void run() {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            for (Listener listener : cameraWrapper.listeners) {
                listener.onCameraPreviewSize(width, height, sensorOrientation);
            }
        }
    }

    private static class StartRunnable implements Runnable {

        private final WeakReference<CameraWrapper> cameraWrapperRef;
        private final int width;
        private final int height;
        private final int displayRotation;

        StartRunnable(CameraWrapper cameraWrapper, int width, int height, int displayRotation) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
            this.width = width;
            this.height = height;
            this.displayRotation = displayRotation;
        }

        @Override
        public void run() {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            try {
                Context context = cameraWrapper.getContext().orElseThrow(() -> new Exception("Activity unavailable"));
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    cameraWrapper.onError(new VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED));
                    return;
                }
                CameraManager manager = cameraWrapper.getCameraManager();
                if (!cameraWrapper.cameraOpenCloseLock.tryAcquire(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Time out waiting to acquire camera lock.");
                }
                String[] cameras = manager.getCameraIdList();
                cameraWrapper.cameraId.set(null);
                int requestedLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
                if (cameraWrapper.cameraLocation == CameraLocation.BACK) {
                    requestedLensFacing = CameraCharacteristics.LENS_FACING_BACK;
                }
                for (String camId : cameras) {
                    Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing != null && lensFacing == requestedLensFacing) {
                        cameraWrapper.cameraId.set(camId);
                        break;
                    }
                }
                if (cameraWrapper.cameraId.get() == null) {
                    for (String camId : cameras) {
                        Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                        if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                            cameraWrapper.cameraId.set(camId);
                            break;
                        }
                    }
                    if (cameraWrapper.cameraId.get() == null) {
                        throw new Exception("Camera not available");
                    }
                }
//                if (manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) == CameraMetadata.IN

                // Choose the sizes for camera preview and video recording
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraWrapper.cameraId.get());
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Integer cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                int sensorOrientation = (360 - (cameraOrientation != null ? cameraOrientation : 0)) % 360;
                if (map == null) {
                    throw new Exception("Cannot get video sizes");
                }
                int rotation = (360 - (sensorOrientation - displayRotation)) % 360;

                Size[] yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                Size[] previewSizes = map.getOutputSizes(cameraWrapper.previewClass);
                Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
                Size[] sizes = cameraWrapper.cameraPreviewHelper.getOutputSizes(previewSizes, yuvSizes, videoSizes, width, height, sensorOrientation, displayRotation);
                Size previewSize = sizes[0];

                cameraWrapper.imageReader = ImageReader.newInstance(sizes[1].getWidth(), sizes[1].getHeight(), ImageFormat.YUV_420_888, 2);
                cameraWrapper.imageIterator.setExifOrientation(cameraWrapper.getExifOrientation(rotation));

                cameraWrapper.getSessionVideoRecorder().ifPresent(videoRecorder -> {
                    Size videoSize = sizes[2];
                    videoRecorder.setup(videoSize, rotation);
                });
                cameraWrapper.runOnMainThread(new OnPreviewSizeRunnable(cameraWrapper, previewSize.getWidth(), previewSize.getHeight(), sensorOrientation));
                if (cameraWrapper.isCameraOpen.compareAndSet(false, true)) {
                    manager.openCamera(cameraWrapper.cameraId.get(), new CameraDeviceStateCallback(cameraWrapper), cameraWrapper.cameraProcessingHandler);
                } else {
                    cameraWrapper.cameraOpenCloseLock.release();
                }
            } catch (Exception e) {
                cameraWrapper.cameraOpenCloseLock.release();
                cameraWrapper.onError(new VerIDSessionException(e));
            }
        }
    }

    private static class CameraDeviceStateCallback extends CameraDevice.StateCallback {

        private final WeakReference<CameraWrapper> cameraWrapperRef;

        CameraDeviceStateCallback(CameraWrapper cameraWrapper) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            cameraWrapper.cameraDevice.set(cameraDevice);
            cameraWrapper.cameraOpenCloseLock.release();
            cameraWrapper.startPreview();
            Log.d("Camera opened");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            cameraWrapper.getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::stop);
            cameraDevice.close();
            cameraWrapper.cameraDevice.set(null);
            cameraWrapper.cameraId.set(null);
            cameraWrapper.cameraOpenCloseLock.release();
            cameraWrapper.isCameraOpen.set(false);
            cameraWrapper.videoRecorder = null;
            cameraWrapper.imageIterator = null;
            Log.d("Camera disconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            cameraWrapper.getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::stop);
            cameraDevice.close();
            cameraWrapper.cameraDevice.set(null);
            cameraWrapper.cameraId.set(null);
            cameraWrapper.cameraOpenCloseLock.release();
            cameraWrapper.isCameraOpen.set(false);
            String message = switch (error) {
                case ERROR_CAMERA_IN_USE -> "Camera in use";
                case ERROR_MAX_CAMERAS_IN_USE -> "Too many other open camera devices";
                case ERROR_CAMERA_DISABLED -> "Camera disabled";
                case ERROR_CAMERA_DEVICE -> "Camera failed";
                case ERROR_CAMERA_SERVICE -> "Camera service failed";
                default -> "";
            };
            cameraWrapper.videoRecorder = null;
            cameraWrapper.imageIterator = null;
            cameraWrapper.onError(new VerIDSessionException(new Exception("Failed to open camera: "+message)));
            Log.e("Camera error code "+error);
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.v("CameraDevice onClosed");
            super.onClosed(camera);
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            cameraWrapper.cameraDevice.set(null);
            cameraWrapper.cameraId.set(null);
            Log.d("Camera closed");
        }
    };

    private static class CaptureSessionStateCallback extends CameraCaptureSession.StateCallback {

        private final WeakReference<CameraWrapper> cameraWrapperRef;
        private final WeakReference<CaptureRequest.Builder> previewBuilderRef;

        CaptureSessionStateCallback(CameraWrapper cameraWrapper, CaptureRequest.Builder previewBuilder) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
            previewBuilderRef = new WeakReference<>(previewBuilder);
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            CaptureRequest.Builder previewBuilder = previewBuilderRef.get();
            if (cameraWrapper == null || previewBuilder == null) {
                return;
            }
            cameraWrapper.cameraCaptureSession = session;
            if (cameraWrapper.isCameraOpen.get() && (!cameraWrapper.getLifecycleOwner().isPresent() || cameraWrapper.getLifecycleOwner().map(LifecycleOwner::getLifecycle).map(Lifecycle::getCurrentState).map(state -> state.isAtLeast(Lifecycle.State.STARTED)).orElse(false))) {
                try {
                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    session.setRepeatingRequest(previewBuilder.build(), null, cameraWrapper.cameraPreviewHandler);
                    cameraWrapper.getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::start);
                } catch (CameraAccessException e) {
                    cameraWrapper.onError(new VerIDSessionException(e));
                }
            } else {
                cameraWrapper.onError(new VerIDSessionException(new Exception("Failed to configure camera")));
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            cameraWrapper.onError(new VerIDSessionException(new Exception("Failed to start preview")));
        }
    }

    private static class StartCameraPreviewBackgroundRunnable implements Runnable {

        private final WeakReference<CameraWrapper> cameraWrapperRef;

        StartCameraPreviewBackgroundRunnable(CameraWrapper cameraWrapper) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
        }

        @Override
        public void run() {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            try {
                if (cameraWrapper.cameraDevice.get() == null) {
                    throw new VerIDSessionException(new Exception("Camera unavailable"));
                }
                if (!cameraWrapper.cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                    throw new Exception("Failed to acquire camera");
                }
                ArrayList<Surface> surfaces = new ArrayList<>();
                CaptureRequest.Builder previewBuilder = cameraWrapper.cameraDevice.get().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                Surface previewSurface = cameraWrapper.surfaceRef.get();
                if (previewSurface != null && previewSurface.isValid()) {
                    previewBuilder.addTarget(previewSurface);
                    surfaces.add(previewSurface);
                } else {
                    throw new VerIDSessionException(new Exception("Preview surface unavailable"));
                }

                if (cameraWrapper.imageReader != null) {
                    cameraWrapper.imageReader.setOnImageAvailableListener(cameraWrapper.imageIterator, cameraWrapper.cameraProcessingHandler);
                    previewBuilder.addTarget(cameraWrapper.imageReader.getSurface());
                    surfaces.add(cameraWrapper.imageReader.getSurface());
                } else {
                    throw new VerIDSessionException(new Exception("Image reader unavailable"));
                }

                cameraWrapper.getSessionVideoRecorder().flatMap(ISessionVideoRecorder::getSurface).ifPresent(surface -> {
                    previewBuilder.addTarget(surface);
                    surfaces.add(surface);
                });

                cameraWrapper.cameraDevice.get().createCaptureSession(surfaces, new CaptureSessionStateCallback(cameraWrapper, previewBuilder), cameraWrapper.cameraProcessingHandler);
            } catch (Exception e) {
                cameraWrapper.onError(new VerIDSessionException(e));
            } finally {
                cameraWrapper.cameraOpenCloseLock.release();
            }
        }
    }

    private static class StartCameraPreviewRunnable implements Runnable {

        private final WeakReference<CameraWrapper> cameraWrapperRef;

        StartCameraPreviewRunnable(CameraWrapper cameraWrapper) {
            cameraWrapperRef = new WeakReference<>(cameraWrapper);
        }

        @Override
        public void run() {
            CameraWrapper cameraWrapper = cameraWrapperRef.get();
            if (cameraWrapper == null) {
                return;
            }
            if (!cameraWrapper.getLifecycleOwner().map(LifecycleOwner::getLifecycle).map(Lifecycle::getCurrentState).map(state -> state.isAtLeast(Lifecycle.State.STARTED)).orElse(true)) {
                cameraWrapper.onError(new VerIDSessionException(new Exception("CameraWrapper requires a started activity")));
                return;
            }
            cameraWrapper.runInBackground(new StartCameraPreviewBackgroundRunnable(cameraWrapper));
        }
    }

    private void startPreview() {
        runOnMainThread(new StartCameraPreviewRunnable(this));
    }

    private Optional<Context> getContext() {
        return Optional.ofNullable(contextWeakReference.get());
    }

    private Optional<LifecycleOwner> getLifecycleOwner() {
        return getContext().flatMap(context -> {
            if (context instanceof LifecycleOwner) {
                return Optional.of((LifecycleOwner)context);
            } else {
                return Optional.empty();
            }
        });
    }

    private Optional<ISessionVideoRecorder> getSessionVideoRecorder() {
        return Optional.ofNullable(videoRecorder);
    }

    private CameraManager getCameraManager() throws Exception {
        if (cameraManager == null) {
            Context context = getContext().orElseThrow(() -> new Exception("Context unavailable"));
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                throw new Exception("Camera manager unavailable");
            }
        }
        return cameraManager;
    }

    private @ExifOrientation int getExifOrientation(int rotationDegrees) {
        int exifOrientation;
        switch (rotationDegrees) {
            case 90:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
                break;
            case 180:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
                break;
            case 270:
                exifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
                break;
            default:
                exifOrientation = ExifInterface.ORIENTATION_NORMAL;
        }
        if (cameraLocation == CameraLocation.FRONT) {
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    exifOrientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    exifOrientation = ExifInterface.ORIENTATION_TRANSVERSE;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    exifOrientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    exifOrientation = ExifInterface.ORIENTATION_TRANSPOSE;
                    break;
            }
        }
        return exifOrientation;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        cameraProcessingThread = new HandlerThread("Ver-ID session activity");
        cameraProcessingThread.start();
        cameraProcessingHandler = new Handler(cameraProcessingThread.getLooper());
        cameraPreviewThread = new HandlerThread("Ver-ID camera preview");
        cameraPreviewThread.start();
        cameraPreviewHandler = new Handler(cameraPreviewThread.getLooper());
        backgroundExecutor = Executors.newSingleThreadExecutor();
        Log.d("Started background threads");
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        Log.v("Stopping background threads");
        try {
            if (cameraProcessingHandler != null) {
                cameraProcessingHandler.removeCallbacksAndMessages(null);
            }
            if (cameraProcessingThread != null) {
                cameraProcessingThread.quitSafely();
                cameraProcessingThread.join(500);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            cameraProcessingThread = null;
            cameraProcessingHandler = null;
        }
        try {
            if (cameraPreviewHandler != null) {
                cameraPreviewHandler.removeCallbacksAndMessages(null);
            }
            if (cameraPreviewThread != null) {
                cameraPreviewThread.quitSafely();
                cameraPreviewThread.join(500);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            cameraPreviewThread = null;
            cameraPreviewHandler = null;
        }
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        backgroundExecutor = null;
        Log.v("Stopped background threads");
    }

    //region Lifecycle callbacks

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        Log.v("CameraWrapper onResume");
        if (viewWidth.get() > 0 && viewHeight.get() > 0) {
            start(viewWidth.get(), viewHeight.get(), displayRotation.get());
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        Log.v("CameraWrapper onPause");
        stop();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        Log.v("CameraWrapper onDestroy");
        owner.getLifecycle().removeObserver(this);
        cameraDevice.set(null);
        contextWeakReference.clear();
        listeners.clear();
    }

    //endregion
}
