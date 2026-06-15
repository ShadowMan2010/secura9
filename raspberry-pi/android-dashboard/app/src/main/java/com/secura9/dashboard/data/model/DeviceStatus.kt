package com.secura9.dashboard.data.model

data class DeviceStatus(
    val online: Boolean = false,
    val doorLocked: Boolean = true,
    val nobodyHome: Boolean = false,
    val displayState: String = "IDLE",
    val camera: Boolean = false,
    val engine: Boolean = false,
    val webrtc: String = "idle",
    val passageMode: Boolean = false,
    val otaAvailable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

data class Event(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, Any> = emptyMap(),
)

data class ApprovalRequest(
    val name: String = "",
    val confidence: Double = 0.0,
    val imageBase64: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

data class OTPData(
    val otp: String = "",
    val expiresIn: Int = 90,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ClipInfo(
    val filename: String = "",
    val path: String = "",
    val size: Long = 0,
    val mtime: Long = 0,
)
