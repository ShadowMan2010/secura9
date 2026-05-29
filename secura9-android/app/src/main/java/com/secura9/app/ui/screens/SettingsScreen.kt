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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.data.model.DeviceStatus
import com.secura9.app.ui.components.*
import com.secura9.app.ui.theme.*
import com.secura9.app.utils.SoundManager

@Composable
fun SettingsScreen(
    repository: FirebaseRepository,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val status by repository.listenStatus().collectAsState(initial = DeviceStatus())
    var nobodyHome by remember { mutableStateOf(status.nobodyHome) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(status.nobodyHome) {
        nobodyHome = status.nobodyHome
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        CyberGrid(color = Cyan)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            GlitchText(
                text = "SETTINGS",
                fontSize = 14,
                letterSpacing = 6,
                color = Muted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            NeonDivider(color = Cyan, modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(16.dp))

            SectionHeader("ACCOUNT")

            HoloCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        repository.getUserName(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Text,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Text(
                        repository.getUserEmail(),
                        fontSize = 9.sp,
                        color = Muted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlowButton(
                        text = "SIGN OUT",
                        onClick = {
                            soundManager.playClick()
                            showLogoutConfirm = true
                        },
                        color = Red,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("CONTROLS")

            CyberCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "NOBODY HOME MODE",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        color = Text,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When active, visitors get OTP for door access",
                        fontSize = 8.sp,
                        color = Muted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (nobodyHome) "ACTIVE" else "INACTIVE",
                            fontSize = 10.sp,
                            color = if (nobodyHome) Red else Muted,
                            letterSpacing = 2.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                        Switch(
                            checked = nobodyHome,
                            onCheckedChange = {
                                nobodyHome = it
                                if (it) soundManager.playAlert() else soundManager.playClick()
                                repository.toggleNobodyHome(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Red.copy(alpha = 0.3f),
                                checkedThumbColor = Red,
                                uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                                uncheckedThumbColor = Muted,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("DEVICE INFO")

            CyberCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DeviceInfoRow("Device ID", "secura9_pi_01")
                    DeviceInfoRow("App Version", "1.0.0")
                    DeviceInfoRow("Connection", "Firebase")
                    DeviceInfoRow("User ID", repository.getUserId().take(16) + "...")
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "SECURA-9 v1.0.0",
                fontSize = 8.sp,
                color = Muted,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = "POWERED BY FIREBASE",
                fontSize = 8.sp,
                color = Muted.copy(alpha = 0.5f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
            )
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            confirmButton = {
                Button(
                    onClick = {
                        soundManager.playDoorClose()
                        showLogoutConfirm = false
                        repository.signOut()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Red.copy(alpha = 0.1f),
                        contentColor = Red,
                    ),
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Text("SIGN OUT", fontSize = 10.sp, letterSpacing = 2.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showLogoutConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Muted.copy(alpha = 0.1f),
                        contentColor = Muted,
                    ),
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Text("CANCEL", fontSize = 10.sp, letterSpacing = 2.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            containerColor = Panel,
            titleContentColor = Text,
            textContentColor = Muted,
            shape = RoundedCornerShape(4.dp),
            title = {
                Text(
                    "SIGN OUT",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = Red,
                )
            },
            text = {
                Text(
                    "Are you sure you want to sign out?",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Muted,
                )
            },
        )
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 9.sp, color = Muted,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text(value, fontSize = 9.sp, color = Cyan,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
