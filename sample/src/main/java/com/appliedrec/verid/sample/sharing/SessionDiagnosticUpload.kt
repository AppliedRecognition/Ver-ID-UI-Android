package com.appliedrec.verid.sample.sharing

import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.appliedrec.verid.sample.databinding.DiagnosticUploadConsentBinding
import com.appliedrec.verid.sample.preferences.PreferenceKeys
import com.appliedrec.verid.ui2.sharing.SessionResultPackage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class SessionDiagnosticUpload(val activity: Activity) {

    private val sessionDiagnosticCollectionURL = "https://session-upload.ver-id.com"

    fun upload(sessionResultPackage: SessionResultPackage) {
        if (!hasUserConsent) {
            return
        }
        Executors.newSingleThreadExecutor().execute {
            try {
                val tempFile = File.createTempFile("diagnostics_", ".zip")
                FileOutputStream(tempFile).use { outputStream ->
                    sessionResultPackage.archiveToStream(outputStream)
                    val request: OneTimeWorkRequest =
                        OneTimeWorkRequest.Builder(SessionDiagnosticUploadWorker::class.java)
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .setConstraints(
                                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                            )
                            .setInputData(
                                Data.Builder()
                                    .putString("uploadFile", tempFile.path)
                                    .putString("uploadURL", sessionDiagnosticCollectionURL)
                                    .build()
                            )
                            .build()
                    WorkManager.getInstance(activity.applicationContext).enqueue(request)
                }
            } catch (exception: java.lang.Exception) {
                Log.e("Ver-ID", "Failed to upload session diagnostics", exception)
            }
        }
    }

    val hasUserConsent: Boolean
        get() {
            val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
            val value = preferences.getString(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD, "ask")
            return value == "allow"
        }

    fun askForUserConsent(completion: (Boolean) -> Unit) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        try {
            // Check if user previously allowed or denied consent
            val pref = preferences.getString(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD, "ask")
            when (pref) {
                "allow" -> completion(true)
                "deny" -> completion(false)
                else -> {
                    val diagnosticUploadConsentBinding: DiagnosticUploadConsentBinding =
                        createDiagnosticUploadConsentView()
                    AlertDialog.Builder(activity)
                        .setTitle("Diagnostic upload permission")
                        .setView(diagnosticUploadConsentBinding.getRoot())
                        .setNegativeButton("Deny") { _, _ ->
                            if (diagnosticUploadConsentBinding.rememberCheckBox.isChecked) {
                                // Remember the choice
                                preferences.edit()
                                    .putString(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD, "deny")
                                    .apply()
                            }
                            completion(false)
                        }
                        .setPositiveButton("Allow upload") { _, _ ->
                            if (diagnosticUploadConsentBinding.rememberCheckBox.isChecked) {
                                // Remember the choice
                                preferences.edit()
                                    .putString(PreferenceKeys.ALLOW_DIAGNOSTIC_UPLOAD, "allow")
                                    .apply()
                            }
                            completion(true)
                        }
                        .create()
                        .show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            completion(false)
        }
    }

    private fun createDiagnosticUploadConsentView(): DiagnosticUploadConsentBinding {
        return DiagnosticUploadConsentBinding.inflate(activity.layoutInflater, null, false)
    }
}