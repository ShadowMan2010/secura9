package com.secura9.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Glowing Card ──────────────────────────────────────────────────────────

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Cyan,
    content: @Composable () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Panel)
            .border(1.dp, glowColor.copy(alpha = glowAlpha), RoundedCornerShape(4.dp))
    ) {
        content()
    }
}

// ── HoloCard (holographic shimmer) ───────────────────────────────────────

@Composable
fun HoloCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holo")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "holoShift"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Panel)
            .border(
                1.dp,
                Brush.horizontalGradient(
                    colors = listOf(Cyan, Green, Yellow, Cyan),
                    startX = 0f,
                    endX = Float.MAX_VALUE,
                ).let { brush ->
                    brush
                },
                RoundedCornerShape(4.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Cyan.copy(alpha = 0.03f),
                        Color.Transparent,
                        Green.copy(alpha = 0.03f),
                        Color.Transparent,
                    ),
                    startX = size.width * (shift - 0.5f) * 2,
                    endX = size.width * (shift + 0.5f) * 2,
                ),
                size = size,
            )
        }
        content()
    }
}

// ── Status Indicator Dot ──────────────────────────────────────────────────

@Composable
fun StatusDot(
    color: Color,
    size: Int = 8,
    animate: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (animate) 1000 else 1),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (animate) alpha else 1f))
    )
}

// ── Neon Button ───────────────────────────────────────────────────────────

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    enabled: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neonBtn")
    val shadowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shadowAlpha"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(48.dp),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color,
            disabledContainerColor = Muted.copy(alpha = 0.1f),
            disabledContentColor = Muted,
        ),
    ) {
        Text(
            text = text,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Glow Button (extra neon) ──────────────────────────────────────────────

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    enabled: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glowBtn")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f * pulse),
            contentColor = color,
            disabledContainerColor = Muted.copy(alpha = 0.1f),
            disabledContentColor = Muted,
        ),
    ) {
        Text(
            text = text,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Stat Box ──────────────────────────────────────────────────────────────

@Composable
fun StatBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
) {
    HoloCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = color,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = Muted,
                letterSpacing = 2.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Section Header ────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\u2B21 $title",
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            color = Muted,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Red.copy(alpha = 0.15f))
                    .border(1.dp, Red.copy(alpha = 0.4f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    color = Red,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Scan Lines Overlay ────────────────────────────────────────────────────

@Composable
fun ScanLinesOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Cyan.copy(alpha = 0.015f),
                        Color.Transparent,
                    ),
                )
            )
    )
}

// ── Loading Pulse ─────────────────────────────────────────────────────────

@Composable
fun PulseLoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size((24 * scale).dp)
            .clip(RoundedCornerShape(50))
            .background(Cyan.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Cyan)
        )
    }
}

// ── GlitchText ────────────────────────────────────────────────────────────

@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    fontSize: Int = 28,
    letterSpacing: Int = 8,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glitchText")
    val glitchFrame by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glitchTimer"
    )

    val isGlitching = glitchFrame.toInt() % 25 == 0
    val offset = if (isGlitching) (2..4).random() else 0

    Box(modifier = modifier) {
        if (isGlitching) {
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = Red.copy(alpha = 0.5f),
                letterSpacing = letterSpacing.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.offset(x = -offset.dp, y = 0.dp),
            )
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = Cyan.copy(alpha = 0.5f),
                letterSpacing = letterSpacing.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.offset(x = offset.dp, y = 0.dp),
            )
        }
        Text(
            text = text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = letterSpacing.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

// ── TypewriterText ────────────────────────────────────────────────────────

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Muted,
    fontSize: Int = 10,
    delayMs: Int = 50,
) {
    var visibleChars by remember { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        visibleChars = 0
        for (i in text.indices) {
            kotlinx.coroutines.delay(delayMs.toLong())
            visibleChars = i + 1
        }
    }

    Text(
        text = text.take(visibleChars),
        fontSize = fontSize.sp,
        color = color,
        letterSpacing = 2.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = modifier,
    )
}

// ── NeonDivider ───────────────────────────────────────────────────────────

@Composable
fun NeonDivider(
    modifier: Modifier = Modifier,
    color: Color = Cyan,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neonDiv")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neonDivGlow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, color.copy(alpha = glow), Color.Transparent)
                )
            )
    )
}

// ── PulseRing ─────────────────────────────────────────────────────────────

@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    size: Int = 60,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size.dp)) {
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = size.toFloat() / 2 * scale / 2.5f,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Canvas(modifier = Modifier.size(size.dp)) {
            drawCircle(
                color = color.copy(alpha = alpha * 0.5f),
                radius = size.toFloat() / 2 * scale / 3.5f,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

// ── RadarAnimation ────────────────────────────────────────────────────────

@Composable
fun RadarAnimation(
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    size: Int = 120,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radarRot"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val cx = size.toFloat() / 2
        val cy = size.toFloat() / 2
        val r = size.toFloat() / 2 - 4.dp.toPx()

        // Outer ring
        drawCircle(color = color.copy(alpha = 0.2f), radius = r, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = color.copy(alpha = 0.1f), radius = r * 0.66f, style = Stroke(width = 0.5.dp.toPx()))
        drawCircle(color = color.copy(alpha = 0.05f), radius = r * 0.33f, style = Stroke(width = 0.5.dp.toPx()))

        // Sweep
        val angleRad = Math.toRadians(rotation.toDouble())
        val endX = cx + r * cos(angleRad).toFloat()
        val endY = cy + r * sin(angleRad).toFloat()

                    val sweepPath = Path().apply {
                        moveTo(cx, cy)
                        lineTo(endX, endY)
                        arcTo(
                            androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                            rotation,
                            -30f,
                            true,
                        )
                        close()
                    }
        drawPath(sweepPath, color.copy(alpha = 0.08f))

        // Line
        drawLine(color.copy(alpha = 0.6f), Offset(cx, cy), Offset(endX, endY), strokeWidth = 1.5.dp.toPx())
    }
}

// ── FloatingParticles ─────────────────────────────────────────────────────

@Composable
fun FloatingParticles(
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    count: Int = 15,
) {
    val particles = remember {
        List(count) {
            ParticleState(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 0.5f,
                alpha = Random.nextFloat() * 0.3f + 0.1f,
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particleTick"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEachIndexed { i, p ->
            val drift = (tick + i * 0.1f) % 1f
            val px = (p.x + drift * 0.05f) * size.width
            val py = ((p.y - drift * 0.02f + 1f) % 1f) * size.height

            drawCircle(
                color = color.copy(alpha = p.alpha),
                radius = p.size.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}

private class ParticleState(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
)

// ── MatrixRain ────────────────────────────────────────────────────────────

@Composable
fun MatrixRain(
    modifier: Modifier = Modifier,
    color: Color = Green,
) {
    AndroidView(
        factory = { ctx ->
            MatrixRainView(ctx).apply {
                setColor(color.toArgb())
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

// ── CyberGrid (background grid pattern) ──────────────────────────────────

@Composable
fun CyberGrid(
    modifier: Modifier = Modifier,
    color: Color = Cyan,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val gridSize = 48.dp.toPx()
        val cols = (size.width / gridSize).toInt() + 1
        val rows = (size.height / gridSize).toInt() + 1

        for (c in 0..cols) {
            val x = c * gridSize
            drawLine(
                color = color.copy(alpha = 0.03f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.5.dp.toPx(),
            )
        }
        for (r in 0..rows) {
            val y = r * gridSize
            drawLine(
                color = color.copy(alpha = 0.03f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // Bright points at intersections
        val pulseAlpha = (sin(System.currentTimeMillis() / 1000.0).toFloat() * 0.03f + 0.05f)
        for (c in 0..cols step 2) {
            for (r in 0..rows step 2) {
                drawCircle(
                    color = color.copy(alpha = pulseAlpha),
                    radius = 1.5.dp.toPx(),
                    center = Offset(c * gridSize, r * gridSize),
                )
            }
        }
    }
}

// ── LoadingHex (hexagonal spinner) ────────────────────────────────────────

@Composable
fun LoadingHex(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "hexSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hexAngle"
    )

    Canvas(modifier = modifier.size(32.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.width / 2 - 4.dp.toPx()
        val angleRad = Math.toRadians(angle.toDouble())

        val path = Path().apply {
            for (i in 0..5) {
                val a = Math.toRadians((i * 60 + angle).toDouble())
                val px = cx + r * cos(a).toFloat()
                val py = cy + r * sin(a).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(path, Cyan.copy(alpha = 0.3f), style = Stroke(width = 2.dp.toPx()))
        drawCircle(Cyan, 3.dp.toPx(), Offset(cx, cy))
    }
}
