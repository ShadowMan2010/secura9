package com.secura9.app.utils

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.secura9.app.data.model.AppUpdate
import java.io.File

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val CHANNEL_ID = "apk_download"
        private const val NOTIFY_ID = 1001
        private const val DOWNLOAD_FILENAME = "secura9_update.apk"
    }

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var currentDownloadId: Long = -1

    fun downloadAndInstall(update: AppUpdate, onProgress: (Int) -> Unit = {}) {
        val destFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            DOWNLOAD_FILENAME
        )
        destFile.parentFile?.mkdirs()
        destFile.delete()

        val request = DownloadManager.Request(Uri.parse(update.apkUrl)).apply {
            setTitle("SECURA-9 Update")
            setDescription("Downloading ${update.versionName}...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destFile))
            setMimeType("application/vnd.android.package-archive")
        }

        currentDownloadId = dm.enqueue(request)
        registerCompletionReceiver(destFile)
    }

    private fun registerCompletionReceiver(destFile: File) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != currentDownloadId) return
                ctx.unregisterReceiver(this)
                installApk(ctx, destFile)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun installApk(ctx: Context, file: File) {
        if (!file.exists()) {
            Log.w(TAG, "APK not found at ${file.absolutePath}")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
        }
    }

    fun checkAndUpdate(
        currentVersionCode: Int,
        latest: AppUpdate,
        showUpdateDialog: (AppUpdate) -> Unit,
    ) {
        if (latest.versionCode <= currentVersionCode) return
        Log.i(TAG, "Update available: ${latest.versionName} (${latest.versionCode})")
        showUpdateDialog(latest)
    }
}
