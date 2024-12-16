package com.appliedrec.verid.ui2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.media.ImageReader
import android.net.Uri
import android.view.Surface
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.appliedrec.verid.core2.Bearing
import com.appliedrec.verid.core2.session.CoreSession
import com.appliedrec.verid.core2.session.FaceBounds
import com.appliedrec.verid.core2.session.FaceCapture
import com.appliedrec.verid.core2.session.FaceDetectionResult
import com.appliedrec.verid.core2.session.RegistrationSessionSettings
import com.appliedrec.verid.core2.session.VerIDSessionException
import com.appliedrec.verid.core2.session.VerIDSessionResult
import com.appliedrec.verid.core2.util.Log
import com.appliedrec.verid.ui2.SessionFailureDialogFactory.OnDismissAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SessionController<T>(
    sessionActivity: SessionActivity<T>,
    sessionParameters: SessionParameters
) : ISessionView.SessionViewListener, ImageReader.OnImageAvailableListener
        where T : View, T : ISessionView {

    val sessionView: T
    var onShowSessionFailureDialog: ((VerIDSessionException) -> Boolean)? = null
    private val sessionActivityRef = WeakReference(sessionActivity)
    private val sessionParametersRef = WeakReference(sessionParameters)
    private val sessionPrompts: SessionPrompts
    private val coreSession: CoreSession
    private val faceBounds: AtomicReference<FaceBounds>
        get() = sessionView.faceBounds
    private val faceImageHeight: Int
        get() = sessionView.capturedFaceImageHeight
    private val exifOrientation: AtomicInteger
        get() = coreSession.exifOrientation
    private val isMirrored: AtomicBoolean
        get() = coreSession.isMirrored
    private val isSessionRunning: AtomicBoolean = AtomicBoolean(false)
    private val faceCaptureCount: AtomicInteger = AtomicInteger(0)
    private val captureSessionManager: CaptureSessionManager
    private val cameraManager: CameraManager
    private val isCaptureSessionActive = AtomicBoolean(false)
    private val faceImages: MutableList<Bitmap> = mutableListOf()

    init {
        Log.v("SessionController.init")
        cameraManager = CameraManager(sessionActivity)
        captureSessionManager = CaptureSessionManager(sessionActivity)
        sessionView = sessionParameters.getSessionViewFactory<T>().apply(sessionActivity) as T
        sessionView.defaultFaceExtents = sessionParameters.sessionSettings.expectedFaceExtents
        sessionView.setSessionSettings(sessionParameters.sessionSettings)
        sessionView.addListener(this)

        sessionPrompts = SessionPrompts(sessionParameters.stringTranslator)

        coreSession = CoreSession(
            sessionParameters.verID,
            sessionParameters.sessionSettings,
            faceBounds,
            sessionActivity
        )

        coreSession.faceDetectionLiveData.observe(
            sessionActivity,
            { faceDetectionResult: FaceDetectionResult ->
                this.onFaceDetection(
                    faceDetectionResult
                )
            })
        coreSession.faceCaptureLiveData.observe(
            sessionActivity,
            { faceCapture: FaceCapture -> this.onFaceCapture(faceCapture) })
        coreSession.sessionResultLiveData.observe(
            sessionActivity,
            { result: VerIDSessionResult -> this.onSessionResult(result) })
        sessionParameters.videoRecorder.ifPresent({ videoRecorder: ISessionVideoRecorder? ->
            sessionActivity.lifecycle.addObserver(
                videoRecorder!!
            )
        })
    }

    fun startSession() {
        if (isSessionRunning.compareAndSet(false, true)) {
            Log.d("Starting core session")
            faceCaptureCount.set(0)
            faceImages.clear()
            sessionView.onSessionStarted()
            coreSession.start()
        } else {
            Log.v("SessionActivity.startSession: session already running")
        }
    }

    fun cancelSession() {
        if (pauseSession()) {
            sessionParametersRef.get()?.onSessionCancelledRunnable?.ifPresent { it.run() }
        }
    }

    fun cleanup() {
        sessionView.removeListener(this)
        cancelSession()
    }

    private fun pauseSession(): Boolean {
        sessionActivityRef.get()?.lifecycleScope?.launch {
            stopCaptureSession()
        }
        if (isSessionRunning.compareAndSet(true, false)) {
            Log.d("Cancelling core session")
            coreSession.cancel()
            return true
        } else {
            Log.v("SessionActivity.pauseSession: session not running")
            return false
        }
    }

    override fun onPreviewSurfaceCreated(surface: Surface) {
        Log.v("SessionController.onPreviewSurfaceCreated")
        if (isCaptureSessionActive.compareAndSet(false, true)) {
            sessionActivityRef.get()?.lifecycleScope?.launch {
                try {
                    val camera = createCaptureSession(surface)
                    withContext(Dispatchers.Main.immediate) {
                        if (sessionActivityRef.get()?.isDestroyed ?: true || sessionActivityRef.get()?.isFinishing ?: true) {
                            return@withContext
                        }
                        val rotation =
                            (360 - (camera.sensorOrientation - sessionView.displayRotation)) % 360;
                        val exifOrientation = exifOrientationFromRotation(rotation)
                        this@SessionController.isMirrored.set(camera.isMirrored)
                        this@SessionController.exifOrientation.set(exifOrientation)
                        this@SessionController.sessionView.setCameraPreviewMirrored(camera.isMirrored)
                    }
                } catch (e: Exception) {
                    isCaptureSessionActive.set(false)
                    withContext(Dispatchers.Main.immediate) {
                        onSessionResult(
                            VerIDSessionResult(
                                VerIDSessionException(e),
                                0,
                                0,
                                null
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onPreviewSurfaceDestroyed() {
        Log.v("SessionController.onPreviewSurfaceDestroyed")
        sessionActivityRef.get()?.lifecycleScope?.launch {
            stopCaptureSession()
        }
    }

    override fun onImageAvailable(imageReader: ImageReader?) {
        if (isSessionRunning.get() && isCaptureSessionActive.get()) {
            coreSession.onImageAvailable(imageReader)
        }
    }

    private suspend fun createCaptureSession(surface: Surface): Camera = suspendCancellableCoroutine { cont ->
        val sessionParameters = sessionParametersRef.get() ?: return@suspendCancellableCoroutine
        cameraManager.openCamera(sessionParameters.cameraLocation) { cameraResult ->
            cameraResult.onSuccess { camera ->
                if (!cont.isActive) {
                    return@onSuccess
                }
                captureSessionManager.createSession(
                    camera, sessionView, surface, this@SessionController,
                    sessionParameters.videoRecorder.orElse(null)
                ) { captureSessionResult ->
                    captureSessionResult.onSuccess { _ ->
                        if (cont.isActive) {
                            cont.resume(camera)
                        }
                    }
                    captureSessionResult.onFailure { error ->
                        if (cont.isActive) {
                            cont.resumeWithException(error)
                        }
                    }
                }
            }
            cameraResult.onFailure { error ->
                if (cont.isActive) {
                    cont.resumeWithException(error)
                }
            }
        }
    }

    private suspend fun stopCaptureSession() = suspendCancellableCoroutine { cont ->
        if (isCaptureSessionActive.compareAndSet(true, false)) {
            captureSessionManager.closeSession {
                cameraManager.closeCamera {
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
        }
    }

    private fun exifOrientationFromRotation(rotationDegrees: Int): Int {
        return when (rotationDegrees) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }

    //region LiveData observers

    private fun onFaceDetection(faceDetectionResult: FaceDetectionResult) {
        sessionParametersRef.get()?.faceDetectionResultObserver?.ifPresent { observer ->
            observer.onChanged(faceDetectionResult)
        }
        val prompt: String? = sessionPrompts.promptFromFaceDetectionResult(faceDetectionResult).orElse(null)
        sessionView.setFaceDetectionResult(faceDetectionResult, prompt)
    }

    private fun onFaceCapture(faceCapture: FaceCapture) {
        sessionParametersRef.get()?.faceCaptureObserver?.ifPresent { observer ->
            observer.onChanged(faceCapture)
        }
        val sessionSettings = sessionParametersRef.get()?.sessionSettings ?: return
        if (!sessionView.isCapableOfDrawingFaces(sessionSettings)) {
            return
        }
        sessionActivityRef.get()?.lifecycleScope?.launch {
            val targetHeight: Float = faceImageHeight.toFloat()
            val scale: Float = targetHeight / faceCapture.faceImage.height.toFloat()
            var bitmap: Bitmap = Bitmap.createScaledBitmap(
                faceCapture.faceImage,
                Math.round(faceCapture.faceImage.width.toFloat() * scale),
                Math.round(faceCapture.faceImage.height.toFloat() * scale),
                true
            )
            if (sessionParametersRef.get()?.cameraLocation == CameraLocation.FRONT) {
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
            faceImages.add(bitmap)
            val drawables: List<Drawable?> = createFaceDrawables()
            withContext(Dispatchers.Main.immediate) {
                sessionView.drawFaces(drawables)
            }
        }
    }

    private fun onSessionResult(result: VerIDSessionResult) {
        Log.d("SessionController: onSessionResult")
        sessionActivityRef.get()?.lifecycleScope?.launch {
            stopCaptureSession()
            if (!isSessionRunning.compareAndSet(true, false)) {
                Log.w("SessionController: onSessionResult – Session already completed")
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                sessionParametersRef.get()?.videoRecorder?.flatMap({ recorder: ISessionVideoRecorder ->
                    recorder.stop()
                    recorder.videoFile
                })?.ifPresent({ videoFile: File? -> result.setVideoUri(Uri.fromFile(videoFile)) })
                if (result.error.isPresent && sessionParametersRef.get()?.shouldRetryOnFailure()
                        ?.map { onFail -> onFail.apply(result.error.get()) }
                        ?.orElse(false) == true && onShowSessionFailureDialog?.invoke(result.error.get()) == true
                ) {
                    return@withContext
                }
                sessionParametersRef.get()?.sessionResultObserver?.ifPresent { observer ->
                    observer.onChanged(result)
                }
                val onViewFinished = Runnable {
                    val sessionActivity = sessionActivityRef.get() ?: return@Runnable
                    val sessionParameters = sessionParametersRef.get() ?: return@Runnable
                    if (sessionParameters.sessionResultDisplayIndicator.apply(result)) {
                        val intent =
                            sessionParameters.resultIntentSupplier.apply(result, sessionActivity)
                        sessionActivity.sessionResultLauncher.launch(intent)
                    } else if (sessionParameters.onSessionFinishedRunnable.isPresent) {
                        sessionParameters.onSessionFinishedRunnable.get().run()
                        sessionActivity.finish()
                    }
                }
                sessionView.willFinishWithResult(result) {
                    Log.v("SessionController: onSessionFinishedRunnable – finish activity")
                    onViewFinished.run()
                }
            }
        }
    }

    //endregion

    private fun createFaceDrawables(): List<Drawable> {
        val bitmapDrawables: ArrayList<RoundedBitmapDrawable> = ArrayList()
        val sessionActivity = sessionActivityRef.get() ?: return bitmapDrawables
        sessionParametersRef.get()?.sessionSettings?.let { sessionSettings ->
            if (sessionSettings is RegistrationSessionSettings) {
                val bearingsToRegister: Array<Bearing> = sessionSettings.bearingsToRegister
                val targetHeight: Float = faceImageHeight.toFloat()
                for (i in 0 until sessionSettings.getFaceCaptureCount()) {
                    var drawable: RoundedBitmapDrawable
                    var bitmap: Bitmap
                    if (i < faceImages.size) {
                        bitmap = faceImages.get(i)
                    } else {
                        val bearingIndex: Int = i % bearingsToRegister.size
                        val bearing: Bearing = bearingsToRegister.get(bearingIndex)
                        bitmap = BitmapFactory.decodeResource(
                            sessionActivity.resources,
                            placeholderImageForBearing(bearing)
                        )
                        if (sessionParametersRef.get()?.cameraLocation == CameraLocation.FRONT) {
                            val matrix = Matrix()
                            matrix.setScale(-1f, 1f)
                            bitmap = Bitmap.createBitmap(bitmap,0,0, bitmap.width, bitmap.height, matrix,false)
                        }
                    }
                    drawable = RoundedBitmapDrawableFactory.create(
                        sessionActivity.resources, bitmap
                    )
                    drawable.cornerRadius = targetHeight / 6f
                    bitmapDrawables.add(drawable)
                }
            }
        }
        return bitmapDrawables
    }

    @DrawableRes
    private fun placeholderImageForBearing(bearing: Bearing): Int {
        when (bearing) {
            Bearing.LEFT -> return R.mipmap.head_left
            Bearing.RIGHT -> return R.mipmap.head_right
            Bearing.UP -> return R.mipmap.head_up
            Bearing.DOWN -> return R.mipmap.head_down
            Bearing.LEFT_UP -> return R.mipmap.head_left_up
            Bearing.RIGHT_UP -> return R.mipmap.head_right_up
            Bearing.LEFT_DOWN -> return R.mipmap.head_left_down
            Bearing.RIGHT_DOWN -> return R.mipmap.head_right_down
            else -> return R.mipmap.head_straight
        }
    }
}