package com.secura9.dashboard.data.firebase

import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.secura9.dashboard.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    companion object {
        private const val TAG = "FirebaseRepo"
        private const val DEVICE_ID = "secura9_pi_01"
        private const val DEVICES_COLLECTION = "devices"
    }

    private val db = FirebaseFirestore.getInstance()

    private val _status = MutableStateFlow(DeviceStatus())
    val status: StateFlow<DeviceStatus> = _status

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval

    private val _otp = MutableStateFlow<OTPData?>(null)
    val otp: StateFlow<OTPData?> = _otp

    private val _clips = MutableStateFlow<List<ClipInfo>>(emptyList())
    val clips: StateFlow<List<ClipInfo>> = _clips

    private var statusListener: () -> Unit = {}
    private var notificationListener: () -> Unit = {}
    private var approvalListener: () -> Unit = {}
    private var otpListener: () -> Unit = {}

    fun startListening() {
        listenDeviceStatus()
        listenNotifications()
        listenApprovals()
        listenOTP()
        registerFCM()
    }

    fun stopListening() {
        statusListener()
        notificationListener()
        approvalListener()
        otpListener()
    }

    private fun listenDeviceStatus() {
        statusListener = db.collection(DEVICES_COLLECTION)
            .document(DEVICE_ID)
            .collection("status")
            .document("live")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                if (e != null) {
                    Log.w(TAG, "Status listener error", e)
                    return@addSnapshotListener
                }
                snap?.let { doc ->
                    val data = doc.data
                    if (data != null) {
                        _status.value = DeviceStatus(
                            online = data["online"] as? Boolean ?: false,
                            doorLocked = data["doorLocked"] as? Boolean ?: true,
                            nobodyHome = data["nobodyHome"] as? Boolean ?: false,
                            displayState = data["displayState"] as? String ?: "IDLE",
                            camera = data["camera"] as? Boolean ?: false,
                            engine = data["engine"] as? Boolean ?: false,
                            webrtc = data["webrtc"] as? String ?: "idle",
                            passageMode = data["passageMode"] as? Boolean ?: false,
                            otaAvailable = data["otaAvailable"] as? Boolean ?: false,
                            timestamp = (data["lastUpdated"] as? com.google.firebase.Timestamp)
                                ?.toDate()?.time ?: System.currentTimeMillis(),
                        )
                    }
                }
            }
    }

    private fun listenNotifications() {
        notificationListener = db.collection(DEVICES_COLLECTION)
            .document(DEVICE_ID)
            .collection("notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                val items = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Event(
                        id = doc.id,
                        type = d["type"] as? String ?: "",
                        title = d["title"] as? String ?: "",
                        body = d["body"] as? String ?: "",
                        timestamp = (d["timestamp"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: 0,
                        data = d.filterKeys { it !in setOf("type", "title", "body", "timestamp") },
                    )
                }
                _events.value = items
            }
    }

    private fun listenApprovals() {
        approvalListener = db.collection(DEVICES_COLLECTION)
            .document(DEVICE_ID)
            .collection("notifications")
            .whereEqualTo("type", "approval_request")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                val doc = snap.documents.firstOrNull() ?: run {
                    _pendingApproval.value = null
                    return@addSnapshotListener
                }
                val d = doc.data ?: return@addSnapshotListener
                _pendingApproval.value = ApprovalRequest(
                    name = d["name"] as? String ?: "Unknown",
                    confidence = d["confidence"] as? Double ?: 0.0,
                    imageBase64 = d["image"] as? String ?: "",
                    timestamp = (d["timestamp"] as? com.google.firebase.Timestamp)
                        ?.toDate()?.time ?: 0,
                )
            }
    }

    private fun listenOTP() {
        otpListener = db.collection(DEVICES_COLLECTION)
            .document(DEVICE_ID)
            .collection("notifications")
            .whereEqualTo("type", "otp")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                val doc = snap.documents.firstOrNull() ?: return@addSnapshotListener
                val d = doc.data ?: return@addSnapshotListener
                _otp.value = OTPData(
                    otp = d["otp"] as? String ?: "",
                    expiresIn = (d["expiresIn"] as? Long)?.toInt() ?: 90,
                    timestamp = (d["timestamp"] as? com.google.firebase.Timestamp)
                        ?.toDate()?.time ?: 0,
                )
            }
    }

    private fun registerFCM() {
        FirebaseMessaging.getInstance().subscribeToTopic(DEVICE_ID)
            .addOnSuccessListener { Log.i(TAG, "Subscribed to $DEVICE_ID topic") }
    }

    // ── Actions ──────────────────────────────────────────────────────────

    suspend fun sendCommand(command: String, data: Map<String, Any> = emptyMap()) {
        try {
            val payload = mutableMapOf<String, Any>(
                "command" to command,
                "timestamp" to FieldValue.serverTimestamp(),
            )
            payload.putAll(data)
            db.collection(DEVICES_COLLECTION)
                .document(DEVICE_ID)
                .collection("commands")
                .add(payload)
                .await()
            Log.i(TAG, "Command sent: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: $command", e)
        }
    }

    suspend fun approveRequest(name: String) {
        try {
            val snapshot = db.collection(DEVICES_COLLECTION)
                .document(DEVICE_ID)
                .collection("notifications")
                .whereEqualTo("type", "approval_request")
                .whereEqualTo("status", "pending")
                .get(Source.SERVER)
                .await()
            for (doc in snapshot.documents) {
                doc.reference.update("status", "approved", "name", name).await()
            }
            _pendingApproval.value = null
            Log.i(TAG, "Approved: $name")
        } catch (e: Exception) {
            Log.e(TAG, "Approve failed", e)
        }
    }

    suspend fun denyRequest() {
        try {
            val snapshot = db.collection(DEVICES_COLLECTION)
                .document(DEVICE_ID)
                .collection("notifications")
                .whereEqualTo("type", "approval_request")
                .whereEqualTo("status", "pending")
                .get(Source.SERVER)
                .await()
            for (doc in snapshot.documents) {
                doc.reference.update("status", "denied").await()
            }
            _pendingApproval.value = null
            Log.i(TAG, "Denied")
        } catch (e: Exception) {
            Log.e(TAG, "Deny failed", e)
        }
    }

    suspend fun setNobodyHome(active: Boolean) {
        sendCommand("set_nobody_home", mapOf("active" to active))
    }

    suspend fun unlockDoor() {
        sendCommand("unlock")
    }

    suspend fun lockDoor() {
        sendCommand("lock")
    }

    suspend fun generateOTP() {
        sendCommand("generate_otp")
    }

    suspend fun loadClips(): List<ClipInfo> {
        return try {
            val snapshot = db.collection(DEVICES_COLLECTION)
                .document(DEVICE_ID)
                .collection("clips")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get(Source.SERVER)
                .await()
            val items = snapshot.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                ClipInfo(
                    filename = d["filename"] as? String ?: "",
                    path = d["path"] as? String ?: "",
                    size = d["size"] as? Long ?: 0,
                    mtime = (d["timestamp"] as? com.google.firebase.Timestamp)
                        ?.toDate()?.time ?: 0,
                )
            }
            _clips.value = items
            items
        } catch (e: Exception) {
            Log.e(TAG, "loadClips failed", e)
            emptyList()
        }
    }

    suspend fun getWebRTCSession(): String? {
        return try {
            val snapshot = db.collection(DEVICES_COLLECTION)
                .document(DEVICE_ID)
                .collection("webrtc")
                .whereEqualTo("status", "active")
                .limit(1)
                .get(Source.SERVER)
                .await()
            snapshot.documents.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e(TAG, "getWebRTCSession failed", e)
            null
        }
    }

    // ── New Actions ───────────────────────────────────────────────────────

    suspend fun unlockDuringCall(sessionId: String) {
        try {
            db.collection(DEVICES_COLLECTION)
                .document(DEVICE_ID)
                .collection("webrtc")
                .document(sessionId)
                .update("unlockRequest", true)
                .await()
            Log.i(TAG, "Unlock request sent during call")
        } catch (e: Exception) {
            Log.e(TAG, "Unlock during call failed", e)
        }
    }

    suspend fun passageOn() {
        sendCommand("passage_on")
    }

    suspend fun passageOff() {
        sendCommand("passage_off")
    }

    suspend fun generateTimedCode(durationSeconds: Int = 300, label: String = "App") {
        sendCommand("generate_timed_code", mapOf(
            "duration" to durationSeconds,
            "label" to label,
        ))
    }

    suspend fun checkOTA() {
        sendCommand("ota_check")
    }

    suspend fun applyOTA() {
        sendCommand("ota_apply")
    }

    suspend fun updateSchedule(rules: List<Map<String, Any>>) {
        sendCommand("update_schedule", mapOf("rules" to rules))
    }
}
