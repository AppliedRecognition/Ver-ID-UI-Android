package com.appliedrec.verid.ui2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.appliedrec.verid.core2.session.VerIDSessionException
import com.appliedrec.verid.core2.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull

class CameraWrapper<T>(
    context: T, val cameraLocation: CameraLocation, val onImageAvailableListener: OnImageAvailableListener, val exifOrientation: AtomicInteger, val isMirrored: AtomicBoolean, val videoRecorder: ISessionVideoRecorder?
) : DefaultLifecycleObserver where T : Context, T : LifecycleOwner {

    interface Listener {
        fun onCameraPreviewSize(width: Int, height: Int, sensorOrientation: Int)
        fun onCameraError(error: VerIDSessionException)
        fun onCameraStarted()
        fun onCameraStopped()
    }

    private class CaptureSessionStateCallback<T>(
        cameraWrapper: CameraWrapper<T>,
        previewBuilder: CaptureRequest.Builder
    ) : CameraCaptureSession.StateCallback() where T : Context, T : LifecycleOwner {

        val cameraWrapperRef = WeakReference(cameraWrapper)
        val previewBuilderRef = WeakReference(previewBuilder)

        override fun onConfigured(session: CameraCaptureSession) {
            Log.d("Camera capture session configured")
            val cameraWrapper = cameraWrapperRef.get() ?: return
            val previewBuilder = previewBuilderRef.get() ?: return
            cameraWrapper.cameraCaptureSession.set(session)
            val lifecycle = cameraWrapper.contextRef.get()?.lifecycle ?: return
            try {
                if (cameraWrapper.isCameraOpen.get() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    previewBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    session.setRepeatingRequest(
                        previewBuilder.build(),
                        null,
                        cameraWrapper.cameraHandler
                    )
                    cameraWrapper.videoRecorder?.start()
                    cameraWrapper.contextRef.get()?.let { context ->
                        context.lifecycleScope.launch(Dispatchers.Main.immediate) {
                            cameraWrapper.listeners.forEach { it.onCameraStarted() }
                        }
                    }
                } else {
                    throw Exception("Failed to configure camera capture session")
                }
            } catch (e: Exception) {
                cameraWrapper.onError(e)
            }
        }

        override fun onConfigureFailed(p0: CameraCaptureSession) {
            Log.d("Camera capture session configuration failed")
            cameraWrapperRef.get()?.onError(Exception("Failed to start camera preview"))
        }

        override fun onClosed(session: CameraCaptureSession) {
            Log.d("Camera capture session closed")
            super.onClosed(session)
            val cameraWrapper = cameraWrapperRef.get() ?: return
            try {
                cameraWrapper.isCameraOpen.set(false)
                cameraWrapper.cameraDevice.getAndSet(null)?.close()
                cameraWrapper.imageReader.getAndSet(null)?.let { reader ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        reader.discardFreeBuffers()
                    }
                    reader.setOnImageAvailableListener(null, null)
                    reader.surface.release()
                    reader.close()
                }
                cameraWrapper.surfaceRef.getAndSet(null)?.release()
//                cameraWrapper.imageUtils?.close()
//                cameraWrapper.imageUtils = null
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                cameraWrapper.stopBackgroundHandlers()
                cameraWrapper.contextRef.get()?.let { context ->
                    context.lifecycleScope.launch(Dispatchers.Main.immediate) {
                        cameraWrapper.listeners.forEach {
                            it.onCameraStopped()
                        }
                    }
                }
            }
        }

    }

    private val contextRef: WeakReference<T> = WeakReference(context)
    private var surfaceRef: AtomicReference<Surface?> = AtomicReference(null)
    private var previewClass: Class<*>? = null
    private val listeners: MutableSet<CameraWrapper.Listener> = mutableSetOf()
    private val isStarted = AtomicBoolean(false)
    private val isCameraOpen = AtomicBoolean(false)
    private val viewWidth = AtomicInteger(0)
    private val viewHeight = AtomicInteger(0)
    private val displayRotation = AtomicInteger(0)
    private var cameraManager: CameraManager? = null
    private val cameraPreviewHelper = CameraPreviewHelper()
    private val imageReader = AtomicReference<ImageReader?>(null)
    private var cameraDevice = AtomicReference<CameraDevice?>(null)
    private var cameraCaptureSession = AtomicReference<CameraCaptureSession?>(null)
    private var cameraHandlerThread = HandlerThread("CameraHandler")
    private var cameraHandler: Handler = run {
        cameraHandlerThread.start()
        Handler(cameraHandlerThread.looper)
    }
    private val cameraExecutor: Executor
        get() = Executor { cameraHandler.post(it) }
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(p0: CameraDevice) {
            Log.d("Camera opened")
            isCameraOpen.set(true)
            cameraDevice.set(p0)
            startPreview()
        }

        override fun onDisconnected(p0: CameraDevice) {
            Log.d("Camera disconnected")
            isCameraOpen.set(false)
            cameraDevice.set(null)
        }

        override fun onError(p0: CameraDevice, p1: Int) {
            Log.d("Camera error")
            videoRecorder?.stop()
                p0.close()
                cameraDevice.set(null)
                isCameraOpen.set(false)
            val message = when (p1) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Too many other open camera devices"
                ERROR_CAMERA_DISABLED -> "Camera disabled"
                ERROR_CAMERA_DEVICE -> "Camera failed"
                ERROR_CAMERA_SERVICE -> "Camera service failed"
                else -> "Unknown error"
            }
            onError(VerIDSessionException(Exception("Failed to open camera: $message")))
        }
    }

//    val exifOrientation = AtomicInteger(ExifInterface.ORIENTATION_NORMAL)

    var capturedImageMinimumArea: Int
        get() = cameraPreviewHelper.minImageArea
        set(value) {
            cameraPreviewHelper.minImageArea = value
        }

    init {
        if (context.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            throw IllegalStateException()
        }
        if (context.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            createCameraManager()
        }
        context.lifecycle.addObserver(this)
    }

    fun setPreviewSurface(surface: Surface) {
        surfaceRef.set(surface)
    }

    fun setPreviewClass(pClass: Class<*>) {
        previewClass = pClass
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun start(width: Int, height: Int, displayRotation: Int) {
        Log.v("CameraWrapper.start(width: $width, height: $height, displayRotation: $displayRotation")
        if (!isStarted.compareAndSet(false, true)) {
            Log.v("CameraWrapper.start: returning – already started")
            return
        }
//        imageUtils = ImageUtils()
        startBackgroundHandlers()
        viewWidth.set(width)
        viewHeight.set(height)
        this.displayRotation.set(displayRotation)
        val context = contextRef.get() ?: return
        context.lifecycleScope.launch(Dispatchers.Default) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                onError(VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED))
                return@launch
            }
            try {
                val cameraManager = this@CameraWrapper.cameraManager ?: throw IllegalStateException("Camera manager not available")
                val cameras = cameraManager.cameraIdList
                val requestedLensFacing =
                    if (cameraLocation == CameraLocation.BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
                val camId = cameras.firstOrNull { camId ->
                    val lensFacing = cameraManager.getCameraCharacteristics(camId)
                        .get(CameraCharacteristics.LENS_FACING)
                    lensFacing == requestedLensFacing
                } ?: cameras.firstOrNull { camId ->
                    val lensFacing = cameraManager.getCameraCharacteristics(camId)
                        .get(CameraCharacteristics.LENS_FACING)
                    lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
                } ?: throw Exception("Camera not available")
                val characteristics = cameraManager.getCameraCharacteristics(camId)
                val configMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: throw Exception("Scaler stream configuration map not available")
                val cameraOrientation =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val sensorOrientation = (360 - cameraOrientation) % 360
                val rotation = (360 - (sensorOrientation - displayRotation)) % 360;
                val yuvSizes = configMap.getOutputSizes(ImageFormat.YUV_420_888)
                val previewSizes = configMap.getOutputSizes(previewClass)
                val videoSizes = configMap.getOutputSizes(MediaRecorder::class.java)
                val sizes = cameraPreviewHelper.getOutputSizes(
                    previewSizes,
                    yuvSizes,
                    videoSizes,
                    width,
                    height,
                    sensorOrientation,
                    displayRotation
                )
                val previewSize = sizes[0]
                imageReader.set(ImageReader.newInstance(
                    sizes[1].width,
                    sizes[1].height,
                    ImageFormat.YUV_420_888,
                    2
                ))
                setRotation(rotation)
                videoRecorder?.let { recorder ->
                    val videoSize = sizes[2]
                    recorder.setup(videoSize, rotation)
                }
                withContext(Dispatchers.Main) {
                    for (listener in listeners) {
                        listener.onCameraPreviewSize(previewSize.width, previewSize.height, sensorOrientation)
                    }
                }
                if (!isCameraOpen.get()) {
                    cameraManager.openCamera(camId, cameraStateCallback, cameraHandler)
                }
            } catch (e: Exception) {
                onError(VerIDSessionException(e))
            }
        }
    }

    fun stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return
        }
        Log.d("Stopping camera")
        isCameraOpen.set(false)
        try {
            cameraCaptureSession.getAndSet(null)?.let { session ->
                session.stopRepeating()
                session.close()
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startPreview() {
        try {
            val device = cameraDevice.get() ?: throw Exception("Camera not available")
            val surfaces = mutableListOf<Surface>()
            val previewBuilder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val previewSurface =
                surfaceRef.get() ?: throw Exception("Preview surface not available")
            if (!previewSurface.isValid) {
                throw Exception("Preview surface unavailable")
            }
            surfaces.add(previewSurface)
            previewBuilder.addTarget(previewSurface)
            val reader = imageReader.get() ?: throw Exception("Image reader not available")
            reader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler)
            previewBuilder.addTarget(reader.surface)
            surfaces.add(reader.surface)
            videoRecorder?.surface?.getOrNull()?.let { surface ->
                previewBuilder.addTarget(surface)
                surfaces.add(surface)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    surfaces.map { OutputConfiguration(it) },
                    cameraExecutor,
                    CaptureSessionStateCallback(this@CameraWrapper, previewBuilder)
                )
                device.createCaptureSession(sessionConfig)
            } else {
                device.createCaptureSession(
                    surfaces,
                    CaptureSessionStateCallback(this@CameraWrapper, previewBuilder),
                    cameraHandler
                )
            }
        } catch (e: VerIDSessionException) {
            onError(e)
        } catch (e: Exception) {
            onError(VerIDSessionException(e))
        }
    }

    private fun startBackgroundHandlers() {
        if (!cameraHandlerThread.isAlive) {
            cameraHandlerThread = HandlerThread("CameraHandler")
            cameraHandlerThread.start()
            cameraHandler = Handler(cameraHandlerThread.looper)
        }
    }

    private fun stopBackgroundHandlers() {
        try {
            cameraHandler.removeCallbacksAndMessages(null)
            cameraHandlerThread.quitSafely()
            cameraHandlerThread.join(500)
            if (cameraHandlerThread.isAlive) {
                cameraHandlerThread.interrupt()
            }
        } catch (e: InterruptedException) {
            Log.e("Camera handler thread interrupted", e)
        }
    }

    private fun onError(error: Exception) {
        Log.e("Camera error", error)
        val context = contextRef.get() ?: return
        context.lifecycleScope.launch(Dispatchers.Main.immediate) {
            val e: VerIDSessionException =
                if (error is VerIDSessionException) error else VerIDSessionException(error)
            for (listener in listeners) {
                listener.onCameraError(e)
            }
        }
    }

    private fun setRotation(rotationDegrees: Int) {
        val exifOrientation = getExifOrientation(rotationDegrees)
        val (isMirrored, orientation) = when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> true to ExifInterface.ORIENTATION_NORMAL
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> true to ExifInterface.ORIENTATION_ROTATE_180
            ExifInterface.ORIENTATION_TRANSPOSE -> true to ExifInterface.ORIENTATION_ROTATE_270
            ExifInterface.ORIENTATION_TRANSVERSE -> true to ExifInterface.ORIENTATION_ROTATE_90
            else -> false to exifOrientation
        }
        this.isMirrored.set(isMirrored)
        this.exifOrientation.set(orientation)
    }

    private fun getExifOrientation(rotationDegrees: Int): Int {
        return when {
            rotationDegrees == 90 && cameraLocation == CameraLocation.BACK -> ExifInterface.ORIENTATION_ROTATE_90
            rotationDegrees == 90 && cameraLocation == CameraLocation.FRONT -> ExifInterface.ORIENTATION_TRANSVERSE
            rotationDegrees == 180 && cameraLocation == CameraLocation.BACK -> ExifInterface.ORIENTATION_ROTATE_180
            rotationDegrees == 180 && cameraLocation == CameraLocation.FRONT -> ExifInterface.ORIENTATION_FLIP_VERTICAL
            rotationDegrees == 270 && cameraLocation == CameraLocation.BACK -> ExifInterface.ORIENTATION_ROTATE_270
            rotationDegrees == 270 && cameraLocation == CameraLocation.FRONT -> ExifInterface.ORIENTATION_TRANSPOSE
            cameraLocation == CameraLocation.FRONT -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun createCameraManager() {
        cameraManager = contextRef.get()?.applicationContext?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

//    private fun verIDImageFromMediaImage(image: MediaImage): Image {
//        val veridImage = imageUtils?.verIDImageFromMediaImage(image, exifOrientation.get())
//            ?: throw IllegalStateException("Image utils not available")
//        veridImage.setIsMirrored(isMirrored.get())
//        return veridImage
//
////        if (imageBuffer == null) {
////            imageBuffer = ByteBuffer.allocateDirect(image.width * image.height * 4)
////        }
////        imageUtils?.fillBGRABufferFromMediaImage(image, exifOrientation.get(), imageBuffer)
////        val imageData = ByteArray(imageBuffer!!.capacity())
////        imageBuffer!!.get(imageData)
////        val size = Size(image.width, image.height)
////        ImageUtils.adjustSizeForOrientation(size, exifOrientation.get());
////        val veridImage = Image(imageData, size.width, size.height, ExifInterface.ORIENTATION_NORMAL, image.width*4, com.appliedrec.verid.core2.ImageFormat.BGRA);
//    }

    //region OnImageAvailableListener

//    override fun onImageAvailable(reader: ImageReader?) {
//        if (!isStarted.get()) {
//            return
//        }
//        try {
//            reader?.acquireLatestImage()?.use { image ->
//                if (!isStarted.get()) {
//                    return
//                }
//                val mediaImageImage = synchronized(imagePool) {
//                    // Check if we have any available images in the pool that are eligible to be reused
//                    val availableImage = imagePool.find { it.isConsumed.get() }
//                    if (availableImage != null) {
//                        // Mark the image as not consumed and return it for reuse
//                        availableImage.isConsumed.set(false)
//                        availableImage.updateYUVBuffersFromImage(image, exifOrientation.get())
//                        availableImage
//                    } else {
//                        // Create a new MediaImageImage if none are available
//                        val newImage = MediaImageImage(image, exifOrientation.get(), isMirrored.get())
//                        imagePool.add(newImage)
//                        newImage
//                    }
//                }
//                imageMutableSharedFlow.tryEmit(mediaImageImage)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

    //endregion

    //region Lifecycle

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        createCameraManager()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (viewWidth.get() > 0 && viewHeight.get() > 0 && displayRotation.get() > 0) {
            start(viewWidth.get(), viewHeight.get(), displayRotation.get())
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        contextRef.clear()
        listeners.clear()
    }

    //endregion
}