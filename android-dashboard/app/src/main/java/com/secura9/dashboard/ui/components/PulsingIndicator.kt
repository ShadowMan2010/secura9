package com.secura9.dashboard.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 800 else 2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ), label = "pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (active) activeColor.copy(alpha = pulse)
                else inactiveColor.copy(alpha = 0.3f)
            ),
    )
}
