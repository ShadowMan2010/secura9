package com.secura9.dashboard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secura9.dashboard.ui.theme.*

@Composable
fun PulsingIndicator(
    active: Boolean,
    activeColor: Color = Green,
    inactiveColor: Color = TextMuted,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    val pulse by infiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 800 else 2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ), label = "pulse"
    )

    val glowAlpha = if (active) pulse else 0f

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) activeColor.copy(alpha = pulse) else inactiveColor.copy(alpha = 0.3f))
            .shadow(
                if (active) 6.dp * glowAlpha else 0.dp,
                CircleShape,
                spotColor = activeColor.copy(alpha = glowAlpha * 0.6f),
            ),
    )
}
