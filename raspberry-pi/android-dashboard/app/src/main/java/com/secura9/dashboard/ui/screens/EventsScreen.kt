package com.secura9.dashboard.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
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
    val (icon, color) = when (type) {
        "approval_request" -> Icons.Default.PersonAdd to Purple
        "approval_granted" -> Icons.Default.CheckCircle to Green
        "approval_denied" -> Icons.Default.Cancel to Red
        "motion" -> Icons.Default.DirectionsRun to Orange
        "tamper" -> Icons.Default.Warning to Red
        "door" -> Icons.Default.Lock to Cyan
        "otp" -> Icons.Default.Pin to Yellow
        "system" -> Icons.Default.PowerSettingsNew to TextSecondary
        else -> Icons.Default.Notifications to TextSecondary
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
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, type, tint = color, modifier = Modifier.size(18.dp))
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
