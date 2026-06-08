package com.secura9.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.ui.components.*
import com.secura9.app.ui.theme.*
import com.secura9.app.utils.SoundManager

@Composable
fun ApprovalsScreen(repository: FirebaseRepository) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val approvals by repository.listenApprovals().collectAsState(initial = emptyList())
    var prevCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(approvals.size) {
        if (approvals.size > prevCount && prevCount > 0) {
            soundManager.playApprovalReceived()
        }
        if (approvals.isNotEmpty()) {
            soundManager.startApprovalChime()
        } else {
            soundManager.stopApprovalChime()
        }
        prevCount = approvals.size
    }

    DisposableEffect(Unit) {
        onDispose {
            soundManager.stopApprovalChime()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "approval")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alertPulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(bottom = 80.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                title = "APPROVAL QUEUE",
                badge = if (approvals.isNotEmpty()) "${approvals.size} PENDING" else null,
            )

            if (approvals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadarAnimation(color = Cyan, size = 80)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "\uD83D\uDC41",
                            fontSize = 36.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "NO PENDING APPROVALS",
                            fontSize = 10.sp,
                            color = Muted,
                            letterSpacing = 2.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Waiting for new face detection",
                            fontSize = 8.sp,
                            color = Muted.copy(alpha = 0.6f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Red.copy(alpha = pulseAlpha * 0.15f))
                        .border(0.5.dp, Red.copy(alpha = pulseAlpha * 0.5f), RoundedCornerShape(2.dp))
                        .padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(color = Red, size = 8)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "${approvals.size} APPROVAL${if (approvals.size != 1) "S" else ""} PENDING",
                                fontSize = 10.sp,
                                color = Red,
                                letterSpacing = 2.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Review and respond to face recognition requests",
                                fontSize = 7.sp,
                                color = Muted,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    approvals.forEach { approval ->
                        ApprovalCard(
                            approval = approval,
                            cardColor = Red.copy(alpha = pulseAlpha),
                            onApprove = {
                                soundManager.playApprove()
                                repository.approveFace(approval.id, approval.name)
                            },
                            onDeny = {
                                soundManager.playDeny()
                                repository.denyFace(approval.id)
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: com.secura9.app.data.model.Approval,
    cardColor: androidx.compose.ui.graphics.Color,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cardBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Panel)
            .border(1.dp, Red.copy(alpha = borderAlpha), RoundedCornerShape(4.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Dim)
                    .border(0.5.dp, Cyan.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\uD83D\uDC64", fontSize = 28.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    approval.name.ifEmpty { "UNKNOWN" },
                    fontSize = 13.sp,
                    color = Text,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                if (approval.confidence > 0) {
                    Text(
                        "Confidence: ${(approval.confidence * 100).toInt()}%",
                        fontSize = 8.sp,
                        color = Muted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                approval.timestamp?.let {
                    Text(
                        java.text.SimpleDateFormat(
                            "HH:mm:ss", java.util.Locale.getDefault()
                        ).format(it.toDate()),
                        fontSize = 7.sp,
                        color = Muted.copy(alpha = 0.6f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NeonButton(
                text = "APPROVE",
                onClick = onApprove,
                color = Green,
                modifier = Modifier.weight(1f),
            )
            NeonButton(
                text = "DENY",
                onClick = onDeny,
                color = Red,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
