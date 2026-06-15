package com.secura9.dashboard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secura9.dashboard.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedShield(
    modifier: Modifier = Modifier.size(28.dp),
    color: Color = Cyan,
    active: Boolean = true,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(1200, easing = EaseInOutCubic), RepeatMode.Reverse), label = "p")
    val glow by transition.animateFloat(0.3f, 0.8f,
        infiniteRepeatable(tween(1600, easing = EaseInOutCubic), RepeatMode.Reverse), label = "g")
    val c = if (active) color else TextMuted
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val cx = size.width / 2f; val cy = size.height / 2f
        val hw = size.minDimension * 0.45f; val hh = size.minDimension * 0.5f
        val path = Path().apply {
            moveTo(cx, cy - hh); lineTo(cx - hw, cy - hh * 0.4f)
            lineTo(cx - hw * 0.85f, cy + hh * 0.2f)
            lineTo(cx, cy + hh); lineTo(cx + hw * 0.85f, cy + hh * 0.2f)
            lineTo(cx + hw, cy - hh * 0.4f); close()
        }
        drawPath(path, c.copy(alpha = glow * 0.3f), style = Stroke(sw * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, c.copy(alpha = pulse), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val check = Path().apply {
            moveTo(cx - hw * 0.25f, cy)
            lineTo(cx - hw * 0.08f, cy + hh * 0.2f)
            lineTo(cx + hw * 0.3f, cy - hh * 0.15f)
        }
        drawPath(check, c.copy(alpha = pulse), style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun AnimatedLock(
    modifier: Modifier = Modifier.size(28.dp),
    locked: Boolean = true,
    color: Color = Cyan,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(0.7f, 1f,
        infiniteRepeatable(tween(1000, easing = EaseInOutCubic), RepeatMode.Reverse), label = "p")
    val c = if (locked) color else Green
    val unlockedOffset by animateFloatAsState(targetValue = if (locked) 0f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "u")
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val cx = size.width / 2f
        val w = size.width * 0.55f; val h = size.height * 0.45f
        val shackleR = w * 0.3f
        val shackleTopY = size.height * 0.15f + unlockedOffset * h * 1.2f
        val bodyPath = Path().apply {
            addRoundRect(RoundRect(cx - w / 2f, size.height * 0.5f, cx + w / 2f, size.height * 0.85f, w * 0.15f, w * 0.15f))
        }
        drawPath(bodyPath, c.copy(alpha = pulse), style = Stroke(sw, cap = StrokeCap.Round))
        drawPath(bodyPath, c.copy(alpha = 0.15f), style = Stroke(sw * 3f))
        val keyholePos = Offset(cx, size.height * 0.7f)
        drawCircle(c.copy(alpha = pulse), sw * 0.6f, keyholePos)
        drawLine(c.copy(alpha = pulse), keyholePos, Offset(cx, size.height * 0.78f), sw * 0.6f, StrokeCap.Round)
        val shacklePath = Path().apply {
            moveTo(cx - w * 0.25f, size.height * 0.5f)
            lineTo(cx - w * 0.25f, shackleTopY)
            arcTo(Rect(cx - w * 0.25f - shackleR, shackleTopY - shackleR, cx - w * 0.25f + shackleR, shackleTopY + shackleR), 180f, 180f, true)
            lineTo(cx + w * 0.25f, size.height * 0.5f)
        }
        drawPath(shacklePath, c.copy(alpha = pulse), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun AnimatedFingerprint(
    modifier: Modifier = Modifier.size(28.dp),
    color: Color = Cyan,
    active: Boolean = true,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition()
    val scanLine by transition.animateFloat(-1f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "s")
    val pulse by transition.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p")
    val c = if (active) color else TextMuted
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension * 0.4f
        for (i in 0 until 4) {
            drawArc(c.copy(alpha = pulse * (1f - i * 0.15f)),
                -140f + i * 70f, 50f, false,
                Offset(cx - r + i * sw * 0.8f, cy - r + i * sw * 0.8f),
                Size((r - i * sw * 0.8f) * 2f, (r - i * sw * 0.8f) * 2f),
                sw * 0.7f)
        }
        val scanY = cy + scanLine * r * 0.7f
        drawLine(c.copy(alpha = 0.6f), Offset(cx - r * 0.3f, scanY), Offset(cx + r * 0.3f, scanY), sw * 0.5f, StrokeCap.Round)
    }
}

@Composable
fun AnimatedCamera(
    modifier: Modifier = Modifier.size(28.dp),
    recording: Boolean = false,
    color: Color = Cyan,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    val dotAlpha by transition.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d")
    val c = if (recording) Red else color
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val cx = size.width / 2f; val cy = size.height / 2f
        val bw = size.width * 0.65f; val bh = size.height * 0.55f
        val lens = Path().apply {
            addRoundRect(RoundRect(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f, bw * 0.12f, bw * 0.12f))
        }
        drawPath(lens, c.copy(alpha = pulse), style = Stroke(width = sw, cap = StrokeCap.Round))
        drawCircle(c.copy(alpha = 0.3f), sw * 1.2f, Offset(cx + bw * 0.3f, cy - bh * 0.25f))
        drawCircle(c.copy(alpha = pulse * 0.6f), bw * 0.18f, Offset(cx, cy))
        if (recording) drawCircle(Red.copy(alpha = dotAlpha), sw * 1.5f, Offset(cx + bw * 0.05f, cy - bh * 0.35f))
    }
}

@Composable
fun AnimatedRadar(
    modifier: Modifier = Modifier.size(28.dp),
    active: Boolean = true,
    color: Color = Cyan,
    strokeWidth: Dp = 1.5.dp,
) {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val pulse by transition.animateFloat(0.3f, 0.8f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "p")
    val c = if (active) color else TextMuted
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension * 0.42f
        val rad = angle * PI.toFloat() / 180f
        for (i in 1..3) drawCircle(c.copy(alpha = pulse / i), r * i / 3f, Offset(cx, cy), style = Stroke(sw * 0.5f))
        val sweepPath = Path().apply {
            moveTo(cx, cy)
            arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 0f, angle.coerceAtMost(60f), true)
            close()
        }
        drawPath(sweepPath, c.copy(alpha = 0.1f))
        drawLine(c.copy(alpha = pulse), Offset(cx, cy), Offset(cx + cos(rad) * r, cy + sin(rad) * r), sw, StrokeCap.Round)
        listOf(45f, 120f, 200f, 300f).forEach { a ->
            val aRad = a * PI.toFloat() / 180f
            val dist = r * (0.5f + (a % 3f) * 0.15f)
            drawCircle(c.copy(alpha = 0.5f), sw * 2f, Offset(cx + cos(aRad) * dist, cy + sin(aRad) * dist))
        }
    }
}

@Composable
fun AnimatedBell(
    modifier: Modifier = Modifier.size(28.dp),
    active: Boolean = true,
    color: Color = Yellow,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition()
    val shake by transition.animateFloat(-8f, 8f,
        infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "s")
    val pulse by transition.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    val c = if (active) color else TextMuted
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val cx = size.width / 2f + shake.dp.toPx()
        val cy = size.height * 0.35f
        val bw = size.width * 0.35f; val bh = size.height * 0.45f
        val bellPath = Path().apply {
            moveTo(cx - bw * 0.9f, cy + bh * 0.2f)
            cubicTo(cx - bw, cy - bh * 0.1f, cx + bw, cy - bh * 0.1f, cx + bw * 0.9f, cy + bh * 0.2f)
            lineTo(cx + bw * 0.75f, cy + bh * 0.7f)
            lineTo(cx - bw * 0.75f, cy + bh * 0.7f)
            close()
        }
        drawPath(bellPath, c.copy(alpha = pulse), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.copy(alpha = pulse), sw * 0.6f, Offset(cx, cy - bh * 0.1f))
        drawCircle(c.copy(alpha = pulse), sw * 0.8f, Offset(cx, cy + bh * 0.85f))
    }
}
