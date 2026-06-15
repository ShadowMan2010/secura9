package com.secura9.dashboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secura9.dashboard.ui.components.*
import com.secura9.dashboard.ui.theme.*
import com.secura9.dashboard.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = BgDark,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SettingsSection("Security") }
            item {
                SettingsCardAnimated(
                    animatedIcon = { AnimatedShield(active = status.passageMode, color = Yellow, modifier = Modifier.size(18.dp)) },
                    title = "Passage Mode",
                    subtitle = if (status.passageMode) "Door stays unlocked" else "Auto-lock active",
                    action = {
                        Switch(
                            checked = status.passageMode,
                            onCheckedChange = { on ->
                                if (on) viewModel.passageOn() else viewModel.passageOff()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Cyan.copy(alpha = 0.3f)),
                        )
                    }
                )
            }
            item {
                SettingsCardAnimated(
                    animatedIcon = { AnimatedFingerprint(active = true, color = Purple, modifier = Modifier.size(18.dp)) },
                    title = "Dual Auth",
                    subtitle = "Face + OTP required for access",
                    action = { Switch(checked = false, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedTrackColor = Purple.copy(alpha = 0.3f))) }
                )
            }

            item { SettingsSection("Updates") }
            item {
                SettingsCardAnimated(
                    animatedIcon = { AnimatedRadar(active = true, color = Green, modifier = Modifier.size(18.dp)) },
                    title = "OTA Update",
                    subtitle = "Check for system updates",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AnimatedButton(
                                text = "CHECK", onClick = { viewModel.checkOTA() },
                                color = SurfaceElevated, textColor = TextPrimary,
                                height = 36.dp, contentPadding = PaddingValues(horizontal = 16.dp),
                            )
                            AnimatedButton(
                                text = "APPLY", onClick = { viewModel.applyOTA() },
                                color = Green.copy(alpha = 0.15f), textColor = Green,
                                height = 36.dp, contentPadding = PaddingValues(horizontal = 16.dp),
                            )
                        }
                    }
                )
            }

            item { SettingsSection("Timer Codes") }
            item {
                SettingsCardAnimated(
                    animatedIcon = { AnimatedLock(locked = false, color = Yellow, modifier = Modifier.size(18.dp)) },
                    title = "Generate Timed Code",
                    subtitle = "Custom-duration one-time access code",
                    action = {
                        AnimatedButton(
                            text = "5 min", onClick = { viewModel.generateTimedCode(300) },
                            color = SurfaceElevated, textColor = TextPrimary,
                            height = 36.dp, contentPadding = PaddingValues(horizontal = 16.dp),
                        )
                    }
                )
            }

            item { SettingsSection("Schedule") }
            item {
                SettingsCardAnimated(
                    animatedIcon = { AnimatedBell(active = true, color = Cyan, modifier = Modifier.size(18.dp)) },
                    title = "Access Schedule",
                    subtitle = "Time-based access rules",
                    action = {
                        Icon(Icons.Default.ChevronRight, "Configure", tint = TextSecondary)
                    },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cyan.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, title, tint = Cyan, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
private fun SettingsCardAnimated(
    animatedIcon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cyan.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                animatedIcon()
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}
