package com.secura9.dashboard.ui.screens

import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.secura9.dashboard.ui.components.AnimatedButton
import com.secura9.dashboard.ui.theme.*
import com.secura9.dashboard.viewmodel.DashboardViewModel

@Composable
fun ApprovalScreen(
    name: String,
    confidence: Double,
    imageBase64: String,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val imageBytes = remember(imageBase64) {
        if (imageBase64.isNotBlank())
            try { Base64.decode(imageBase64, Base64.DEFAULT) } catch (_: Exception) { null }
        else null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgPanel2, BgDark, BgDark),
                        startY = 0f,
                        endY = 600f,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                "APPROVAL REQUEST",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(24.dp))

            // Photo
            Card(
                modifier = Modifier
                    .size(220.dp)
                    .shadow(20.dp, RoundedCornerShape(24.dp), Cyan.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                if (imageBytes != null) {
                    AsyncImage(
                        model = imageBytes,
                        contentDescription = "Visitor",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BgPanel2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Person,
                            "No image",
                            modifier = Modifier.size(64.dp),
                            tint = TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Name
            Text(
                text = name.ifBlank { "Unknown" },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            // Confidence bar
            if (confidence > 0) {
                Spacer(Modifier.height(8.dp))
                val confPct = (confidence * 100).toInt()
                val confColor = when {
                    confPct >= 80 -> Green
                    confPct >= 50 -> Yellow
                    else -> Orange
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${confPct}% match",
                        fontSize = 13.sp,
                        color = confColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { confidence.toFloat() },
                        modifier = Modifier
                            .width(160.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = confColor,
                        trackColor = confColor.copy(alpha = 0.15f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedButton(
                    text = "DENY",
                    icon = Icons.Default.Close,
                    onClick = { viewModel.denyRequest(); onBack() },
                    color = RedDim,
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                )
                AnimatedButton(
                    text = "APPROVE",
                    icon = Icons.Default.Check,
                    onClick = {
                        viewModel.approveRequest(name.ifBlank { "Visitor" })
                        onBack()
                    },
                    color = Green,
                    textColor = BgDark,
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
