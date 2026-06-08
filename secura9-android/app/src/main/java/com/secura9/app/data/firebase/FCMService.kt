package com.secura9.app.data.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.secura9.app.MainActivity
import java.util.concurrent.atomic.AtomicInteger

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val DEVICE_ID = "secura9_pi_01"
        private val notifIdCounter = AtomicInteger(1000)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        getSharedPreferences("secura9_prefs", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
        storeFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val notif = message.notification

        val title = notif?.title ?: data["title"] ?: "SECURA-9"
        val body = notif?.body ?: data["body"] ?: "Door event"
        val type = data["type"] ?: "alert"

        createNotificationChannel(type)
        showNotification(title, body, type)
    }

    private fun createNotificationChannel(type: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "secura9_$type"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(channelId)
            if (existing != null) return
            val importance = when (type) {
                "approval", "otp" -> NotificationManager.IMPORTANCE_HIGH
                "access" -> NotificationManager.IMPORTANCE_DEFAULT
                else -> NotificationManager.IMPORTANCE_LOW
            }
            val channel = NotificationChannel(
                channelId,
                when (type) {
                    "approval" -> "Approval Requests"
                    "otp" -> "OTP Codes"
                    "access" -> "Door Access"
                    "motion" -> "Motion Alerts"
                    else -> "SECURA-9 Alerts"
                },
                importance,
            ).apply {
                description = when (type) {
                    "approval" -> "Face recognition approval requests"
                    "otp" -> "OTP codes for door access"
                    "access" -> "Door grant and deny events"
                    "motion" -> "Motion detection alerts"
                    else -> "General SECURA-9 alerts"
                }
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun storeFcmToken(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("devices")
            .document(DEVICE_ID)
            .collection("fcm_tokens")
            .document(userId)
            .set(mapOf("token" to token, "updatedAt" to com.google.firebase.Timestamp.now()))
            .addOnSuccessListener { Log.d(TAG, "FCM token stored for user $userId") }
            .addOnFailureListener { Log.w(TAG, "Failed to store FCM token", it) }
    }

    private fun showNotification(title: String, body: String, type: String) {
        val channelId = "secura9_$type"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notifId = notifIdCounter.incrementAndGet()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val icon = when (type) {
            "approval" -> android.R.drawable.ic_menu_myplaces
            "otp" -> android.R.drawable.ic_menu_manage
            "access" -> android.R.drawable.ic_menu_gallery
            "motion" -> android.R.drawable.ic_menu_compass
            else -> android.R.drawable.ic_dialog_info
        }

        val priority = when (type) {
            "approval", "otp" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(notifId, notification)
    }
}
