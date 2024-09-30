package com.appliedrec.verid.sample.sharing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.appliedrec.verid.core2.util.Log
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
                        "Session diagnostics uploaded to ${uploadURL}"
                    )
                    callback.set(Result.success())
                } catch (error: Exception) {
                    Log.e("Failed to upload session diagnostics", error)
                    callback.set(Result.failure())
                }
            }
        }
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        return CallbackToFutureAdapter.getFuture { completer ->
            val notification = createNotification()
            val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, notification)
            completer.set(foregroundInfo)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "upload_channel"
        val channelName = "Upload Channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Uploading Session Diagnostics")
            .setContentText("Session diagnostic upload in progress")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}