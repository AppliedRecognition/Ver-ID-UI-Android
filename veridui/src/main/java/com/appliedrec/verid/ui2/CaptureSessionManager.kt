package com.appliedrec.verid.ui2

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.StateCallback
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
import android.view.View
import com.appliedrec.verid.core2.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.optionals.getOrNull

class CaptureSessionManager(context: Context) {

    class Session(val session: CameraCaptureSession, private val imageReader: ImageReader, private val handler: AtomicReference<Handler?>, private val isProcessingImage: AtomicBoolean) {

        private val isClosed: AtomicBoolean = AtomicBoolean(false)
        private val isClosing: AtomicBoolean = AtomicBoolean(false)
        private val closeCallbacks = mutableSetOf<() -> Unit>()
        private val closeCallbacksLock = ReentrantLock()

        fun close(callback: (() -> Unit)? = null) {
            if (isClosed.get()) {
                callback?.let { it() }
                return
            }
            if (callback != null) {
                closeCallbacksLock.withLock {
                    closeCallbacks.add(callback)
                }
            }
            if (!isClosing.compareAndSet(false, true)) {
                return
            }

            Log.d("CaptureSessionManager.Session: close")
            val cameraHandler = handler.get()
            if (cameraHandler == null) {
                Log.e("CaptureSessionManager.Session: close – Camera handler not available")
                onClosed()
                return
            }

            cameraHandler.post {
                closeSessionInternal()
            }
        }

        private fun closeSessionInternal() {
            try {
                safeExecute("remove image listener") {
                    imageReader.setOnImageAvailableListener(null, null)
                }
                waitForImageProcessingWithTimeout()
                safeExecute("stop repeating requests") {
                    session.stopRepeating()
                }
                safeExecute("abort captures") {
                    session.abortCaptures()
                }
                session.close()
                val timeoutHandler = handler.get()
                timeoutHandler?.postDelayed({
                    if (!isClosed.get()) {
                        Log.w("CaptureSessionManager.Session: onClosed callback not received within timeout, forcing cleanup")
                        onClosed()
                    }
                }, 3000)

            } catch (e: Exception) {
                Log.e("CaptureSessionManager.Session: Error during session close", e)
                onClosed()
            }
        }

        private fun waitForImageProcessingWithTimeout() {
            val maxWaitTimeMs = 1000L // 1 second max wait
            val checkIntervalMs = 50L
            var waitedTimeMs = 0L

            try {
                while (isProcessingImage.get() && waitedTimeMs < maxWaitTimeMs) {
                    Thread.sleep(checkIntervalMs)
                    waitedTimeMs += checkIntervalMs

                    if (waitedTimeMs % 200L == 0L) { // Log every 200ms
                        Log.v("CaptureSessionManager.Session: Still waiting for image processing to complete (${waitedTimeMs}ms)")
                    }
                }

                if (isProcessingImage.get()) {
                    Log.w("CaptureSessionManager.Session: Timeout waiting for image processing to complete, proceeding anyway")
                    // Force reset the flag to prevent infinite blocking
                    isProcessingImage.set(false)
                } else {
                    Log.v("CaptureSessionManager.Session: Image processing completed successfully")
                }
            } catch (e: InterruptedException) {
                Log.w("CaptureSessionManager.Session: Image processing wait interrupted", e)
                Thread.currentThread().interrupt() // Restore interrupt status
                isProcessingImage.set(false) // Force reset
            }
        }

        private fun safeExecute(operation: String, block: () -> Unit) {
            try {
                block()
                Log.v("CaptureSessionManager.Session: Successfully executed: $operation")
            } catch (e: Exception) {
                Log.w("CaptureSessionManager.Session: Failed to execute: $operation", e)
            }
        }

        internal fun onClosed() {
            if (isClosing.compareAndSet(true, false) && isClosed.compareAndSet(false, true)) {
                Log.d("CaptureSessionManager.Session: onClosed")
                closeCallbacksLock.withLock {
                    closeCallbacks.forEach { it() }
                    closeCallbacks.clear()
                }
                handler.get()?.removeCallbacksAndMessages(null)
            }
        }
    }

    private val cameraPreviewHelper = CameraPreviewHelper()
    private val cameraHandlerThreadRef = AtomicReference<HandlerThread?>(null)
    private val cameraHandlerRef = AtomicReference<Handler?>(null)
    private val cameraExecutorRef = AtomicReference<Executor?>(null)
    private val session = AtomicReference<Session?>(null)
    private val contextRef = WeakReference(context)
    private val isProcessingImage = AtomicBoolean(false)

    fun <U> createSession(
        device: Camera,
        sessionView: U,
        previewSurface: Surface,
        onImageAvailableListener: OnImageAvailableListener,
        videoRecorder: ISessionVideoRecorder? = null,
        onSession: (Result<Session>) -> Unit
    ) where U : View, U : ISessionView {
        closeSession {
            startBackgroundHandlers()
            val cameraHandler = cameraHandlerRef.get() ?: throw Exception("Camera handler not available")
            val cameraExecutor = cameraExecutorRef.get() ?: throw Exception("Camera executor not available")
            cameraExecutor.execute {
                try {
                    val cameraManager =
                        contextRef.get()?.applicationContext?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                            ?: throw Exception("Camera manager not available")
                    val characteristics = cameraManager.getCameraCharacteristics(device.id)
                    val configMap =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?: throw Exception("Scaler stream configuration map not available")
                    val sensorOrientation = device.sensorOrientation
                    val rotation = (360 - (sensorOrientation - sessionView.displayRotation)) % 360;
                    val yuvSizes = configMap.getOutputSizes(ImageFormat.YUV_420_888)
                    val previewSizes = configMap.getOutputSizes(sessionView.previewClass)
                    val videoSizes = configMap.getOutputSizes(MediaRecorder::class.java)
                    val sizes = cameraPreviewHelper.getOutputSizes(
                        previewSizes,
                        yuvSizes,
                        videoSizes,
                        sessionView.width,
                        sessionView.height,
                        sensorOrientation,
                        sessionView.displayRotation
                    )
                    val previewSize = sizes[0]
                    sessionView.setPreviewSize(previewSize.width, previewSize.height, sensorOrientation)
                    val reader = ImageReader.newInstance(
                        sizes[1].width,
                        sizes[1].height,
                        ImageFormat.YUV_420_888,
                        2
                    )
                    videoRecorder?.let { recorder ->
                        val videoSize = sizes[2]
                        recorder.setup(videoSize, rotation)
                    }
                    val surfaces = mutableListOf<Surface>()
                    val previewBuilder =
                        device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    if (!previewSurface.isValid) {
                        throw Exception("Preview surface unavailable")
                    }
                    surfaces.add(previewSurface)
                    previewBuilder.addTarget(previewSurface)
                    if (!(cameraHandlerThreadRef.get()?.isAlive ?: false)) {
                        throw Exception("Camera thread not running")
                    }
                    val onImageAvailable = OnImageAvailableListener {
                        if (isProcessingImage.compareAndSet(false, true)) {
                            try {
                                onImageAvailableListener.onImageAvailable(it)
                            } finally {
                                isProcessingImage.set(false)
                            }
                        }
                    }
                    reader.setOnImageAvailableListener(onImageAvailable, cameraHandler)
                    previewBuilder.addTarget(reader.surface)
                    surfaces.add(reader.surface)
                    videoRecorder?.surface?.getOrNull()?.let { surface ->
                        previewBuilder.addTarget(surface)
                        surfaces.add(surface)
                    }
                    previewBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    val captureRequest = previewBuilder.build()
                    val stateCallback =
                        createStateCallback(captureRequest, previewSurface, reader, onSession, videoRecorder)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val sessionConfig = SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            surfaces.map { OutputConfiguration(it) },
                            cameraExecutor,
                            stateCallback
                        )
                        device.createCaptureSession(sessionConfig)
                    } else {
                        device.createCaptureSession(
                            surfaces,
                            stateCallback,
                            cameraHandler
                        )
                    }
                } catch (e: Exception) {
                    onSession(Result.failure(e))
                }
            }
        }
    }

    fun closeSession(callback: (() -> Unit)? = null) {
        val sesh = session.get()
        if (sesh == null) {
            stopBackgroundHandlers()
            callback?.let { it() }
        } else {
            sesh.close(callback)
        }
    }

    private fun createStateCallback(
        captureRequest: CaptureRequest,
        previewSurface: Surface,
        imageReader: ImageReader,
        onSession: (Result<Session>) -> Unit,
        videoRecorder: ISessionVideoRecorder?
    ): StateCallback {
        return object : StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    val cameraHandler = cameraHandlerRef.get() ?: throw Exception("Camera handler not available")
                    session.setRepeatingRequest(
                        captureRequest,
                        null,
                        cameraHandler
                    )
                    videoRecorder?.start()
                    val captureSession = Session(session, imageReader, cameraHandlerRef, isProcessingImage)
                    this@CaptureSessionManager.session.set(captureSession)
                    onSession(Result.success(captureSession))
                } catch (e: Exception) {
                    onSession(Result.failure(e))
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                onSession(Result.failure(Exception("Failed to start camera preview")))
            }

            override fun onClosed(session: CameraCaptureSession) {
                super.onClosed(session)
                val cameraHandler = cameraHandlerRef.get() ?: return
                cameraHandler.post {
                    cleanupImageReader()
                    cleanupPreviewSurface()
                    finalizeCleanup()
                }
            }

            private fun cleanupImageReader() {
                try {
                    // Step 1: Drain images with timeout and limit
                    drainImageReaderSafely()

                    // Step 2: Clean up image reader surface
                    safeExecute("release image reader surface") {
                        if (imageReader.surface.isValid) {
                            imageReader.surface.release()
                            Log.v("CaptureSessionManager: Image reader surface released")
                        } else {
                            Log.v("CaptureSessionManager: Image reader surface already invalid")
                        }
                    }

                    // Step 3: Close image reader (independent of surface release)
                    safeExecute("close image reader") {
                        imageReader.close()
                        Log.v("CaptureSessionManager: Image reader closed")
                    }

                } catch (e: Exception) {
                    Log.e("CaptureSessionManager: Unexpected error during image reader cleanup", e)
                    // Still try to force close the image reader
                    try {
                        imageReader.close()
                    } catch (e2: Exception) {
                        Log.e("CaptureSessionManager: Failed to force close image reader", e2)
                    }
                }
            }

            private fun drainImageReaderSafely() {
                val maxImages = 50 // Prevent infinite drainage
                val timeoutMs = 1000L // 1 second max
                val startTime = System.currentTimeMillis()
                var imageCount = 0

                try {
                    while (imageCount < maxImages && (System.currentTimeMillis() - startTime) < timeoutMs) {
                        val image = imageReader.acquireNextImage() ?: break
                        try {
                            image.close()
                            imageCount++
                        } catch (e: Exception) {
                            Log.w("CaptureSessionManager: Failed to close drained image $imageCount", e)
                            // Continue draining other images
                        }
                    }

                    if (imageCount > 0) {
                        Log.v("CaptureSessionManager: Drained $imageCount images from reader")
                    }

                    if (imageCount >= maxImages) {
                        Log.w("CaptureSessionManager: Hit maximum image drainage limit ($maxImages)")
                    }

                    if ((System.currentTimeMillis() - startTime) >= timeoutMs) {
                        Log.w("CaptureSessionManager: Hit timeout while draining images")
                    }

                } catch (e: Exception) {
                    Log.e("CaptureSessionManager: Error during image drainage", e)
                    // Don't rethrow - continue with other cleanup
                }
            }

            private fun cleanupPreviewSurface() {
                safeExecute("release preview surface") {
                    if (previewSurface.isValid) {
                        previewSurface.release()
                        Log.v("CaptureSessionManager: Preview surface released")
                    } else {
                        Log.v("CaptureSessionManager: Preview surface already invalid")
                    }
                }
            }

            private fun finalizeCleanup() {
                this@CaptureSessionManager.session.getAndSet(null)?.onClosed()
                stopBackgroundHandlers()
            }

            private fun safeExecute(operation: String, block: () -> Unit) {
                try {
                    block()
                } catch (e: Exception) {
                    Log.e("CaptureSessionManager: Failed to execute: $operation", e)
                    // Continue with other operations
                }
            }
        }
    }

    private fun startBackgroundHandlers() {
        stopBackgroundHandlers()
        val newThread = HandlerThread("CaptureSessionHandler").apply {
            start()
            Log.d("CaptureSessionManager: Background handler thread started")
        }
        cameraHandlerThreadRef.set(newThread)
        cameraHandlerRef.set(Handler(newThread.looper))
        cameraExecutorRef.set(Executor { cameraHandlerRef.get()?.post(it) })
    }

    private fun stopBackgroundHandlers() {
        val handler = cameraHandlerRef.getAndSet(null)
        val thread = cameraHandlerThreadRef.getAndSet(null)
        cameraExecutorRef.set(null)
        thread?.let {
            try {
                handler?.removeCallbacksAndMessages(null)
                it.quitSafely()
                it.join(500)
                if (it.isAlive) {
                    it.interrupt()
                }
                Log.v("CaptureSessionManager: Background handler thread stopped")
            } catch (e: InterruptedException) {
                Log.e("CaptureSessionManager: Camera handler thread interrupted", e)
            }
        }
    }
}