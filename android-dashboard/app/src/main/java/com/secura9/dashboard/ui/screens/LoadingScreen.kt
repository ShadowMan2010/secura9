package com.secura9.dashboard.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.dashboard.ui.components.*
import com.secura9.dashboard.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(
    onLoaded: () -> Unit,
) {
    val transition = rememberInfiniteTransition()
    val dots by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "dots")

    LaunchedEffect(Unit) {
        delay(2000)
        onLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedShield(
                modifier = Modifier.size(96.dp),
                color = Cyan,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "SECURA-9",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "SMART LOCK SYSTEM",
                fontSize = 11.sp,
                color = TextSecondary,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (i == 0) 1f - dots else if (i == 1) 1f else dots)
                            .background(
                                Brush.verticalGradient(listOf(Cyan, Purple)),
                                CircleShape,
                            )
                    )
                }
            }
        }
    }
}
