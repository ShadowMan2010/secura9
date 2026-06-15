package com.secura9.dashboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.dashboard.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AnimatedButton(
    text: String = "",
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    color: Color = Cyan,
    textColor: Color = BgDark,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    height: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    val glowAlpha by infiniteTransition().animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ), label = "glow"
    )

    Button(
        onClick = {
            pressed = true
            onClick()
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .scale(scale)
            .height(height)
            .shadow(if (enabled) 8.dp * glowAlpha else 0.dp, shape, spotColor = color.copy(alpha = 0.4f * glowAlpha)),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = TextMuted,
        ),
        contentPadding = contentPadding,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = textColor,
                    strokeWidth = 2.dp,
                )
            }
            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = text,
                            modifier = Modifier.size(20.dp),
                        )
                        if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    }
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }
    }
    if (pressed) {
        LaunchedEffect(Unit) { delay(150); pressed = false }
    }
}
