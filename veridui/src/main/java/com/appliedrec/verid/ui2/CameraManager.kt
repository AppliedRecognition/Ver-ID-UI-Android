package com.appliedrec.verid.ui2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.appliedrec.verid.core2.session.VerIDSessionException
import com.appliedrec.verid.core2.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

class CameraManager(context: Context) {

    private companion object {
        val cameraRef = AtomicReference<Camera?>(null)
    }

    private val contextRef = WeakReference(context)
    private val cameraHandlerThreadRef = AtomicReference<HandlerThread?>(null)
    private val cameraHandlerRef = AtomicReference<Handler?>(null)

    fun openCamera(cameraLocation: CameraLocation, onCamera: (Result<Camera>) -> Unit) {
        val context = contextRef.get() ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onCamera(Result.failure(VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED)))
            return
        }
        closeCamera {
            try {
                startBackgroundHandlers()
            } catch (e: Exception) {
                onCamera(Result.failure(e))
                return@closeCamera
            }
            val cameraHandler = cameraHandlerRef.get() ?: return@closeCamera
            cameraHandler.post {
                try {
                    val cameraManager =
                        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                            ?: throw Exception("Camera manager not available")
                    val requestedLensFacing = when (cameraLocation) {
                        CameraLocation.BACK -> CameraCharacteristics.LENS_FACING_BACK
                        CameraLocation.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                        else -> CameraCharacteristics.LENS_FACING_EXTERNAL
                    }
                    val cameras = cameraManager.cameraIdList
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
                    val cameraOrientation =
                        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    val sensorOrientation = (360 - cameraOrientation) % 360
                    val isMirrored = cameraLocation == CameraLocation.FRONT
                    cameraManager.openCamera(
                        camId, cameraStateCallback(sensorOrientation, isMirrored, onCamera),
                        cameraHandler
                    )
                } catch (e: Exception) {
                    onCamera(Result.failure(e))
                }
            }
        }
    }

    fun closeCamera(callback: (() -> Unit)? = null) {
        val camera = cameraRef.get()
        if (camera != null) {
            camera.close(callback)
        } else {
            callback?.let { it() }
        }
    }

    private fun cameraStateCallback(sensorOrientation: Int, isMirrored: Boolean, onCamera: (Result<Camera>) -> Unit) = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            val camera = Camera(device, sensorOrientation, isMirrored)
            cameraRef.set(camera)
            Log.d("CameraManager: Camera opened")
            onCamera(Result.success(camera))
        }

        override fun onDisconnected(p0: CameraDevice) {
            Log.v("CameraManager: Camera disconnected")
            cameraRef.set(null)
        }

        override fun onError(p0: CameraDevice, errorCode: Int) {
            cameraRef.set(null)
            val message = when (errorCode) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Too many other open camera devices"
                ERROR_CAMERA_DISABLED -> "Camera disabled"
                ERROR_CAMERA_DEVICE -> "Camera failed"
                ERROR_CAMERA_SERVICE -> "Camera service failed"
                else -> "Unknown error"
            }
            onCamera(Result.failure(Exception("Failed to open camera: $message")))
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            stopBackgroundHandlers()
            cameraRef.getAndSet(null)?.onClosed()
        }

    }

    private fun startBackgroundHandlers() {
        stopBackgroundHandlers()
        val newThread = HandlerThread("CameraManagerHandler").apply {
            start()
            Log.d("CameraManager: Background handler thread started")
        }
        cameraHandlerThreadRef.set(newThread)
        cameraHandlerRef.set(Handler(newThread.looper))
    }

    private fun stopBackgroundHandlers() {
        val handler = cameraHandlerRef.getAndSet(null)
        val thread = cameraHandlerThreadRef.getAndSet(null)
        thread?.let {
            try {
                handler?.removeCallbacksAndMessages(null)
                it.quitSafely()
                it.join(500)
                if (it.isAlive) {
                    it.interrupt()
                }
                Log.v("CameraManager: Background handler thread stopped")
            } catch (e: InterruptedException) {
                Log.e("CameraManager: Camera handler thread interrupted", e)
            }
        }
    }
}

//object CameraManager1 : DefaultLifecycleObserver {
//
//    private val cameraLock = ReentrantLock()
//    private var camera: Camera? = null
//    private var cameraHandlerThread = HandlerThread("CameraHandler")
//    private var cameraHandler: Handler = run {
//        cameraHandlerThread.start()
//        Handler(cameraHandlerThread.looper)
//    }
//
//    suspend fun <T> openCamera(context: T, cameraLocation: CameraLocation): Camera where T : Context, T : LifecycleOwner = suspendCancellableCoroutine { cont ->
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            cont.resumeWithException(VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED))
//            return@suspendCancellableCoroutine
//        }
//        try {
//            context.lifecycle.addObserver(this)
//            val cameraManager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: throw Exception("Camera manager not available")
//            val cameraLensFacing = when (cameraLocation) {
//                CameraLocation.BACK -> CameraCharacteristics.LENS_FACING_BACK
//                CameraLocation.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
//                else -> CameraCharacteristics.LENS_FACING_EXTERNAL
//            }
//            val camera: Camera? = cameraLock.withLock {
//                if (camera != null && cameraManager.getCameraCharacteristics(camera!!.id).get(CameraCharacteristics.LENS_FACING) == cameraLensFacing) {
//                    camera!!
//                } else {
//                    camera?.close()
//                    camera = null
//                }
//                camera
//            }
//            if (camera != null) {
//                cont.resume(camera) {
//                    cameraLock.withLock {
//                        this@CameraManager.camera?.close()
//                        this@CameraManager.camera = null
//                    }
//                }
//                return@suspendCancellableCoroutine
//            }
//            val cameras = cameraManager.cameraIdList
//            val requestedLensFacing =
//                if (cameraLocation == CameraLocation.BACK) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
//            val camId = cameras.firstOrNull { camId ->
//                val lensFacing = cameraManager.getCameraCharacteristics(camId)
//                    .get(CameraCharacteristics.LENS_FACING)
//                lensFacing == requestedLensFacing
//            } ?: cameras.firstOrNull { camId ->
//                val lensFacing = cameraManager.getCameraCharacteristics(camId)
//                    .get(CameraCharacteristics.LENS_FACING)
//                lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
//            } ?: throw Exception("Camera not available")
//            val characteristics = cameraManager.getCameraCharacteristics(camId)
//            val cameraOrientation =
//                characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
//            val sensorOrientation = (360 - cameraOrientation) % 360
//            val isMirrored = cameraLocation == CameraLocation.FRONT
//            startBackgroundHandlers()
//            cameraManager.openCamera(camId, cameraStateCallback(context, sensorOrientation, isMirrored, cont), cameraHandler)
//        } catch (e: Exception) {
//            cont.resumeWithException(VerIDSessionException(e))
//        }
//    }
//
//    override fun onDestroy(owner: LifecycleOwner) {
//        super.onDestroy(owner)
//        cameraLock.withLock {
//            Log.d("Closing camera")
//            camera?.close()
//            camera = null
//        }
//    }
//
//    private fun <T> cameraStateCallback(context: T, sensorOrientation: Int, isMirrored: Boolean, continuation: CancellableContinuation<Camera>) where T : Context, T : LifecycleOwner = object : CameraDevice.StateCallback() {
//        override fun onOpened(device: CameraDevice) {
//            stopBackgroundHandlers()
//            val camera = Camera(device, sensorOrientation, isMirrored)
//            cameraLock.withLock {
//                this@CameraManager.camera = camera
//            }
//            if (continuation.isActive) {
//                Log.d("Opened camera")
//                continuation.resume(camera) {
//                    cameraLock.withLock {
//                        this@CameraManager.camera?.close()
//                        this@CameraManager.camera = null
//                    }
//                }
//            }
//        }
//
//        override fun onDisconnected(device: CameraDevice) {
//            val shouldContinue = cameraLock.withLock {
//                if (device == this@CameraManager.camera?.device) {
//                    this@CameraManager.camera?.close()
//                    this@CameraManager.camera = null
//                    true
//                } else {
//                    false
//                }
//            }
//            if (shouldContinue) {
//                Log.d("Camera disconnected")
//                stopBackgroundHandlers()
//            }
//        }
//
//        override fun onError(device: CameraDevice, errorCode: Int) {
//            val shouldContinue = cameraLock.withLock {
//                if (device == this@CameraManager.camera?.device) {
//                    this@CameraManager.camera?.close()
//                    this@CameraManager.camera = null
//                    true
//                } else {
//                    false
//                }
//            }
//            if (shouldContinue && continuation.isActive) {
//                stopBackgroundHandlers()
//                val message = when (errorCode) {
//                    ERROR_CAMERA_IN_USE -> "Camera in use"
//                    ERROR_MAX_CAMERAS_IN_USE -> "Too many other open camera devices"
//                    ERROR_CAMERA_DISABLED -> "Camera disabled"
//                    ERROR_CAMERA_DEVICE -> "Camera failed"
//                    ERROR_CAMERA_SERVICE -> "Camera service failed"
//                    else -> "Unknown error"
//                }
//                Log.e("Camera error")
//                continuation.resumeWithException(VerIDSessionException(Exception("Failed to open camera: $message")))
//            }
//        }
//
//        override fun onClosed(camera: CameraDevice) {
//            super.onClosed(camera)
//            val shouldClose = cameraLock.withLock {
//                if (camera == this@CameraManager.camera?.device) {
//                    this@CameraManager.camera?.close()
//                    this@CameraManager.camera = null
//                    true
//                } else {
//                    false
//                }
//            }
//            if (shouldClose) {
//                Log.d("Camera closed")
//                stopBackgroundHandlers()
//            }
//        }
//    }
//
//    private fun startBackgroundHandlers() {
//        if (!cameraHandlerThread.isAlive) {
//            cameraHandlerThread = HandlerThread("CameraHandler")
//            cameraHandlerThread.start()
//            cameraHandler = Handler(cameraHandlerThread.looper)
//        }
//    }
//
//    private fun stopBackgroundHandlers() {
//        try {
//            cameraHandler.removeCallbacksAndMessages(null)
//            cameraHandlerThread.quitSafely()
//            cameraHandlerThread.join(500)
//            if (cameraHandlerThread.isAlive) {
//                cameraHandlerThread.interrupt()
//            }
//        } catch (e: InterruptedException) {
//            Log.e("Camera handler thread interrupted", e)
//        }
//    }
//}