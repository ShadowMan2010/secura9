package com.secura9.dashboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.secura9.dashboard.ui.theme.*

@Composable
fun StatusCard(
    icon: ImageVector,
    label: String,
    value: String,
    isActive: Boolean,
    activeColor: Color = Green,
    inactiveColor: Color = TextMuted,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition()
    val cardColor by animateColorAsState(targetValue = if (isActive) activeColor.copy(alpha = 0.1f) else BgPanel, label = "cardBg")
    val indicatorColor by animateColorAsState(targetValue = if (isActive) activeColor else inactiveColor, label = "indicator")
    val pulse by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isActive) 1000 else 3000), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, tint = indicatorColor.copy(alpha = pulse), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = TextSecondary, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isActive) TextPrimary else TextSecondary)
            }
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(indicatorColor.copy(alpha = if (isActive) pulse else 0.3f))
            )
        }
    }
}

@Composable
fun StatusCardAnimated(
    animatedIcon: @Composable () -> Unit,
    label: String,
    value: String,
    isActive: Boolean,
    activeColor: Color = Green,
    inactiveColor: Color = TextMuted,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition()
    val cardColor by animateColorAsState(targetValue = if (isActive) activeColor.copy(alpha = 0.1f) else BgPanel, label = "cardBg")
    val indicatorColor by animateColorAsState(targetValue = if (isActive) activeColor else inactiveColor, label = "indicator")
    val pulse by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isActive) 1000 else 3000), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                animatedIcon()
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = TextSecondary, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (isActive) TextPrimary else TextSecondary)
            }
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(indicatorColor.copy(alpha = if (isActive) pulse else 0.3f))
            )
        }
    }
}
