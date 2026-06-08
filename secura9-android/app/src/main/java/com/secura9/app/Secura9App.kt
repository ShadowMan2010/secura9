package com.secura9.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.data.model.AppUpdate
import com.secura9.app.utils.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Secura9App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var updateManager: UpdateManager

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        updateManager = UpdateManager(this)

        scope.launch {
            checkForUpdates()
        }
    }

    private suspend fun checkForUpdates() {
        val repo = FirebaseRepository()
        val latest = repo.getLatestUpdate()
        val currentVersion = BuildConfig.VERSION_CODE

        updateManager.checkAndUpdate(currentVersion, latest) { update ->
            showUpdateNotification(update)
        }
    }

    private fun showUpdateNotification(update: AppUpdate) {
        val channelId = "app_update"
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("show_update", true)
            putExtra("update_version", update.versionName)
            putExtra("update_apk_url", update.apkUrl)
            putExtra("update_changelog", update.changelog)
            putExtra("update_force", update.forceUpdate)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("\u2B06\uFE0F Update Available: v${update.versionName}")
            .setContentText("\uD83D\uDCE6 ${update.changelog.take(80)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\uD83D\uDCE6 ${update.changelog}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(2001, notification)
    }
}
