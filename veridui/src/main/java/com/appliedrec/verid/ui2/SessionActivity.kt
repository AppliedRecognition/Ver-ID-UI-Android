package com.appliedrec.verid.ui2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.appliedrec.verid.core2.Bearing
import com.appliedrec.verid.core2.Size
import com.appliedrec.verid.core2.session.CoreSession
import com.appliedrec.verid.core2.session.FaceBounds
import com.appliedrec.verid.core2.session.FaceCapture
import com.appliedrec.verid.core2.session.FaceDetectionResult
import com.appliedrec.verid.core2.session.FaceExtents
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings
import com.appliedrec.verid.core2.session.RegistrationSessionSettings
import com.appliedrec.verid.core2.session.Session
import com.appliedrec.verid.core2.session.VerIDSessionException
import com.appliedrec.verid.core2.session.VerIDSessionResult
import com.appliedrec.verid.core2.session.VerIDSessionSettings
import com.appliedrec.verid.ui2.ISessionView.SessionViewListener
import com.appliedrec.verid.ui2.SessionFailureDialogFactory.OnDismissAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Activity used to control Ver-ID sessions
 * @since 2.0.0
 */
@Keep
class SessionActivity<T> : AppCompatActivity(), ISessionActivity,
    SessionViewListener,
    CameraWrapper.Listener where T : View?, T : ISessionView? {

    private val faceImages: ArrayList<Bitmap> = ArrayList()
    private var cameraWrapperRef: WeakReference<CameraWrapper<*>?> = WeakReference(null)
    private var _sessionView: T? = null
    private var coreSession: CoreSession? = null
    private var sessionPrompts: SessionPrompts? = null
    private val isSessionRunning: AtomicBoolean = AtomicBoolean(false)
    private var sessionParameters: SessionParameters? = null
    private val faceCaptureCount: AtomicInteger = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceImages.clear()
//        backgroundExecutor = Executors.newSingleThreadExecutor()
        _sessionView = getSessionParameters().map { parameters -> parameters.getSessionViewFactory<T>() }.orElseThrow().apply(this) as T
        _sessionView!!.defaultFaceExtents = defaultFaceExtents
        _sessionView!!.setSessionSettings(getSessionParameters().map { parameters -> parameters.sessionSettings }.orElse(LivenessDetectionSessionSettings()))
        _sessionView!!.addListener(this)
        setContentView(_sessionView)
        onSessionParams()
        lifecycleScope.launch {
            val drawables = createFaceDrawables()
            withContext(Dispatchers.Main.immediate) {
                drawFaces(drawables)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
//        if (backgroundExecutor != null && !backgroundExecutor!!.isShutdown) {
//            backgroundExecutor!!.shutdownNow()
//        }
//        backgroundExecutor = null
        faceImages.clear()
        sessionParameters = null
        cameraWrapper.ifPresent({ wrapper: CameraWrapper<*> -> wrapper.removeListener(this) })
        _sessionView?.removeListener(this)
        _sessionView = null
        sessionVideoRecorder.ifPresent({ observer: ISessionVideoRecorder? ->
            lifecycle.removeObserver(
                observer!!
            )
        })
        if (coreSession != null) {
            coreSession!!.faceDetectionLiveData.removeObservers(this)
            coreSession!!.faceCaptureLiveData.removeObservers(this)
            coreSession!!.sessionResultLiveData.removeObservers(this)
            coreSession = null
        }
        cameraWrapperRef.clear()
    }

    override fun onResume() {
        super.onResume()
        startSession()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            cancelSession()
        }
    }

    /**
     * Start Ver-ID session
     * @since 2.0.0
     */
    @Keep
    protected fun startSession() {
        if (isSessionRunning.compareAndSet(false, true)) {
            faceCaptureCount.set(0)
            try {
                startCamera()
                _sessionView?.onSessionStarted()
            } catch (ignore: Exception) {
            }
            if (coreSession != null) {
                coreSession!!.start()
            }
        }
    }

    /**
     * Cancel Ver-ID session
     * @since 2.0.0
     */
    @Keep
    protected fun cancelSession() {
        if (isSessionRunning.compareAndSet(true, false)) {
            getSessionParameters().flatMap({ obj: SessionParameters -> obj.onSessionCancelledRunnable })
                .ifPresent(
                    { obj: Runnable -> obj.run() })
        }
    }

    @get:Keep
    protected val session: Optional<Session<*>>
        /**
         * Get Ver-ID session
         * @return Ver-ID session running in this activity
         * @since 2.0.0
         */
        get() =//        return Optional.ofNullable(session);
            Optional.empty()

    //region Session view listener
    override fun onPreviewSurfaceCreated(surface: Surface) {
        try {
            val cameraWrapper: CameraWrapper<*> = cameraWrapper.orElseThrow(
                { Exception("Camera wrapper unavailable") })
            cameraWrapper.setPreviewSurface(surface)
            startCamera()
        } catch (e: Exception) {
            fail(VerIDSessionException(e))
        }
    }

    override fun onPreviewSurfaceDestroyed() {
        cameraWrapper.ifPresent({ obj: CameraWrapper<*> -> obj.stop() })
    }

    //endregion
    /**
     * Fail the session
     * @param error Session exception to return in the session result
     * @since 2.0.0
     */
    @Keep
    protected fun fail(error: VerIDSessionException?) {
        if (isSessionRunning.compareAndSet(true, false)) {
            val now: Long = System.currentTimeMillis()
            getSessionParameters().ifPresent({ sessionParams: SessionParameters ->
                sessionParams.setSessionResult(
                    VerIDSessionResult(error, now, now, null)
                )
            })
            getSessionParameters().flatMap({ obj: SessionParameters -> obj.onSessionFinishedRunnable })
                .ifPresent(
                    { obj: Runnable -> obj.run() })
            finish()
        }
    }

    @get:Keep
    protected val sessionView: Optional<T & Any>
        get() = Optional.ofNullable(_sessionView)

    @get:Keep
    protected val cameraWrapper: Optional<CameraWrapper<*>>
        get() = Optional.ofNullable(cameraWrapperRef.get())

    @get:Keep
    protected val viewSize: Size
        get() = _sessionView?.let { sv -> Size(sv.width, sv.height)} ?: Size(0, 0)

    @get:Keep
    protected val displayRotation: Int
        get() = _sessionView?.getDisplayRotation() ?: 0

    @get:Keep
    protected val faceImageHeight: Int
        get() = _sessionView?.capturedFaceImageHeight ?: 0

    @Keep
    protected fun drawFaces(faceImages: List<Drawable?>?) {
        _sessionView?.drawFaces(faceImages)
    }

    //region ISessionActivity
    @Keep
    override fun setSessionParameters(sessionParameters: SessionParameters) {
        this.sessionParameters = sessionParameters
        onSessionParams()
    }

    private fun onSessionParams() {
        val sessionView: ISessionView? = sessionView.orElse(null)
        if (this.sessionParameters != null && sessionView != null) {
            sessionPrompts = SessionPrompts(sessionParameters!!.stringTranslator)
            //            IImageIterator imageIterator = sessionParameters.getImageIteratorFactory().apply(this);
            coreSession = CoreSession(
                sessionParameters!!.verID,
                sessionParameters!!.sessionSettings,
                sessionView.faceBounds,
                this
            )
            val cameraWrapper = CameraWrapper(
                this,
                sessionParameters!!.cameraLocation,
                coreSession!!,
                coreSession!!.exifOrientation,
                coreSession!!.isMirrored,
                sessionParameters!!.videoRecorder.orElse(null)
            )
            cameraWrapper.capturedImageMinimumArea = sessionParameters!!.minImageArea
            cameraWrapper.setPreviewClass(sessionView.previewClass)
            cameraWrapper.addListener(this)
            //            Session.Builder<?> sessionBuilder = new Session.Builder<>(sessionParameters.getVerID(), sessionParameters.getSessionSettings(), imageIterator, this);
//            sessionBuilder.setSessionFunctions(sessionParameters.getSessionFunctions());
//            sessionBuilder.bindToLifecycle(this.getLifecycle());
//            session = sessionBuilder.build();
            coreSession!!.faceDetectionLiveData.observe(
                this,
                { faceDetectionResult: FaceDetectionResult ->
                    this.onFaceDetection(
                        faceDetectionResult
                    )
                })
            coreSession!!.faceCaptureLiveData.observe(
                this,
                { faceCapture: FaceCapture -> this.onFaceCapture(faceCapture) })
            coreSession!!.sessionResultLiveData.observe(
                this,
                { result: VerIDSessionResult -> this.onSessionResult(result) })
            sessionParameters!!.videoRecorder.ifPresent({ videoRecorder: ISessionVideoRecorder? ->
                lifecycle.addObserver(
                    videoRecorder!!
                )
            })
            cameraWrapperRef = WeakReference(cameraWrapper)
        } else {
            cameraWrapperRef.clear()
            //            session = null;
        }
    }

    @Keep
    protected fun onFaceDetection(faceDetectionResult: FaceDetectionResult) {
        getSessionParameters().flatMap({ obj: SessionParameters -> obj.faceDetectionResultObserver })
            .ifPresent(
                { observer: Observer<FaceDetectionResult> -> observer.onChanged(faceDetectionResult) })
        val prompt: String? =
            sessionPrompts!!.promptFromFaceDetectionResult(faceDetectionResult).orElse(null)
        _sessionView?.let { sv ->
            sv.setFaceDetectionResult(faceDetectionResult, prompt)
        }
    }

    @Keep
    protected fun onFaceCapture(faceCapture: FaceCapture) {
        getSessionParameters().flatMap({ obj: SessionParameters -> obj.faceCaptureObserver })
            .ifPresent(
                { faceCaptureObserver: Observer<FaceCapture> ->
                    faceCaptureObserver.onChanged(
                        faceCapture
                    )
                })
        if (getSessionParameters().map({ obj: SessionParameters -> obj.sessionSettings }).filter(
                { settings: VerIDSessionSettings? -> settings is RegistrationSessionSettings })
                .orElse(null) != null
        ) {
            lifecycleScope.launch {
                val targetHeight: Float = faceImageHeight.toFloat()
                val scale: Float = targetHeight / faceCapture.faceImage.height.toFloat()
                var bitmap: Bitmap = Bitmap.createScaledBitmap(
                    faceCapture.faceImage,
                    Math.round(faceCapture.faceImage.width.toFloat() * scale),
                    Math.round(faceCapture.faceImage.height.toFloat() * scale),
                    true
                )
                if (getSessionParameters()
                        .map<CameraLocation>({ obj: SessionParameters -> obj.cameraLocation })
                        .orElse(
                            CameraLocation.FRONT
                        ) == CameraLocation.FRONT
                ) {
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
                    drawFaces(drawables)
                }
            }
        }
    }

    private val sessionResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult<Intent, ActivityResult>(
            ActivityResultContracts.StartActivityForResult(),
            { _ ->
                getSessionParameters().flatMap(
                    { obj: SessionParameters -> obj.onSessionFinishedRunnable }).ifPresent(
                    { obj: Runnable -> obj.run() })
                finish()
            })

    @Keep
    protected fun onSessionResult(result: VerIDSessionResult) {
        if (!isSessionRunning.compareAndSet(true, false)) {
            return
        }
        cameraWrapper.ifPresent({ obj: CameraWrapper<*> -> obj.stop() })
        sessionVideoRecorder.flatMap({ recorder: ISessionVideoRecorder ->
            recorder.stop()
            recorder.videoFile
        }).ifPresent({ videoFile: File? -> result.setVideoUri(Uri.fromFile(videoFile)) })
        getSessionParameters().flatMap({ obj: SessionParameters -> obj.sessionResultObserver })
            .ifPresent(
                { observer: Observer<VerIDSessionResult> -> observer.onChanged(result) })

        if (result.error.isPresent && getSessionParameters().flatMap(
                { obj: SessionParameters -> obj.shouldRetryOnFailure() }).orElse(
                Function { _ -> false })
                .apply(result.error.get()) && getSessionParameters().map(
                { obj: SessionParameters -> obj.sessionFailureDialogFactory }).isPresent
        ) {
            val alertDialog: AlertDialog? = getSessionParameters().map(
                { obj: SessionParameters -> obj.sessionFailureDialogFactory }).get().makeDialog(
                this,
                { onDismissAction: OnDismissAction? ->
                    if (onDismissAction != null) {
                        when (onDismissAction) {
                            OnDismissAction.RETRY -> startSession()
                            OnDismissAction.CANCEL -> {
                                getSessionParameters().flatMap({ obj: SessionParameters -> obj.onSessionCancelledRunnable })
                                    .ifPresent(
                                        { obj: Runnable -> obj.run() })
                                finish()
                            }

                            OnDismissAction.SHOW_TIPS -> {
                                val tipsActivityIntent: Intent = getSessionParameters().map(
                                    { obj: SessionParameters -> obj.tipsIntentSupplier }).map(
                                    { supplier: Function<Activity, Intent> ->
                                        supplier.apply(
                                            this
                                        )
                                    }).orElseThrow(
                                    { RuntimeException() })
                                startActivity(tipsActivityIntent)
                            }
                        }
                    }
                },
                result.error.get(),
                getSessionParameters().map({ obj: SessionParameters -> obj.stringTranslator })
                    .orElse(null)
            )
            if (alertDialog != null) {
                alertDialog.show()
                return
            }
        }
        val onViewFinished: Runnable =
            Runnable {
                if (getSessionParameters().map(
                        { obj: SessionParameters -> obj.sessionResultDisplayIndicator }).orElse(
                        Function { _ -> false })
                        .apply(result) && getSessionParameters().map(
                        { obj: SessionParameters -> obj.resultIntentSupplier }).isPresent
                ) {
                    val intent: Intent = getSessionParameters().map(
                        { obj: SessionParameters -> obj.resultIntentSupplier }).get()
                        .apply(result, this)
                    sessionResultLauncher.launch(intent)
                } else if (getSessionParameters().flatMap({ obj: SessionParameters -> obj.onSessionFinishedRunnable }).isPresent) {
                    getSessionParameters().flatMap({ obj: SessionParameters -> obj.onSessionFinishedRunnable })
                        .get().run()
                    finish()
                }
            }
        if (_sessionView != null) {
            _sessionView!!.willFinishWithResult(result, onViewFinished)
        } else {
            onViewFinished.run()
        }
    }

    @Keep
    fun getSessionParameters(): Optional<SessionParameters> {
        return Optional.ofNullable(sessionParameters)
    }

    private fun createFaceDrawables(): List<Drawable?> {
        val bitmapDrawables: ArrayList<RoundedBitmapDrawable?> = ArrayList()
        getSessionParameters().map({ obj: SessionParameters -> obj.sessionSettings }).ifPresent(
            { sessionSettings: VerIDSessionSettings ->
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
                                resources,
                                placeholderImageForBearing(bearing)
                            )
                            if (getSessionParameters().map<CameraLocation>({ obj: SessionParameters -> obj.cameraLocation })
                                    .orElse(
                                        CameraLocation.FRONT
                                    ) == CameraLocation.FRONT
                            ) {
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
                        }
                        drawable = RoundedBitmapDrawableFactory.create(
                            resources, bitmap
                        )
                        drawable.cornerRadius = targetHeight / 6f
                        bitmapDrawables.add(drawable)
                    }
                }
            })
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

    @get:Keep
    protected val sessionVideoRecorder: Optional<ISessionVideoRecorder>
        //endregion
        get() {
            return getSessionParameters().flatMap({ obj: SessionParameters -> obj.videoRecorder })
        }

    //endregion
    /**
     * Start camera.
     */
    @SuppressLint("MissingPermission")
    @MainThread
    @Throws(
        Exception::class
    )
    private fun startCamera() {
        if (hasCameraPermission()) {
            val viewSize: Size = viewSize
            val displayRotation: Int = displayRotation
            val cameraWrapper: CameraWrapper<*> = cameraWrapper.orElseThrow(
                { Exception("Camera wrapper unavailable") })
            cameraWrapper.start(viewSize.width, viewSize.height, displayRotation)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
        }
    }

    @get:Keep
    protected val defaultFaceExtents: FaceExtents
        /**
         * Gets default face extents.
         *
         * @return the default face extents
         */
        get() {
            return getSessionParameters().map({ obj: SessionParameters -> obj.sessionSettings })
                .map(
                    { obj: VerIDSessionSettings -> obj.expectedFaceExtents })
                .orElse(LivenessDetectionSessionSettings().expectedFaceExtents)
        }

    //region Camera permissions
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            for (i in permissions.indices) {
                if (Manifest.permission.CAMERA == permissions.get(i)) {
                    if (grantResults.get(i) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            startCamera()
                        } catch (e: Exception) {
                            fail(VerIDSessionException(e))
                        }
                    } else {
                        fail(VerIDSessionException(VerIDSessionException.Code.CAMERA_ACCESS_DENIED))
                    }
                    return
                }
            }
        }
    }

    /**
     * Has camera permission boolean.
     *
     * @return the boolean
     */
    @Keep
    protected fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    //endregion

    //region Camera wrapper listener
    override fun onCameraPreviewSize(width: Int, height: Int, sensorOrientation: Int) {
        _sessionView?.setPreviewSize(width, height, sensorOrientation)
        _sessionView?.setCameraPreviewMirrored(
            getSessionParameters().map(
                { params: SessionParameters -> params.cameraLocation == CameraLocation.FRONT })
                .orElse(true)
        )
    }

    override fun onCameraError(error: VerIDSessionException) {
        fail(error)
    }
    //endregion

    companion object {
        /**
         * The constant EXTRA_SESSION_ID.
         */
        @Keep
        const val EXTRA_SESSION_ID: String = "com.appliedrec.verid.EXTRA_SESSION_ID"

        /**
         * The constant REQUEST_CODE_CAMERA_PERMISSION.
         */
        protected const val REQUEST_CODE_CAMERA_PERMISSION: Int = 10
    }
}
