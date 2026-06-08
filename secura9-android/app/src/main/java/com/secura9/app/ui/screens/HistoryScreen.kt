package com.secura9.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.data.model.AccessLogEntry
import com.secura9.app.ui.components.*
import com.secura9.app.ui.theme.*

@Composable
fun HistoryScreen(repository: FirebaseRepository) {
    val entries by repository.listenAccessLog().collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        CyberGrid(color = Cyan)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(bottom = 80.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                title = "ACCESS LOG",
                badge = "${entries.size} EVENTS",
            )

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "No access records yet",
                            fontSize = 10.sp,
                            color = Muted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Dim)
                                .border(0.5.dp, Muted.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(
                                color = when (entry.type) {
                                    "granted" -> Green
                                    "denied" -> Red
                                    "otp" -> Yellow
                                    "motion" -> Cyan
                                    else -> Muted
                                },
                                size = 6,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.name.ifEmpty { entry.message },
                                    fontSize = 11.sp,
                                    color = Text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                                Text(
                                    entry.timestamp?.let {
                                        java.text.SimpleDateFormat(
                                            "dd MMM HH:mm:ss",
                                            java.util.Locale.getDefault()
                                        ).format(it.toDate())
                                    } ?: "",
                                    fontSize = 8.sp,
                                    color = Muted,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        when (entry.type) {
                                            "granted" -> Green.copy(alpha = 0.1f)
                                            "denied" -> Red.copy(alpha = 0.1f)
                                            "otp" -> Yellow.copy(alpha = 0.1f)
                                            else -> Cyan.copy(alpha = 0.1f)
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        when (entry.type) {
                                            "granted" -> Green.copy(alpha = 0.3f)
                                            "denied" -> Red.copy(alpha = 0.3f)
                                            "otp" -> Yellow.copy(alpha = 0.3f)
                                            else -> Cyan.copy(alpha = 0.3f)
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    entry.type.uppercase(),
                                    fontSize = 7.sp,
                                    color = when (entry.type) {
                                        "granted" -> Green
                                        "denied" -> Red
                                        "otp" -> Yellow
                                        "motion" -> Cyan
                                        else -> Muted
                                    },
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
