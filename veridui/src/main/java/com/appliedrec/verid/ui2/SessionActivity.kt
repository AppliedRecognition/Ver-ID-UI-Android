package com.appliedrec.verid.ui2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appliedrec.verid.core2.util.Log
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Activity used to control Ver-ID sessions
 * @since 2.0.0
 */
@Keep
class SessionActivity<T> : AppCompatActivity(), ISessionActivity where T : View, T : ISessionView {

    private val faceImages: ArrayList<Bitmap> = ArrayList()
    private var sessionParameters: SessionParameters? = null
    private var sessionController: SessionController<T>? = null
    private var isSessionStarted = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v("SessionActivity: onCreate")
        faceImages.clear()
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), SessionActivity.REQUEST_CODE_CAMERA_PERMISSION)
        } else {
            createSessionController()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        if (isSessionStarted.compareAndSet(false, true)) {
            sessionController?.startSession()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            sessionController?.cancelSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        faceImages.clear()
        isSessionStarted.set(false)
        sessionParameters = null
        sessionController?.cleanup()
        sessionController = null
    }

    //region ISessionActivity
    @Keep
    override fun setSessionParameters(sessionParameters: SessionParameters) {
        Log.v("SessionActivity: setSessionParameters")
        this.sessionParameters = sessionParameters
    }

    @Keep
    fun getSessionParameters(): Optional<SessionParameters> {
        return Optional.ofNullable(sessionParameters)
    }

    val sessionResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            { _ ->
                getSessionParameters().flatMap(
                    { obj: SessionParameters -> obj.onSessionFinishedRunnable }).ifPresent(
                    { obj: Runnable -> obj.run() })
                finish()
            })

    private fun createSessionController() {
        val sessionParams = sessionParameters
        if (sessionParams == null) {
            return;
        }
        sessionController = SessionController(this, sessionParams)
        sessionController!!.onShowSessionFailureDialog = dialog@ { error ->
            isSessionStarted.set(false)
            val alertDialog = sessionParams.sessionFailureDialogFactory?.makeDialog(
                this,
                { onDismissAction: SessionFailureDialogFactory.OnDismissAction? ->
                    if (onDismissAction != null) {
                        when (onDismissAction) {
                            SessionFailureDialogFactory.OnDismissAction.RETRY -> {
                                startActivity(intent)
                                finish()
                            }

                            SessionFailureDialogFactory.OnDismissAction.CANCEL -> {
                                sessionParams.onSessionCancelledRunnable.ifPresent { it.run() }
                                finish()
                            }

                            SessionFailureDialogFactory.OnDismissAction.SHOW_TIPS -> {
                                val tipsActivityIntent = sessionParams.tipsIntentSupplier.apply(this)
                                tipsLauncher.launch(tipsActivityIntent)
                            }
                        }
                    }
                },
                error,
                sessionParams.stringTranslator
            )
            if (alertDialog != null) {
                alertDialog.show()
                return@dialog true
            } else {
                return@dialog false
            }
        }
        setContentView(sessionController!!.sessionView)
        if (isSessionStarted.get()) {
            sessionController!!.startSession()
        }
    }

    private val tipsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        startActivity(intent)
        finish()
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
                        createSessionController()
                    } else {
                        sessionParameters?.onSessionCancelledRunnable?.ifPresent { it.run() }
                        finish()
                    }
                    return
                }
            }
        }
    }

    companion object {
        /**
         * The constant EXTRA_SESSION_ID.
         */
        @Keep
        const val EXTRA_SESSION_ID: String = "com.appliedrec.verid.EXTRA_SESSION_ID"

        /**
         * The constant REQUEST_CODE_CAMERA_PERMISSION.
         */
        const val REQUEST_CODE_CAMERA_PERMISSION: Int = 10
    }
}
