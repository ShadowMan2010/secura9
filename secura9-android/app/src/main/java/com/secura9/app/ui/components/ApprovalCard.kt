package com.secura9.app.ui.components

import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secura9.app.data.model.Approval
import com.secura9.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ApprovalCard(
    approval: Approval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sweep")
    val sweepAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep"
    )

    var decodedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(approval.imageB64) {
        if (approval.imageB64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(approval.imageB64, Base64.DEFAULT)
                decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Panel)
            .border(1.dp, Red.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
    ) {
        // Red top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Red.copy(alpha = sweepAlpha))
        )

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Red.copy(alpha = 0.07f))
                .border(0.dp, Red.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Red, size = 6)
                Spacer(Modifier.width(8.dp))
                Text(
                    "⚠ NEW FACE",
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                    color = Red,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
            Text(
                approval.timestamp?.let {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(it.toDate())
                } ?: "",
                fontSize = 9.sp,
                color = Muted,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }

        // Body
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Photo
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 120.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Dim)
                    .border(1.dp, Red.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                if (decodedBitmap != null) {
                    Image(
                        bitmap = decodedBitmap!!.asImageBitmap(),
                        contentDescription = "Visitor",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👤", fontSize = 28.sp)
                        Text(
                            "NO IMAGE",
                            fontSize = 8.sp,
                            color = Muted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }

                // Scan line animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Red.copy(alpha = 0.3f))
                        .offset(y = (20 * sweepAlpha).dp)
                )
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SPOKEN NAME",
                    fontSize = 9.sp,
                    color = Muted,
                    letterSpacing = 2.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    approval.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Yellow,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 2.sp,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Confidence: ${"%.0f".format(approval.confidence)}%",
                    fontSize = 9.sp,
                    color = Muted,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )

                Spacer(Modifier.height(12.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NeonButton(
                        text = "✓ APPROVE",
                        onClick = onApprove,
                        color = Green,
                        modifier = Modifier.weight(1f),
                    )
                    NeonButton(
                        text = "✗ DENY",
                        onClick = onDeny,
                        color = Red,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
