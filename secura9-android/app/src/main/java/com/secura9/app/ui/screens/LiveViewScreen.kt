package com.secura9.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.ui.components.*
import com.secura9.app.ui.theme.*
import com.secura9.app.utils.SoundManager
import com.secura9.app.webrtc.WebRTCClient
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.util.UUID

@Composable
fun LiveViewScreen(repository: FirebaseRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundManager = remember { SoundManager(context) }
    var webRTCClient by remember { mutableStateOf<WebRTCClient?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isTalkActive by remember { mutableStateOf(false) }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var currentVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }

    val camPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPerm = granted }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPerm = granted }

    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val redAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec"
    )

    DisposableEffect(Unit) {
        onDispose {
            webRTCClient?.release()
            webRTCClient = null
            soundManager.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp)
            .padding(bottom = 80.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Dim)
                .border(1.dp, Cyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (isConnected && currentVideoTrack != null) {
                var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

                AndroidView(
                    factory = { ctx ->
                        val eglCtx = webRTCClient?.getEglBaseContext()
                        SurfaceViewRenderer(ctx).apply {
                            if (eglCtx != null) {
                                init(eglCtx, null)
                            }
                            setMirror(false)
                            setEnableHardwareScaler(true)
                            currentVideoTrack?.addSink(this)
                            renderer = this
                        }
                    },
                    update = { view ->
                        currentVideoTrack?.let { track ->
                            track.addSink(view)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Bg.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Red.copy(alpha = redAlpha))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "LIVE",
                        fontSize = 9.sp,
                        color = Red.copy(alpha = redAlpha),
                        letterSpacing = 3.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Red.copy(alpha = redAlpha))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isConnecting) "CONNECTING" else "OFFLINE",
                            fontSize = 10.sp,
                            color = if (isConnecting) Yellow else Red.copy(alpha = redAlpha),
                            letterSpacing = 3.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }

                    Text(
                        text = "\uD83D\uDCF7",
                        fontSize = 48.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "CAMERA FEED",
                        fontSize = 10.sp,
                        color = Muted,
                        letterSpacing = 3.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Text(
                        text = if (isConnecting) "Establishing WebRTC connection..."
                        else "Tap CONNECT to view live feed",
                        fontSize = 8.sp,
                        color = Muted.copy(alpha = 0.6f),
                        letterSpacing = 1.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    if (isConnecting) {
                        Spacer(Modifier.height(16.dp))
                        LoadingHex()
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NeonButton(
                text = if (isConnected) "DISCONNECT" else "CONNECT",
                onClick = {
                    if (isConnected) {
                        webRTCClient?.release()
                        webRTCClient = null
                        isConnected = false
                        currentVideoTrack = null
                        soundManager.playDoorClose()
                    } else {
                        if (!hasCameraPerm) {
                            camPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                        if (!hasAudioPerm) {
                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        isConnecting = true
                        soundManager.playClick()
                        val sessionId = "android_${UUID.randomUUID().toString().take(8)}"
                        val client = WebRTCClient(context, repository, sessionId)
                        webRTCClient = client
                        client.initialize(scope)

                        scope.launch {
                            client.connectionState.collect { state ->
                                val connected = state == PeerConnection.PeerConnectionState.CONNECTED
                                isConnected = connected
                                isConnecting = state == PeerConnection.PeerConnectionState.CONNECTING
                                if (connected) soundManager.playDoorOpen()
                            }
                        }
                        scope.launch {
                            client.remoteVideoTrack.collect { track ->
                                currentVideoTrack = track
                            }
                        }
                    }
                },
                color = if (isConnected) Red else Cyan,
                modifier = Modifier.weight(1f),
            )

            NeonButton(
                text = if (isTalkActive) "TALK: ON" else "TALK: OFF",
                onClick = {
                    if (!hasAudioPerm) {
                        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@NeonButton
                    }
                    webRTCClient?.let { client ->
                        isTalkActive = client.toggleTalk()
                        if (isTalkActive) soundManager.playSuccess()
                        else soundManager.playClick()
                    }
                },
                color = if (isTalkActive) Green else Muted,
                enabled = isConnected && hasAudioPerm,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        CyberCard {
            Column(modifier = Modifier.padding(12.dp)) {
                val connText = if (isConnected) "CONNECTED" else if (isConnecting) "CONNECTING..." else "DISCONNECTED"
                val connColor = if (isConnected) Green else if (isConnecting) Yellow else Muted

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = connColor, size = 6, animate = isConnecting)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        connText,
                        fontSize = 9.sp,
                        color = connColor,
                        letterSpacing = 2.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Device: secura9_pi_01",
                    fontSize = 8.sp,
                    color = Muted,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(
                    "Two-way talk: ${if (isTalkActive) "ACTIVE" else "INACTIVE"}",
                    fontSize = 8.sp,
                    color = if (isTalkActive) Green else Muted,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}
