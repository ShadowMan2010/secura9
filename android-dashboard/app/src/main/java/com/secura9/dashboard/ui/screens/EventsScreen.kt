package com.secura9.dashboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(events, key = { i, _ -> "${events[i].id}_$i" }) { index, event ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(
                        animationSpec = tween(300 + index * 50)
                    ),
                ) {
                    EventCard(event.type, event.title, event.body, event.timestamp)
                }
            }
            if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Inbox,
                                "Empty",
                                modifier = Modifier.size(48.dp),
                                tint = TextMuted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No events yet", fontSize = 14.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(type: String, title: String, body: String, timestamp: Long) {
    val (iconColor, color) = when (type) {
        "approval_request" -> Purple to Purple
        "approval_granted" -> Green to Green
        "approval_denied" -> Red to Red
        "motion" -> Orange to Orange
        "tamper" -> Red to Red
        "door" -> Cyan to Cyan
        "otp" -> Yellow to Yellow
        "system" -> TextSecondary to TextSecondary
        else -> TextSecondary to TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                when (type) {
                    "tamper" -> AnimatedBell(active = true, color = iconColor, modifier = Modifier.size(18.dp))
                    "motion" -> AnimatedRadar(active = true, color = iconColor, modifier = Modifier.size(18.dp))
                    "door" -> AnimatedLock(locked = true, color = iconColor, modifier = Modifier.size(18.dp))
                    "otp" -> AnimatedFingerprint(active = true, color = iconColor, modifier = Modifier.size(18.dp))
                    "approval_granted" -> AnimatedShield(active = true, color = iconColor, modifier = Modifier.size(18.dp))
                    else -> Icon(
                        when (type) {
                            "approval_request" -> Icons.Default.PersonAdd
                            "approval_denied" -> Icons.Default.Cancel
                            "system" -> Icons.Default.PowerSettingsNew
                            else -> Icons.Default.Notifications
                        },
                        type, tint = iconColor, modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = body, fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
                        .format(Date(timestamp)),
                    fontSize = 10.sp,
                    color = TextMuted,
                )
            }
        }
    }
}
