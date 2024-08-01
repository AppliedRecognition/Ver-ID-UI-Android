package com.appliedrec.verid.sample.sharing

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class SessionDiagnosticUploadWorker(context: Context, workerParams: WorkerParameters) : ListenableWorker(context, workerParams) {

    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture { callback ->
            Executors.newSingleThreadExecutor().execute {
                try {
                    val uploadFilePath = inputData.getString("uploadFile")
                    val uploadURL = URL(inputData.getString("uploadURL"))
                    val connection = uploadURL.openConnection() as HttpsURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/zip")
                    connection.outputStream.use { outputStream ->
                        FileInputStream(uploadFilePath).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    File(uploadFilePath).delete()
                    if (connection.responseCode != 200) {
                        throw IOException("Session diagnostics upload responded with code ${connection.responseCode}")
                    }
                    Log.d(
                        "Ver-ID",
                        "Session diagnostics uploaded to ${uploadURL}"
                    )
                    callback.set(Result.success())
                } catch (error: Exception) {
                    Log.e("Ver-ID", "Failed to upload session diagnostics", error)
                    callback.set(Result.failure())
                }
            }
        }
    }
}