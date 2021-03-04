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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.appliedrec.verid.core2.session.IImageIterator;
import com.appliedrec.verid.core2.session.VerIDSessionException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
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
    private final IImageIterator imageIterator;
    private final AtomicReference<String> cameraId = new AtomicReference<>();
    private ImageReader imageReader;
    private final ISessionVideoRecorder videoRecorder;
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
        contextWeakReference = new WeakReference<>(context);
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
        startBackgroundThread();
        this.viewWidth.set(width);
        this.viewHeight.set(height);
        this.displayRotation.set(displayRotation);
        runInBackground(() -> {
            try {
                Context context = getContext().orElseThrow(() -> new Exception("Activity unavailable"));
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                CameraManager manager = getCameraManager();
                if (!cameraOpenCloseLock.tryAcquire(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Time out waiting to acquire camera lock.");
                }
                String[] cameras = manager.getCameraIdList();
                cameraId.set(null);
                int requestedLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
                if (cameraLocation == CameraLocation.BACK) {
                    requestedLensFacing = CameraCharacteristics.LENS_FACING_BACK;
                }
                for (String camId : cameras) {
                    Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing != null && lensFacing == requestedLensFacing) {
                        cameraId.set(camId);
                        break;
                    }
                }
                if (cameraId.get() == null) {
                    for (String camId : cameras) {
                        Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                        if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                            cameraId.set(camId);
                            break;
                        }
                    }
                    if (cameraId.get() == null) {
                        throw new Exception("Camera not available");
                    }
                }
//                if (manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) == CameraMetadata.IN

                // Choose the sizes for camera preview and video recording
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId.get());
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Integer cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                int sensorOrientation = (360 - (cameraOrientation != null ? cameraOrientation : 0)) % 360;
                if (map == null) {
                    throw new Exception("Cannot get video sizes");
                }
                int rotation = (360 - (sensorOrientation - displayRotation)) % 360;

                float aspectRatio = sensorOrientation % 180 == 0 ? 3f/4f : 4f/3f;

                Size[] yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                Size[] previewSizes = map.getOutputSizes(previewClass);
                Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
                Size[] sizes = getOutputSizes(previewSizes, yuvSizes, videoSizes, aspectRatio);
                Size previewSize = sizes[0];

                imageReader = ImageReader.newInstance(sizes[1].getWidth(), sizes[1].getHeight(), ImageFormat.YUV_420_888, 2);
                imageIterator.setExifOrientation(getExifOrientation(rotation));

                getSessionVideoRecorder().ifPresent(videoRecorder -> {
                    Size videoSize = sizes[2];
                    videoRecorder.setup(videoSize, rotation);
                });
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (Listener listener : listeners) {
                        listener.onCameraPreviewSize(previewSize.getWidth(), previewSize.getHeight(), sensorOrientation);
                    }
                });
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    onError(new VerIDSessionException(new Exception("Missing camera permission")));
                    return;
                }
                if (isCameraOpen.compareAndSet(false, true)) {
                    manager.openCamera(cameraId.get(), stateCallback, cameraProcessingHandler);
                }
            } catch (Exception e) {
                onError(new VerIDSessionException(e));
            } finally {
                cameraOpenCloseLock.release();
            }
        });
    }

    /**
     * Close the camera
     * @since 2.0.0
     */
    @Keep
    public void stop() {
        isCameraOpen.set(false);
        try {
            if (imageReader != null) {
                imageReader.close();
            }
            if (cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                CameraDevice camera = cameraDevice.getAndSet(null);
                if (camera != null) {
                    camera.close();
                }
                cameraOpenCloseLock.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            stopBackgroundThread();
        }
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

    private void onError(VerIDSessionException exception) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (Listener listener : listeners) {
                listener.onCameraError(exception);
            }
        });
    }

    private void runInBackground(Runnable runnable) {
        if (backgroundExecutor != null) {
            backgroundExecutor.execute(runnable);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            CameraWrapper.this.cameraDevice.set(cameraDevice);
            cameraOpenCloseLock.release();
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            isCameraOpen.set(false);
            getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::stop);
            cameraDevice.close();
            CameraWrapper.this.cameraDevice.set(null);
            cameraId.set(null);
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            isCameraOpen.set(false);
            getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::stop);
            cameraDevice.close();
            CameraWrapper.this.cameraDevice.set(null);
            cameraId.set(null);
            String message;
            switch (error) {
                case ERROR_CAMERA_IN_USE:
                    message = "Camera in use";
                    break;
                case ERROR_MAX_CAMERAS_IN_USE:
                    message = "Too many other open camera devices";
                    break;
                case ERROR_CAMERA_DISABLED:
                    message = "Camera disabled";
                    break;
                case ERROR_CAMERA_DEVICE:
                    message = "Camera failed";
                    break;
                case ERROR_CAMERA_SERVICE:
                    message = "Camera service failed";
                    break;
                default:
                    message = "";
            }
            CameraWrapper.this.onError(new VerIDSessionException(new Exception("Failed to open camera: "+message)));
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            cameraDevice.set(null);
            cameraId.set(null);
        }
    };

    private void startPreview() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!getLifecycleOwner().isPresent() || !getLifecycleOwner().get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                onError(new VerIDSessionException(new Exception("CameraWrapper requires a started activity")));
                return;
            }
            runInBackground(() -> {
                try {
                    if (cameraDevice.get() == null) {
                        throw new VerIDSessionException(new Exception("Camera unavailable"));
                    }
                    if (!cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                        throw new Exception("Failed to acquire camera");
                    }
                    ArrayList<Surface> surfaces = new ArrayList<>();
                    CaptureRequest.Builder previewBuilder = cameraDevice.get().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                    Surface previewSurface = surfaceRef.get();
                    if (previewSurface != null) {
                        previewBuilder.addTarget(previewSurface);
                        surfaces.add(previewSurface);
                    }

                    imageReader.setOnImageAvailableListener(imageIterator, cameraProcessingHandler);
                    previewBuilder.addTarget(imageReader.getSurface());
                    surfaces.add(imageReader.getSurface());

                    getSessionVideoRecorder().flatMap(ISessionVideoRecorder::getSurface).ifPresent(surface -> {
                        previewBuilder.addTarget(surface);
                        surfaces.add(surface);
                    });

                    cameraDevice.get().createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (isCameraOpen.get() && getLifecycleOwner().map(LifecycleOwner::getLifecycle).map(Lifecycle::getCurrentState).map(state -> state.isAtLeast(Lifecycle.State.STARTED)).orElse(false)) {
                                try {
                                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                    session.setRepeatingRequest(previewBuilder.build(), null, cameraPreviewHandler);
                                } catch (CameraAccessException e) {
                                    onError(new VerIDSessionException(e));
                                }
                                getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::start);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            onError(new VerIDSessionException(new Exception("Failed to start preview")));
                        }

                    }, cameraProcessingHandler);
                } catch (Exception e) {
                    onError(new VerIDSessionException(e));
                } finally {
                    cameraOpenCloseLock.release();
                }
            });
        });
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

    private Size[] getOutputSizes(Size[] previewSizes, Size[] imageReaderSizes, Size[] videoSizes, float aspectRatio) {
        HashMap<Float,ArrayList<Size>> previewAspectRatios = getAspectRatioSizes(previewSizes);
        HashMap<Float,ArrayList<Size>> imageReaderAspectRatios = getAspectRatioSizes(imageReaderSizes);
        HashMap<Float,ArrayList<Size>> videoAspectRatios = getAspectRatioSizes(videoSizes);
        HashMap<Float,Size[]> candidates = new HashMap<>();
        Comparator<Size> sizeComparator = (lhs, rhs) -> lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight();
        for (Map.Entry<Float,ArrayList<Size>> entry : previewAspectRatios.entrySet()) {
            ArrayList<Size> imageReaderCandidates = getSizesMatchingAspectRatio(entry.getKey(), imageReaderAspectRatios);
            ArrayList<Size> videoCandidates = getSizesMatchingAspectRatio(entry.getKey(), videoAspectRatios);
            if (imageReaderCandidates.isEmpty() || videoCandidates.isEmpty()) {
                continue;
            }
            Size[] sizes = new Size[3];
            sizes[0] = Collections.max(entry.getValue(), sizeComparator);
            sizes[1] = Collections.min(imageReaderCandidates, sizeComparator);
            sizes[2] = Collections.min(videoCandidates, sizeComparator);
            candidates.put(entry.getKey(), sizes);
        }
        if (candidates.isEmpty()) {
            return new Size[]{previewSizes[0],imageReaderSizes[0],videoSizes[0]};
        }
        Map.Entry<Float,Size[]> bestEntry = Collections.min(candidates.entrySet(), (lhs, rhs) -> {
            float lhsAspectRatioDiff = Math.abs(lhs.getKey() - aspectRatio);
            float rhsAspectRatioDiff = Math.abs(rhs.getKey() - aspectRatio);
            return (int)(lhsAspectRatioDiff * 1000f - rhsAspectRatioDiff * 1000f);
        });
        return bestEntry.getValue();
    }

    private HashMap<Float,ArrayList<Size>> getAspectRatioSizes(Size[] sizes) {
        HashMap<Float,ArrayList<Size>> aspectRatios = new HashMap<>();
        for (Size size : sizes) {
            float aspectRatio = (float)size.getWidth()/(float)size.getHeight();
            if (!aspectRatios.containsKey(aspectRatio)) {
                aspectRatios.put(aspectRatio, new ArrayList<>());
            }
            //noinspection ConstantConditions
            aspectRatios.get(aspectRatio).add(size);
        }
        return aspectRatios;
    }

    private ArrayList<Size> getSizesMatchingAspectRatio(float aspectRatio, HashMap<Float,ArrayList<Size>> candidates) {
        float aspectRatioTolerance = 0.01f;
        ArrayList<Size> sizes = new ArrayList<>();
        for (Map.Entry<Float,ArrayList<Size>> entry : candidates.entrySet()) {
            if (Math.abs(entry.getKey() - aspectRatio) < aspectRatioTolerance) {
                sizes.addAll(entry.getValue());
            }
        }
        return sizes;
    }

    private @VerIDImageIterator.ExifOrientation int getExifOrientation(int rotationDegrees) {
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
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (cameraProcessingThread != null) {
            cameraProcessingThread.quit();
        }
        cameraProcessingThread = null;
        cameraProcessingHandler = null;
        if (cameraPreviewThread != null) {
            cameraPreviewThread.quit();
        }
        cameraPreviewThread = null;
        cameraPreviewHandler = null;
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        backgroundExecutor = null;
    }

    //region Lifecycle callbacks

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (viewWidth.get() > 0 && viewHeight.get() > 0) {
            start(viewWidth.get(), viewHeight.get(), displayRotation.get());
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        stop();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
    }

    //endregion
}
