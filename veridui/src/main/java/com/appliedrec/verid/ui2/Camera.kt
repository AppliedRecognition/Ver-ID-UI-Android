package com.appliedrec.verid.ui2

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.view.Surface
import androidx.annotation.RequiresApi
import com.appliedrec.verid.core2.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Camera(private val device: CameraDevice, val sensorOrientation: Int, val isMirrored: Boolean) {

    val id: String = device.id
    private val isClosed = AtomicBoolean(false)
    private val isClosing = AtomicBoolean(false)
    private val closeCallbacks = mutableSetOf<() -> Unit>();
    private val closeCallbackLock = ReentrantLock()

    fun createCaptureRequest(template: Int): CaptureRequest.Builder {
        return device.createCaptureRequest(template)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun createCaptureSession(configuration: SessionConfiguration) {
        device.createCaptureSession(configuration)
    }

    fun createCaptureSession(surfaces: List<Surface>, callback: CameraCaptureSession.StateCallback, handler: Handler) {
        device.createCaptureSession(surfaces, callback, handler)
    }

    fun close(callback: (() -> Unit)? = null) {
        if (isClosed.get()) {
            callback?.let { it() }
            return
        }
        if (callback != null) {
            closeCallbackLock.withLock {
                closeCallbacks.add(callback)
            }
        }
        if (isClosing.compareAndSet(false, true)) {
            Log.d("Camera: close")
            device.close()
        }
    }

    internal fun onClosed() {
        if (isClosing.compareAndSet(true, false) && isClosed.compareAndSet(false, true)) {
            Log.d("Camera: onClosed")
            closeCallbackLock.withLock {
                closeCallbacks.forEach { it() }
                closeCallbacks.clear()
            }
        }
    }
}