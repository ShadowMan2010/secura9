package com.secura9.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.data.model.DeviceStats
import com.secura9.app.data.model.DeviceStatus
import com.secura9.app.ui.components.*
import com.secura9.app.ui.theme.*
import com.secura9.app.utils.SoundManager

@Composable
fun DashboardScreen(repository: FirebaseRepository) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val status by repository.listenStatus().collectAsState(initial = DeviceStatus())
    var stats by remember { mutableStateOf(DeviceStats()) }
    var nobodyHome by remember { mutableStateOf(false) }

    LaunchedEffect(status.nobodyHome) {
        nobodyHome = status.nobodyHome
    }

    LaunchedEffect(Unit) {
        stats = repository.getStats()
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            stats = repository.getStats()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        CyberGrid(color = Cyan)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            GlitchText(
                text = "WELCOME, ${repository.getUserName().uppercase()}",
                fontSize = 12,
                letterSpacing = 4,
                color = Muted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(4.dp))

            NeonDivider(color = Cyan, modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(8.dp))

            Text(
                text = "\u2B21 SYSTEM STATUS",
                fontSize = 10.sp,
                color = Cyan,
                letterSpacing = 4.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBox(
                    value = if (status.camera) "ON" else "OFF",
                    label = "CAMERA",
                    color = if (status.camera) Green else Red,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = if (status.engine) "ON" else "OFF",
                    label = "FACE ENGINE",
                    color = if (status.engine) Green else Red,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBox(
                    value = if (status.doorLocked) "LOCKED" else "OPEN",
                    label = "DOOR",
                    color = if (status.doorLocked) Cyan else Green,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = if (status.nobodyHome) "ACTIVE" else "OFF",
                    label = "NOBODY HOME",
                    color = if (status.nobodyHome) Red else Muted,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("STATISTICS")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBox(
                    value = stats.grantedToday.toString(),
                    label = "GRANTED",
                    color = Green,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = stats.deniedToday.toString(),
                    label = "DENIED",
                    color = Red,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = stats.otpToday.toString(),
                    label = "OTP",
                    color = Yellow,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatBox(
                    value = stats.knownFaces.toString(),
                    label = "KNOWN FACES",
                    color = Cyan,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    value = stats.totalEvents.toString(),
                    label = "TOTAL EVENTS",
                    color = Cyan,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("RECENT NOTIFICATIONS")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(4.dp),
            ) {
                val notifs by repository.listenNotifications()
                    .collectAsState(initial = emptyList())
                val recent = notifs.take(5)

                if (recent.isEmpty()) {
                    Text(
                        text = "No notifications yet",
                        fontSize = 9.sp,
                        color = Muted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    recent.forEach { notif ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(
                                color = when (notif.type) {
                                    "otp" -> Yellow
                                    "approval" -> Red
                                    "access" -> Green
                                    "motion" -> Cyan
                                    else -> Muted
                                },
                                size = 6,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    notif.title,
                                    fontSize = 11.sp,
                                    color = Text,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                                Text(
                                    notif.body,
                                    fontSize = 8.sp,
                                    color = Muted,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                        }
                        if (notif != recent.last()) {
                            HorizontalDivider(color = Muted.copy(alpha = 0.2f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("DEVICE STATUS")
            CyberCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    StatusRow("Door", if (status.doorLocked) "LOCKED" else "UNLOCKED",
                        if (status.doorLocked) Green else Cyan)
                    StatusRow("Display", status.displayState.ifEmpty { "IDLE" }, Cyan)
                    StatusRow("Motion", if (status.motion) "YES" else "NO",
                        if (status.motion) Yellow else Muted)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("QUICK ACTIONS")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NeonButton(
                    text = "UNLOCK",
                    onClick = {
                        soundManager.playDoorOpen()
                        repository.unlockDoor()
                    },
                    color = Green,
                    modifier = Modifier.weight(1f),
                )
                NeonButton(
                    text = "LOCK",
                    onClick = {
                        soundManager.playDoorClose()
                        repository.lockDoor()
                    },
                    color = Red,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NeonButton(
                    text = if (nobodyHome) "NOBODY HOME: ON" else "NOBODY HOME: OFF",
                    onClick = {
                        nobodyHome = !nobodyHome
                        if (nobodyHome) soundManager.playAlert() else soundManager.playClick()
                        repository.toggleNobodyHome(nobodyHome)
                    },
                    color = if (nobodyHome) Red else Muted,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 9.sp, color = Muted,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, fontSize = 9.sp, color = valueColor,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
