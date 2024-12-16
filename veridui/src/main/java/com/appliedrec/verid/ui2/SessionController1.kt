package com.appliedrec.verid.ui2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.net.Uri
import android.view.Surface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.appliedrec.verid.core2.session.CoreSession
import com.appliedrec.verid.core2.session.FaceBounds
import com.appliedrec.verid.core2.session.FaceCapture
import com.appliedrec.verid.core2.session.FaceDetectionResult
import com.appliedrec.verid.core2.session.RegistrationSessionSettings
import com.appliedrec.verid.core2.session.VerIDSessionException
import com.appliedrec.verid.core2.session.VerIDSessionResult
import com.appliedrec.verid.core2.util.Log
import com.appliedrec.verid.ui2.ISessionView.SessionViewListener
import com.appliedrec.verid.ui2.SessionFailureDialogFactory.OnDismissAction
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SessionController1<T>(
    val activity: SessionActivity<T>,
    val sessionParameters: SessionParameters
) : CameraWrapper.Listener, SessionViewListener, OnImageAvailableListener, DefaultLifecycleObserver where T : View, T : ISessionView {

    private enum class State {
        IDLE,
        STARTING,
        STARTED,
        CLOSING
    }

    val sessionView: T

    private val sessionPrompts: SessionPrompts
    private val coreSession: CoreSession
    private val cameraWrapper: CameraWrapper<*>
    private val faceBounds: AtomicReference<FaceBounds>
        get() = sessionView.faceBounds
    private val exifOrientation: AtomicInteger
        get() = coreSession.exifOrientation
    private val isMirrored: AtomicBoolean
        get() = coreSession.isMirrored
    private val faceImageHeight: Int
        get() = sessionView.capturedFaceImageHeight
    private val isSessionRunning: AtomicBoolean = AtomicBoolean(false)
    private val faceCaptureCount: AtomicInteger = AtomicInteger(0)
    private val cameraState: AtomicReference<State> = AtomicReference(State.IDLE)
    private lateinit var camera: Camera

    init {
        sessionView = sessionParameters.getSessionViewFactory<T>().apply(activity) as T
        sessionView.defaultFaceExtents = sessionParameters.sessionSettings.expectedFaceExtents
        sessionView.setSessionSettings(sessionParameters.sessionSettings)
        sessionView.addListener(this)
        sessionPrompts = SessionPrompts(sessionParameters.stringTranslator)
        coreSession = CoreSession(
            sessionParameters.verID,
            sessionParameters.sessionSettings,
            faceBounds,
            activity
        )
        cameraWrapper = CameraWrapper(
            activity,
            sessionParameters.cameraLocation,
            this,
            exifOrientation,
            isMirrored,
            sessionParameters.videoRecorder.orElse(null)
        )
        cameraWrapper.capturedImageMinimumArea = sessionParameters.minImageArea
        cameraWrapper.setPreviewClass(sessionView.previewClass)
        cameraWrapper.addListener(this)
        coreSession.faceDetectionLiveData.observe(
            activity,
            { faceDetectionResult: FaceDetectionResult ->
                this.onFaceDetection(
                    faceDetectionResult
                )
            })
        coreSession.faceCaptureLiveData.observe(
            activity,
            { faceCapture: FaceCapture -> this.onFaceCapture(faceCapture) })
        coreSession.sessionResultLiveData.observe(
            activity,
            { result: VerIDSessionResult -> this.onSessionResult(result) })
        sessionParameters.videoRecorder.ifPresent({ videoRecorder: ISessionVideoRecorder? ->
            activity.lifecycle.addObserver(
                videoRecorder!!
            )
        })
    }

    fun startSession() {
        if (isSessionRunning.compareAndSet(false, true)) {
            faceCaptureCount.set(0)
            sessionView.onSessionStarted()
            coreSession.start()
        } else {
            Log.v("SessionActivity.startSession: session already running")
        }
    }

    fun cancelSession() {
        if (isSessionRunning.compareAndSet(true, false)) {
            coreSession.cancel()
            sessionParameters.onSessionCancelledRunnable.ifPresent { it.run() }
        } else {
            Log.v("SessionActivity.cancelSession: session not running")
        }
        cleanup()
    }

    fun onCameraPermissionGranted() {
        if (cameraState.get() == State.STARTING) {
            cameraWrapper.start(sessionView.width, sessionView.height, sessionView.displayRotation)
        }
    }

    private fun cleanup() {
        sessionView.removeListener(this)
        cameraWrapper.removeListener(this)
    }

    private fun startCamera() {
        if (cameraState.compareAndSet(State.IDLE, State.STARTING)) {
            if (!hasCameraPermission()) {
                activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), SessionActivity.REQUEST_CODE_CAMERA_PERMISSION)
                return
            }
            cameraWrapper.start(sessionView.width, sessionView.height, sessionView.displayRotation)
        }
    }

    private fun stopCamera() {
        if (cameraState.get() == State.STARTED || cameraState.get() == State.STARTING) {
            cameraState.set(State.CLOSING)
            cameraWrapper.stop()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    //region CameraWrapper.Listener

    override fun onCameraPreviewSize(width: Int, height: Int, sensorOrientation: Int) {
        sessionView.setPreviewSize(width, height, sensorOrientation)
        sessionView.setCameraPreviewMirrored(sessionParameters.cameraLocation == CameraLocation.FRONT)
    }

    override fun onCameraError(error: VerIDSessionException) {
        cameraState.set(State.IDLE)
        if (isSessionRunning.compareAndSet(true, false)) {
            cancelSession()
            onSessionResult(VerIDSessionResult(error, 0, 0, null))
        }
    }

    override fun onCameraStarted() {
        cameraState.set(State.STARTED)
    }

    override fun onCameraStopped() {
        cameraState.set(State.IDLE)
    }

    //endregion

    //region SessionViewListener

    override fun onPreviewSurfaceCreated(surface: Surface) {
        cameraWrapper.setPreviewSurface(surface)
        startCamera()
    }

    override fun onPreviewSurfaceDestroyed() {
        stopCamera()
    }

    //endregion

    //region OnImageAvailableListener

    override fun onImageAvailable(imageReader: ImageReader?) {
        if (isSessionRunning.get()) {
            coreSession.onImageAvailable(imageReader)
        }
    }

    //endregion

    //region LiveData observers

    private fun onFaceDetection(faceDetectionResult: FaceDetectionResult) {
        sessionParameters.faceDetectionResultObserver.ifPresent { observer ->
            observer.onChanged(faceDetectionResult)
        }
        val prompt: String? = sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null)
        sessionView.setFaceDetectionResult(faceDetectionResult, prompt)
    }

    private fun onFaceCapture(faceCapture: FaceCapture) {
        sessionParameters.faceCaptureObserver.ifPresent { observer ->
            observer.onChanged(faceCapture)
        }
        if (sessionParameters.sessionSettings !is RegistrationSessionSettings) {
            return
        }
        activity.lifecycleScope.launch {
            val targetHeight: Float = faceImageHeight.toFloat()
            val scale: Float = targetHeight / faceCapture.faceImage.height.toFloat()
            var bitmap: Bitmap = Bitmap.createScaledBitmap(
                faceCapture.faceImage,
                Math.round(faceCapture.faceImage.width.toFloat() * scale),
                Math.round(faceCapture.faceImage.height.toFloat() * scale),
                true
            )
            if (sessionParameters.cameraLocation == CameraLocation.FRONT) {
                val matrix: Matrix = Matrix()
                matrix.setScale(-1f, 1f)
                bitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    false
                )
            }
            // TODO: Fix this
//            activity.faceImages.add(bitmap)
//            val drawables: List<Drawable?> = activity.createFaceDrawables()
//            withContext(Dispatchers.Main.immediate) {
//                activity.drawFaces(drawables)
//            }
        }
    }

    private fun onSessionResult(result: VerIDSessionResult) {
        stopCamera()
        if (!isSessionRunning.compareAndSet(true, false)) {
            return
        }
        sessionParameters.videoRecorder.flatMap({ recorder: ISessionVideoRecorder ->
            recorder.stop()
            recorder.videoFile
        }).ifPresent({ videoFile: File? -> result.setVideoUri(Uri.fromFile(videoFile)) })
        sessionParameters.sessionResultObserver.ifPresent { observer ->
            observer.onChanged(result)
        }
        if (result.error.isPresent && sessionParameters.shouldRetryOnFailure().map { onFail -> onFail.apply(result.error.get()) }.orElse(false)) {
            val alertDialog = sessionParameters.sessionFailureDialogFactory.makeDialog(
                activity,
                { onDismissAction: OnDismissAction? ->
                    if (onDismissAction != null) {
                        when (onDismissAction) {
                            OnDismissAction.RETRY -> {
                                startCamera()
                                startSession()
                            }
                            OnDismissAction.CANCEL -> {
                                sessionParameters.onSessionCancelledRunnable.ifPresent { it.run() }
                                cancelSession()
                            }

                            OnDismissAction.SHOW_TIPS -> {
                                val tipsActivityIntent = sessionParameters.tipsIntentSupplier.apply(activity)
                                activity.startActivity(tipsActivityIntent)
                            }
                        }
                    }
                },
                result.error.get(),
                sessionParameters.stringTranslator
            )
            if (alertDialog != null) {
                alertDialog.show()
                return
            }
        }
        sessionView.willFinishWithResult(result) {
            sessionParameters.onSessionFinishedRunnable.ifPresent {
                it.run()
                activity.finish()
            }
        }
    }

    //endregion

    //region DefaultLifecycleObserver

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        stopCamera()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopCamera()
        cancelSession()
    }

    //endregion
}