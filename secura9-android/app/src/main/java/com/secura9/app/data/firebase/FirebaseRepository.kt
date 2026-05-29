package com.secura9.app.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import com.secura9.app.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    companion object {
        private const val TAG = "FirebaseRepo"
        private const val DEVICE_ID = "secura9_pi_01"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val deviceRef = db.collection("devices").document(DEVICE_ID)
    private val notificationsRef = deviceRef.collection("notifications")
    private val approvalsRef = deviceRef.collection("approvals")
    private val decisionsRef = deviceRef.collection("decisions")
    private val statusDocRef = deviceRef.collection("status").document("live")
    private val logRef = deviceRef.collection("access_log")
    private val fcmTokensRef = deviceRef.collection("fcm_tokens")
    private val webrtcRef = deviceRef.collection("webrtc")
    private val knownFacesRef = deviceRef.collection("known_faces")

    // ── Auth ───────────────────────────────────────────────────────────

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getUserName(): String = auth.currentUser?.displayName ?: "User"

    fun getUserEmail(): String = auth.currentUser?.email ?: ""

    fun getUserPhoto(): String? = auth.currentUser?.photoUrl?.toString()

    fun getUserId(): String = auth.currentUser?.uid ?: ""

    fun signInWithGoogle(idToken: String, onResult: (Boolean, String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, "Signed in")
                } else {
                    onResult(false, task.exception?.message ?: "Auth failed")
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }

    // ── FCM Token ──────────────────────────────────────────────────────

    fun pushPendingFcmToken(context: android.content.Context) {
        val token = context.getSharedPreferences("secura9_prefs", android.content.Context.MODE_PRIVATE)
            .getString("fcm_token", null) ?: return
        storeFcmToken(token)
    }

    fun storeFcmToken(token: String) {
        val userId = getUserId()
        if (userId.isEmpty()) return
        fcmTokensRef.document(userId)
            .set(mapOf("token" to token, "updatedAt" to Timestamp.now()))
            .addOnSuccessListener { Log.d(TAG, "FCM token stored for $userId") }
            .addOnFailureListener { Log.w(TAG, "Failed to store FCM token", it) }
    }

    // ── Real-time listeners (Flow) ─────────────────────────────────────

    fun listenNotifications(): Flow<List<Notification>> = callbackFlow {
        val listener = notificationsRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "Listen error", error)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { doc ->
                    Notification(
                        id = doc.id,
                        type = doc.getString("type") ?: "",
                        title = doc.getString("title") ?: "",
                        body = doc.getString("body") ?: "",
                        data = (doc.get("data") as? Map<String, String>) ?: emptyMap(),
                        timestamp = doc.getTimestamp("timestamp"),
                        read = doc.getBoolean("read") ?: false,
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun listenApprovals(): Flow<List<Approval>> = callbackFlow {
        val listener = approvalsRef
            .whereEqualTo("status", "pending")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val list = snap?.documents?.map { doc ->
                    Approval(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        confidence = doc.getDouble("confidence") ?: 0.0,
                        imageB64 = doc.getString("imageB64") ?: "",
                        status = doc.getString("status") ?: "pending",
                        timestamp = doc.getTimestamp("timestamp"),
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun listenStatus(): Flow<DeviceStatus> = callbackFlow {
        val listener = statusDocRef.addSnapshotListener { snap, error ->
            if (error != null) return@addSnapshotListener
            val status = snap?.toObject(DeviceStatus::class.java) ?: DeviceStatus()
            trySend(status)
        }
        awaitClose { listener.remove() }
    }

    fun listenAccessLog(): Flow<List<AccessLogEntry>> = callbackFlow {
        val listener = logRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val list = snap?.documents?.map { doc ->
                    AccessLogEntry(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        type = doc.getString("type") ?: "",
                        message = doc.getString("message") ?: "",
                        timestamp = doc.getTimestamp("timestamp"),
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun listenKnownFaces(): Flow<List<KnownFace>> = callbackFlow {
        val listener = knownFacesRef
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val list = snap?.documents?.map { doc ->
                    KnownFace(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        imageB64 = doc.getString("imageB64") ?: "",
                        addedAt = doc.getTimestamp("addedAt"),
                        lastSeen = doc.getTimestamp("lastSeen"),
                        visitCount = (doc.getLong("visitCount") ?: 0).toInt(),
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Stats ──────────────────────────────────────────────────────────

    suspend fun getStats(): DeviceStats {
        val today = Timestamp.now()
        val todayStart = Timestamp(today.seconds - today.seconds % 86400, 0)
        return try {
            val totalEvents = logRef.get().await().size()
            val todayEntries = logRef.whereGreaterThan("timestamp", todayStart).get().await()
            val grantedToday = todayEntries.documents.count { it.getString("type") == "granted" }
            val deniedToday = todayEntries.documents.count { it.getString("type") == "denied" }
            val otpToday = todayEntries.documents.count { it.getString("type") == "otp" }
            val motionEvents = logRef.whereEqualTo("type", "motion").get().await().size()
            val knownFaces = knownFacesRef.get().await().size()

            DeviceStats(
                totalEvents = totalEvents,
                grantedToday = grantedToday,
                deniedToday = deniedToday,
                otpToday = otpToday,
                knownFaces = knownFaces,
                motionEvents = motionEvents,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get stats", e)
            DeviceStats()
        }
    }

    // ── WebRTC Signaling ───────────────────────────────────────────────

    fun createWebRTCSession(sessionId: String, offer: org.webrtc.SessionDescription) {
        val session = hashMapOf(
            "offer" to hashMapOf(
                "type" to offer.type.canonicalForm(),
                "sdp" to offer.description,
            ),
            "status" to "waiting",
            "createdAt" to Timestamp.now(),
            "viewer" to getUserId(),
        )
        webrtcRef.document(sessionId).set(session)
            .addOnSuccessListener { Log.d(TAG, "WebRTC session created: $sessionId") }
            .addOnFailureListener { Log.w(TAG, "Failed to create WebRTC session", it) }
    }

    fun listenForAnswer(sessionId: String): Flow<String> = callbackFlow {
        val listener = webrtcRef.document(sessionId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val answer = snap?.getString("answer") ?: ""
                if (answer.isNotEmpty()) {
                    trySend(answer)
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenForPiIce(sessionId: String): Flow<Map<String, Any?>> = callbackFlow {
        val listener = webrtcRef.document(sessionId).collection("piIce")
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                snap?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        trySend(mapOf(
                            "sdpMid" to doc.getString("sdpMid"),
                            "sdpMLineIndex" to doc.getLong("sdpMLineIndex"),
                            "candidate" to doc.getString("candidate"),
                        ))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    fun sendViewerIceCandidate(sessionId: String, candidate: org.webrtc.IceCandidate) {
        val data = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex.toLong(),
            "candidate" to candidate.sdp,
            "timestamp" to Timestamp.now(),
        )
        webrtcRef.document(sessionId).collection("viewerIce")
            .add(data)
            .addOnFailureListener { Log.w(TAG, "Failed to send ICE candidate", it) }
    }

    fun deleteWebRTCSession(sessionId: String) {
        webrtcRef.document(sessionId).delete()
            .addOnSuccessListener { Log.d(TAG, "WebRTC session deleted: $sessionId") }
    }

    // ── Actions ───────────────────────────────────────────────────────

    fun approveFace(approvalId: String, name: String) {
        approvalsRef.document(approvalId).update("status", "approved")
        val decision = hashMapOf(
            "decision" to "approve",
            "name" to name,
            "approvalId" to approvalId,
        )
        decisionsRef.add(decision)
    }

    fun denyFace(approvalId: String) {
        approvalsRef.document(approvalId).update("status", "denied")
        val decision = hashMapOf(
            "decision" to "deny",
            "approvalId" to approvalId,
        )
        decisionsRef.add(decision)
    }

    fun unlockDoor() {
        decisionsRef.add(hashMapOf("decision" to "unlock"))
    }

    fun lockDoor() {
        decisionsRef.add(hashMapOf("decision" to "lock"))
    }

    fun toggleNobodyHome(active: Boolean) {
        val decision = hashMapOf(
            "decision" to "nobody_home",
            "active" to active,
        )
        decisionsRef.add(decision)
    }

    fun markNotificationRead(notifId: String) {
        notificationsRef.document(notifId).update("read", true)
    }

    // ── In-App Updates ────────────────────────────────────────────────

    fun listenForUpdates(): Flow<AppUpdate> = callbackFlow {
        val ref = db.collection("app").document("updates").collection("versions")
            .orderBy("versionCode", Query.Direction.DESCENDING).limit(1)
        val listener = ref.addSnapshotListener { snap, error ->
            if (error != null) return@addSnapshotListener
            val doc = snap?.documents?.firstOrNull()
            val update = doc?.toObject(AppUpdate::class.java) ?: AppUpdate()
            trySend(update)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getLatestUpdate(): AppUpdate {
        return try {
            val snap = db.collection("app").document("updates")
                .collection("versions")
                .orderBy("versionCode", Query.Direction.DESCENDING)
                .limit(1)
                .get().await()
            snap.documents.firstOrNull()?.toObject(AppUpdate::class.java) ?: AppUpdate()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get latest update", e)
            AppUpdate()
        }
    }
}
