package com.secura9.app.webrtc

import android.content.Context
import android.util.Log
import com.secura9.app.data.firebase.FirebaseRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*

class WebRTCClient(
    private val context: Context,
    private val repository: FirebaseRepository,
    private val sessionId: String,
) {
    companion object {
        private const val TAG = "WebRTCClient"
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
    }

    private var peerConnection: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var remoteVideoTrackValue: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var isTalkEnabled = false

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val pendingIceCandidates = java.util.concurrent.ConcurrentLinkedQueue<IceCandidate>()
    private var signalingJob: Job? = null

    fun initialize(scope: CoroutineScope) {
        signalingJob = Job()
        val innerScope = scope + signalingJob!!
        PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("")
            .createInitializationOptions()
            .let { PeerConnectionFactory.initialize(it) }

        eglBase = EglBase.create()

        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)
        val videoEncoderFactory = DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .createPeerConnectionFactory()

        createPeerConnection()
        startSignaling(innerScope)
    }

    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    private fun createPeerConnection() {
        val config = PeerConnection.RTCConfiguration(ICE_SERVERS)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = factory?.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Local ICE candidate: $candidate")
                repository.sendViewerIceCandidate(sessionId, candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d(TAG, "Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE connection state: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "ICE gathering state: $state")
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "Connection state: $state")
                _connectionState.value = state
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "onAddStream: ${stream.videoTracks.size} video, ${stream.audioTracks.size} audio")
                stream.videoTracks.firstOrNull()?.let { track ->
                    remoteVideoTrackValue = track
                    _remoteVideoTrack.value = track
                }
            }

            override fun onAddTrack(track: RtpReceiver, streams: Array<out MediaStream>) {
                Log.d(TAG, "onAddTrack: ${track.track()?.kind()}")
                if (track.track() is VideoTrack) {
                    remoteVideoTrackValue = track.track() as VideoTrack
                    _remoteVideoTrack.value = track.track() as VideoTrack
                }
            }

            override fun onRemoveStream(stream: MediaStream) {
                Log.d(TAG, "onRemoveStream")
                remoteVideoTrackValue = null
                _remoteVideoTrack.value = null
            }

            override fun onDataChannel(channel: DataChannel) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed — creating new offer")
                val mediaConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                peerConnection?.createOffer(object : SdpObserverAdapter("renegOffer") {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        peerConnection?.setLocalDescription(object : SdpObserverAdapter("renegLocal") {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Renegotiated local description set")
                                sessionDescription.description.let { sdp ->
                                    repository.createWebRTCSession(sessionId, sessionDescription)
                                }
                            }
                        }, sessionDescription)
                    }
                }, mediaConstraints)
            }

            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        })
    }

    private fun startSignaling(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val mediaConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            }

            peerConnection?.createOffer(object : SdpObserverAdapter("createOffer") {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    peerConnection?.setLocalDescription(object : SdpObserverAdapter("setLocal") {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set, writing offer to Firestore")
                            repository.createWebRTCSession(sessionId, sessionDescription)
                        }
                    }, sessionDescription)
                }
            }, mediaConstraints)

            repository.listenForAnswer(sessionId).collect { answer ->
                if (answer.isNotEmpty()) {
                    Log.d(TAG, "Received answer from Pi")
                    val sd = SessionDescription(SessionDescription.Type.ANSWER, answer)
                    peerConnection?.setRemoteDescription(object : SdpObserverAdapter("setRemote") {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set")
                            while (true) {
                                val candidate = pendingIceCandidates.poll() ?: break
                                peerConnection?.addIceCandidate(candidate)
                            }
                        }
                    }, sd)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            repository.listenForPiIce(sessionId).collect { data ->
                val sdpMid = data["sdpMid"] as? String ?: "0"
                val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                val candidateStr = data["candidate"] as? String ?: ""
                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)

                if (peerConnection?.remoteDescription != null) {
                    peerConnection?.addIceCandidate(iceCandidate)
                } else {
                    pendingIceCandidates.offer(iceCandidate)
                }
            }
        }
    }

    fun toggleTalk(): Boolean {
        isTalkEnabled = !isTalkEnabled
        if (isTalkEnabled) {
            enableAudioTrack()
        } else {
            disableAudioTrack()
        }
        return isTalkEnabled
    }

    fun isTalkActive(): Boolean = isTalkEnabled

    private fun enableAudioTrack() {
        if (localAudioTrack != null) {
            localAudioTrack?.setEnabled(true)
            return
        }

        val constraints = MediaConstraints()
        audioSource = factory?.createAudioSource(constraints)
        localAudioTrack = factory?.createAudioTrack("secura9_audio", audioSource)

        localAudioTrack?.let { track ->
            val mediaStream = factory?.createLocalMediaStream("localAudio")
            mediaStream?.addTrack(track)

            peerConnection?.addTrack(track, listOf("stream_secura9_audio"))
            Log.d(TAG, "Audio track added for two-way talk")
        }
    }

    private fun disableAudioTrack() {
        localAudioTrack?.setEnabled(false)
        Log.d(TAG, "Audio track disabled")
    }

    fun release() {
        signalingJob?.cancel()
        signalingJob = null
        disableAudioTrack()
        localAudioTrack = null
        audioSource = null
        remoteVideoTrackValue = null
        _remoteVideoTrack.value = null
        peerConnection?.close()
        peerConnection?.dispose()
        factory?.dispose()
        eglBase?.release()
        repository.deleteWebRTCSession(sessionId)
    }

    private open class SdpObserverAdapter(val name: String) : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription) {}
        override fun onSetSuccess() {
            Log.d(TAG, "SDP $name success")
        }
        override fun onCreateFailure(error: String) {
            Log.e(TAG, "SDP $name create failure: $error")
        }
        override fun onSetFailure(error: String) {
            Log.e(TAG, "SDP $name set failure: $error")
        }
    }
}
