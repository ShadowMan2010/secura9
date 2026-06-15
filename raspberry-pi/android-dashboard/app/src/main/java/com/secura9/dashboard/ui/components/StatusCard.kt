package com.secura9.dashboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    val bgAlpha by infiniteTransition().animateFloat(
        initialValue = 0.08f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ), label = "bgPulse"
    )
    val cardColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.1f) else BgPanel,
        animationSpec = tween(400), label = "cardColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = tween(300), label = "indicator"
    )

    val pulse by infiniteTransition().animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1000 else 3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ), label = "pulse"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = indicatorColor.copy(alpha = pulse),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) TextPrimary else TextSecondary,
                )
            }
            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = if (isActive) pulse else 0.3f))
                    .shadow(if (isActive) 4.dp * pulse else 0.dp, CircleShape, spotColor = activeColor.copy(alpha = 0.5f)),
            )
        }
    }
}
