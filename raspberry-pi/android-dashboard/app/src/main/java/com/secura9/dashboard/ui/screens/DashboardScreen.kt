package com.secura9.dashboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secura9.dashboard.ui.components.*
import com.secura9.dashboard.ui.theme.*
import com.secura9.dashboard.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToApproval: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCall: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pendingApproval by viewModel.pendingApproval.collectAsStateWithLifecycle()
    val otp by viewModel.otp.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val showApprovalBanner = pendingApproval != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SECURA-9",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 3.sp,
                            color = Cyan,
                        )
                        Spacer(Modifier.width(8.dp))
                        PulsingIndicator(active = status.online, size = 8.dp)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = BgDark,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Approval Banner ────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = showApprovalBanner,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                ) {
                    Card(
                        onClick = onNavigateToApproval,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = 0.15f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Purple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    "Approval",
                                    tint = Purple,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Approval Request",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    pendingApproval?.name ?: "Unknown",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                "View",
                                tint = TextSecondary,
                            )
                        }
                    }
                }
            }

            // ── Status Cards Grid ─────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusCard(
                            icon = Icons.Default.Lock,
                            label = "DOOR",
                            value = if (status.doorLocked) "LOCKED" else "UNLOCKED",
                            isActive = !status.doorLocked,
                            activeColor = if (status.doorLocked) DoorLocked else DoorUnlocked,
                            modifier = Modifier.weight(1f),
                        )
                        StatusCard(
                            icon = Icons.Default.Wifi,
                            label = "CONNECTION",
                            value = if (status.online) "ONLINE" else "OFFLINE",
                            isActive = status.online,
                            activeColor = StatusOnline,
                            inactiveColor = StatusOffline,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusCard(
                            icon = Icons.Default.Person,
                            label = "CAMERA",
                            value = if (status.camera) "ACTIVE" else "OFF",
                            isActive = status.camera,
                            modifier = Modifier.weight(1f),
                        )
                        StatusCard(
                            icon = Icons.Default.DoorFront,
                            label = "PASSAGE",
                            value = if (status.passageMode) "ON" else "OFF",
                            isActive = status.passageMode,
                            activeColor = Yellow,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Quick Actions ─────────────────────────────────────────
            item {
                Text(
                    "Quick Actions",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedButton(
                        text = "UNLOCK",
                        icon = Icons.Default.LockOpen,
                        onClick = { viewModel.unlockDoor() },
                        color = DoorUnlocked,
                        textColor = BgDark,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedButton(
                        text = "LOCK",
                        icon = Icons.Default.Lock,
                        onClick = { viewModel.lockDoor() },
                        color = DoorLocked,
                        textColor = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedButton(
                        text = "OTP",
                        icon = Icons.Default.Pin,
                        onClick = { viewModel.generateOTP() },
                        color = Purple,
                        textColor = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedButton(
                        text = if (status.nobodyHome) "HOME" else "AWAY",
                        icon = if (status.nobodyHome) Icons.Default.Home else Icons.Default.Hiking,
                        onClick = { viewModel.setNobodyHome(!status.nobodyHome) },
                        color = if (status.nobodyHome) Green else Orange,
                        textColor = BgDark,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedButton(
                        text = "EVENTS",
                        icon = Icons.Default.History,
                        onClick = onNavigateToEvents,
                        color = SurfaceElevated,
                        textColor = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedButton(
                        text = "CODE",
                        icon = Icons.Default.Timer,
                        onClick = { viewModel.generateTimedCode() },
                        color = SurfaceElevated,
                        textColor = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── OTP Display ───────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = otp != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    otp?.let { data ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Cyan.copy(alpha = 0.08f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("OTP CODE", fontSize = 11.sp, color = TextSecondary, letterSpacing = 2.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = data.otp.chunked(3).joinToString(" "),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 6.sp,
                                    color = Cyan,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Expires in ${data.expiresIn}s",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
