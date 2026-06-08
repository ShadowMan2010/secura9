package com.secura9.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.ui.theme.*
import com.secura9.app.utils.SoundManager
import kotlinx.coroutines.delay

data class BootLine(val text: String, val color: Color = Cyan, val delay: Long = 0L)

private val bootLines = listOf(
    BootLine("SECURA-9 v1.0.0", Cyan, 0),
    BootLine("", Muted, 40),
    BootLine("[  OK  ] Initializing kernel modules", Green, 80),
    BootLine("[  OK  ] Mounting Firebase Firestore", Green, 140),
    BootLine("[  OK  ] Establishing secure tunnel", Green, 200),
    BootLine("[  OK  ] Loading face recognition engine", Green, 260),
    BootLine("[  OK  ] Calibrating camera sensors", Green, 320),
    BootLine("[  OK  ] Starting WebRTC broadcaster", Green, 380),
    BootLine("[  OK  ] Initializing audio subsystem", Green, 420),
    BootLine("[ WARN ] Nobody Home mode: INACTIVE", Yellow, 480),
    BootLine("[  OK  ] Syncing known face database", Green, 520),
    BootLine("[  OK  ] Loading access control lists", Green, 580),
    BootLine("", Muted, 620),
    BootLine("SYSTEM READY  --  SECURA-9", Cyan, 660),
)

@Composable
fun BootScreen(onBootComplete: () -> Unit) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    var visibleLines by remember { mutableIntStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "boot")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorBlink"
    )
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        soundManager.playBootSequence()
        for (i in bootLines.indices) {
            delay(bootLines[i].delay + 40)
            visibleLines = i + 1
        }
        soundManager.playSystemReady()
        delay(400)
        soundManager.release()
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "SECURA-9",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan,
                letterSpacing = 8.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                "DOOR ACCESS SYSTEM",
                fontSize = 9.sp,
                color = Muted,
                letterSpacing = 4.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Muted.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Cyan, Green)
                            )
                        )
                )
            }

            Spacer(Modifier.height(20.dp))

            bootLines.take(visibleLines).forEach { line ->
                Text(
                    line.text,
                    fontSize = 10.sp,
                    color = line.color,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            if (visibleLines < bootLines.size) {
                Text(
                    "_",
                    fontSize = 10.sp,
                    color = Cyan.copy(alpha = cursorAlpha),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Initializing...",
                fontSize = 8.sp,
                color = Muted.copy(alpha = 0.5f),
                letterSpacing = 2.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.alpha(if (visibleLines < bootLines.size) 1f else 0f),
            )
        }
    }
}
