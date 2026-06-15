package com.secura9.dashboard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class Secura9App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    "approvals", "Approval Requests",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Face recognition approval requests" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    "alerts", "Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Tamper, motion, and security alerts" }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    "status", "Status Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "System status changes" }
            )
        }
    }
}
