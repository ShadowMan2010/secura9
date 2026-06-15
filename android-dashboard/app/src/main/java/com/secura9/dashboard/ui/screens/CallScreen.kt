package com.secura9.dashboard.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secura9.dashboard.ui.components.AnimatedButton
import com.secura9.dashboard.ui.components.PulsingIndicator
import com.secura9.dashboard.ui.theme.*
import com.secura9.dashboard.viewmodel.DashboardViewModel

@Composable
fun CallScreen(
    sessionId: String,
    onEnd: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    var callDuration by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            callDuration++
        }
    }

    val recTransition = rememberInfiniteTransition()
    val pulse by recTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "recPulse"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Video area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .background(BgPanel2),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Videocam,
                        "Camera",
                        modifier = Modifier.size(48.dp),
                        tint = TextMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Live Feed",
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                }
                // Recording indicator
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingIndicator(active = true, activeColor = Red, size = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "LIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Red.copy(alpha = pulse),
                        letterSpacing = 2.sp,
                    )
                }
                // Duration
                Text(
                    text = "%02d:%02d".format(callDuration / 60, callDuration % 60),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Visitor info
            Text(
                "Visitor at door",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                "Session: ${sessionId.take(8)}...",
                fontSize = 12.sp,
                color = TextSecondary,
            )

            Spacer(Modifier.weight(1f))

            // Unlock button
            AnimatedButton(
                text = "UNLOCK DOOR",
                icon = Icons.Default.LockOpen,
                onClick = { viewModel.unlockDuringCall(sessionId) },
                color = Green,
                textColor = BgDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                height = 56.dp,
            )

            Spacer(Modifier.height(16.dp))

            // End call
            IconButton(
                onClick = onEnd,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Red.copy(alpha = 0.2f)),
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    "End Call",
                    tint = Red,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
