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
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class CameraWrapper implements DefaultLifecycleObserver {

    interface Listener {
        void onPreviewSize(int width, int height, int sensorOrientation);
    }

    private final WeakReference<AppCompatActivity> activityWeakReference;
    private final CameraLocation cameraLocation;
    private final VerIDImageAnalyzer imageAnalyzer;
    private Surface previewSurface;
    private String cameraId;
    private ImageReader imageReader;
    private final ISessionVideoRecorder videoRecorder;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private Listener listener;
    private CameraDevice cameraDevice;
    private HandlerThread cameraProcessingThread;
    private Handler cameraProcessingHandler;
    private ExecutorService backgroundExecutor;
    private HandlerThread cameraPreviewThread;
    private Handler cameraPreviewHandler;
    private Class<?> previewSurfaceClass;

    CameraWrapper(@NonNull AppCompatActivity activity, @NonNull CameraLocation cameraLocation, @NonNull VerIDImageAnalyzer imageAnalyzer, @Nullable ISessionVideoRecorder videoRecorder) {
        activityWeakReference = new WeakReference<>(activity);
        this.cameraLocation = cameraLocation;
        this.imageAnalyzer = imageAnalyzer;
        this.videoRecorder = videoRecorder;
        if (activity.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            throw new IllegalStateException();
        }
        activity.getLifecycle().addObserver(this);
    }

    void setPreviewSurface(Surface surface, Class<?> surfaceClass) {
        previewSurface = surface;
        previewSurfaceClass = surfaceClass;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void start(int width, int height, int displayRotation) {
        startBackgroundThread();
        getActivity().ifPresent(context -> {
            runInBackground(() -> {
                try {
                    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                    if (manager == null) {
                        throw new Exception("Camera manager unavailable");
                    }
                    if (!cameraOpenCloseLock.tryAcquire(10, TimeUnit.SECONDS)) {
                        throw new TimeoutException("Time out waiting to acquire camera lock.");
                    }
                    String[] cameras = manager.getCameraIdList();
                    cameraId = null;
                    int requestedLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
                    if (cameraLocation == CameraLocation.BACK) {
                        requestedLensFacing = CameraCharacteristics.LENS_FACING_BACK;
                    }
                    for (String camId : cameras) {
                        Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                        if (lensFacing != null && lensFacing == requestedLensFacing) {
                            cameraId = camId;
                            break;
                        }
                    }
                    if (cameraId == null) {
                        for (String camId : cameras) {
                            Integer lensFacing = manager.getCameraCharacteristics(camId).get(CameraCharacteristics.LENS_FACING);
                            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                                cameraId = camId;
                                break;
                            }
                        }
                        if (cameraId == null) {
                            throw new Exception("Camera not available");
                        }
                    }

                    // Choose the sizes for camera preview and video recording
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Integer cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    int sensorOrientation = (360 - (cameraOrientation != null ? cameraOrientation : 0)) % 360;
                    if (map == null) {
                        throw new Exception("Cannot get video sizes");
                    }
                    int rotation = (360 - (sensorOrientation - displayRotation)) % 360;

                    float desiredAspectRatio;
                    if (rotation % 180 == 0) {
                        desiredAspectRatio = (float)width/(float)height;
                    } else {
                        desiredAspectRatio = (float)height/(float)width;
                    }

                    Size[] yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                    Size[] previewSizes = map.getOutputSizes(previewSurfaceClass);
                    Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
                    Size[] sizes = getOutputSizes(previewSizes, yuvSizes, videoSizes, desiredAspectRatio);
                    Size previewSize = sizes[0];

                    imageReader = ImageReader.newInstance(sizes[1].getWidth(), sizes[1].getHeight(), ImageFormat.YUV_420_888, 2);
                    imageAnalyzer.setExifOrientation(getExifOrientation(rotation));

                    getSessionVideoRecorder().ifPresent(videoRecorder -> {
                        Size videoSize = sizes[2];
                        videoRecorder.setup(videoSize, rotation);
                    });
                    getListener().ifPresent(listener -> {
                        listener.onPreviewSize(previewSize.getWidth(), previewSize.getHeight(), sensorOrientation);
                    });
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        imageAnalyzer.fail(new Exception("Missing camera permission"));
                        return;
                    }
                    manager.openCamera(cameraId, stateCallback, cameraProcessingHandler);
                } catch (Exception e) {
                    imageAnalyzer.fail(e);
                } finally {
                    cameraOpenCloseLock.release();
                }
            });
        });
    }

    void stop() {
        runInBackground(() -> {
            try {
                if (cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                    if (imageReader != null) {
                        imageReader.close();
                    }
                    if (cameraDevice != null) {
                        cameraDevice.close();
                    }
                    cameraOpenCloseLock.release();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                stopBackgroundThread();
            }
        });
    }

    Optional<Listener> getListener() {
        return Optional.ofNullable(listener);
    }

    private void runInBackground(Runnable runnable) {
        if (backgroundExecutor != null) {
            backgroundExecutor.execute(runnable);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            CameraWrapper.this.cameraDevice = cameraDevice;
            cameraOpenCloseLock.release();
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::stop);
            cameraDevice.close();
            CameraWrapper.this.cameraDevice = null;
            cameraId = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::stop);
            cameraDevice.close();
            CameraWrapper.this.cameraDevice = null;
            cameraId = null;
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
            imageAnalyzer.fail(new Exception("Failed to open camera: "+message));
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            super.onClosed(camera);
            cameraDevice = null;
            cameraId = null;
        }
    };

    private void startPreview() {
        if (null == cameraDevice || !getActivity().isPresent() || !getActivity().get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            return;
        }
        try {
            if (!cameraOpenCloseLock.tryAcquire(3, TimeUnit.SECONDS)) {
                return;
            }
            ArrayList<Surface> surfaces = new ArrayList<>();
            CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            if (previewSurface != null) {
                previewBuilder.addTarget(previewSurface);
                surfaces.add(previewSurface);
            }

            imageReader.setOnImageAvailableListener(imageAnalyzer, cameraProcessingHandler);
            previewBuilder.addTarget(imageReader.getSurface());
            surfaces.add(imageReader.getSurface());

            getSessionVideoRecorder().flatMap(ISessionVideoRecorder::getSurface).ifPresent(surface -> {
                previewBuilder.addTarget(surface);
                surfaces.add(surface);
            });

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        if (getActivity().isPresent() && getActivity().get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                            try {
                                previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(previewBuilder.build(), null, cameraPreviewHandler);
                            } catch (CameraAccessException e) {
                                imageAnalyzer.fail(e);
                            }
                            getSessionVideoRecorder().ifPresent(ISessionVideoRecorder::start);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        imageAnalyzer.fail(new Exception("Failed to start preview"));
                    }

                }, cameraProcessingHandler);
        } catch (CameraAccessException | InterruptedException e) {
            imageAnalyzer.fail(e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private Optional<AppCompatActivity> getActivity() {
        return Optional.ofNullable(activityWeakReference.get());
    }

    private Optional<ISessionVideoRecorder> getSessionVideoRecorder() {
        return Optional.ofNullable(videoRecorder);
    }

    private Size[] getOutputSizes(Size[] previewSizes, Size[] imageReaderSizes, Size[] videoSizes, float preferredAspectRatio) {
        HashMap<Float,ArrayList<Size>> previewAspectRatios = getAspectRatioSizes(previewSizes);
        HashMap<Float,ArrayList<Size>> imageReaderAspectRatios = getAspectRatioSizes(imageReaderSizes);
        HashMap<Float,ArrayList<Size>> videoAspectRatios = getAspectRatioSizes(videoSizes);
        HashMap<Float,Size[]> candidates = new HashMap<>();
        Comparator<Size> sizeComparator = (lhs, rhs) -> lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight();
        for (float ratio : previewAspectRatios.keySet()) {
            ArrayList<Size> imageReaderCandidates = getSizesMatchingAspectRatio(ratio, imageReaderAspectRatios);
            ArrayList<Size> videoCandidates = getSizesMatchingAspectRatio(ratio, videoAspectRatios);
            if (imageReaderCandidates.isEmpty() || videoCandidates.isEmpty()) {
                continue;
            }
            Size[] sizes = new Size[3];
            sizes[0] = Collections.max(previewAspectRatios.get(ratio), sizeComparator);
            sizes[1] = Collections.min(imageReaderCandidates, sizeComparator);
            sizes[2] = Collections.min(videoCandidates, sizeComparator);
            candidates.put(ratio, sizes);
        }
        if (candidates.isEmpty()) {
            return new Size[]{previewSizes[0],imageReaderSizes[0],videoSizes[0]};
        }
        float ratio = Collections.min(candidates.keySet(), (lhs, rhs) -> Math.abs(lhs - preferredAspectRatio) < Math.abs(rhs - preferredAspectRatio) ? -1 : 1);
        return candidates.get(ratio);
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
        for (float ratio : candidates.keySet()) {
            if (Math.abs(ratio - aspectRatio) < aspectRatioTolerance) {
                sizes.addAll(candidates.get(ratio));
            }
        }
        return sizes;
    }

    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, (lhs, rhs) -> Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight()));
        } else {
            return choices[0];
        }
    }


    private Comparator<Size> videoSizeComparator = new Comparator<Size>() {

        private boolean is4x3(Size size) {
            return size.getWidth() == size.getHeight() * 4 / 3;
        }
        @Override
        public int compare(Size s1, Size s2) {
            if (is4x3(s1) != is4x3(s2)) {
                return is4x3(s1) ? -1 : 1;
            }
            return Math.abs(s1.getWidth()-640) < Math.abs(s2.getWidth()-640) ? -1 : 1;
        }
    };

    private Size chooseVideoSize(Size[] choices) {
        ArrayList<Size> sizes = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                sizes.add(size);
            }
        }
        if (sizes.isEmpty()) {
            return choices[choices.length-1];
        }
        Collections.sort(sizes, videoSizeComparator);
        return sizes.get(0);
    }

    private @VerIDImageAnalyzer.ExifOrientation int getExifOrientation(int rotationDegrees) {
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
        cameraProcessingHandler = new Handler(cameraPreviewThread.getLooper());
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

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        stop();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
    }
}
