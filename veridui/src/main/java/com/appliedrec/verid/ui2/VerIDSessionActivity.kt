package com.appliedrec.verid.ui2

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.appliedrec.verid.core2.VerID
import com.appliedrec.verid.core2.session.VerIDSessionResult
import java.lang.ref.WeakReference

class VerIDSessionActivity : AppCompatActivity(), VerIDSessionInViewDelegate, Observer<VerIDSessionResult> {

    private var session: VerIDSessionInView<SessionView>? = null
    private var settings: VerIDSessionActivitySettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ver_idsession)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        try {
            val settings: VerIDSessionActivitySettings =
                intent.getParcelableExtra("settings") ?: throw IllegalArgumentException("No settings provided")
            val verID = VerID.getInstance(settings.verIDInstanceId)
            this.settings = settings
            val sessionView = SessionView(this)
            this.session = VerIDSessionInView(verID, settings.sessionSettings, sessionView)
            this.session!!.sessionResultLiveData.observe(this, this)
            findViewById<ConstraintLayout>(R.id.main).addView(sessionView, ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT))
        } catch (e: Exception) {
            finish()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, BackCallback(this))
        } else {
            onBackPressedDispatcher.addCallback(this, BackPressCallback(this))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.let {
            it.sessionResultLiveData?.let { liveData ->
                liveData.removeObserver(this)
            }
        }
        session = null;
        findViewById<ConstraintLayout>(R.id.main).removeAllViews()
    }

    override fun onResume() {
        super.onResume()
        session?.start()
    }

    override fun onPause() {
        session?.stop()
        super.onPause()
    }

    override fun onChanged(value: VerIDSessionResult) {
        session?.let { onSessionFinished(it, value) }
    }

    override fun onSessionFinished(session: IVerIDSession<*>, result: VerIDSessionResult) {
        this.session?.let {
            it.sessionResultLiveData?.let { liveData ->
                liveData.removeObserver(this)
            }
            it.stop()
        }
        this.session = null
        val resultId = SessionResultStorage.addResult(result)
        setResult(RESULT_OK, Intent().apply {
            putExtra("resultId", resultId)
        })
        finish()
    }

    override fun shouldSessionRecordVideo(session: IVerIDSession<*>): Boolean {
        return settings?.shouldSessionRecordVideo ?: super.shouldSessionRecordVideo(session)
    }

    override fun shouldSessionSpeakPrompts(session: IVerIDSession<*>): Boolean {
        return settings?.shouldSessionSpeakPrompts ?: super.shouldSessionSpeakPrompts(session)
    }

    override fun getSessionCameraLocation(session: IVerIDSession<*>): CameraLocation {
        return settings?.cameraLocation ?: super.getSessionCameraLocation(session)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class BackCallback(activity: VerIDSessionActivity): OnBackInvokedCallback {

    private val activityRef: WeakReference<VerIDSessionActivity> = WeakReference(activity)

    override fun onBackInvoked() {
        activityRef.get()?.finish()
        activityRef.clear()
    }
}

private class BackPressCallback(activity: VerIDSessionActivity): OnBackPressedCallback(true) {

    private val activityRef: WeakReference<VerIDSessionActivity> = WeakReference(activity)

    override fun handleOnBackPressed() {
        activityRef.get()?.finish()
        activityRef.clear()
    }

}