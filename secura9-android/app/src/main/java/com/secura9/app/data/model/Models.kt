package com.secura9.app.data.model

import com.google.firebase.Timestamp

data class Notification(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val data: Map<String, String> = emptyMap(),
    val timestamp: Timestamp? = null,
    val read: Boolean = false,
)

data class Approval(
    val id: String = "",
    val name: String = "",
    val confidence: Double = 0.0,
    val imageB64: String = "",
    val status: String = "pending",
    val timestamp: Timestamp? = null,
)

data class DeviceStatus(
    val camera: Boolean = false,
    val engine: Boolean = false,
    val doorLocked: Boolean = true,
    val nobodyHome: Boolean = false,
    val displayState: String = "",
    val motion: Boolean = false,
    val lastSeen: Timestamp? = null,
)

data class AccessLogEntry(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null,
)

data class KnownFace(
    val id: String = "",
    val name: String = "",
    val imageB64: String = "",
    val addedAt: Timestamp? = null,
    val lastSeen: Timestamp? = null,
    val visitCount: Int = 0,
)

data class DeviceStats(
    val totalEvents: Int = 0,
    val grantedToday: Int = 0,
    val deniedToday: Int = 0,
    val otpToday: Int = 0,
    val knownFaces: Int = 0,
    val motionEvents: Int = 0,
)

data class WebRTCSession(
    val sessionId: String = "",
    val offer: String = "",
    val answer: String = "",
    val status: String = "",
    val createdAt: Timestamp? = null,
)

data class AppUpdate(
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    val releaseDate: Timestamp? = null,
    val minSdkVersion: Int = 26,
    val forceUpdate: Boolean = false,
)
