package com.secura9.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.secura9.app.data.model.AppUpdate

private val Cyan = Color(0xFF00FFE5)
private val Green = Color(0xFF00FF88)
private val Red = Color(0xFFFF003C)
private val Muted = Color(0xFF3A6080)
private val Text = Color(0xFFB8DDF0)
private val Dim = Color(0xFF0A1828)
private val Panel = Color(0xFF070F1C)
private val Bg = Color(0xFF030810)

@Composable
fun UpdateDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Bg, RoundedCornerShape(4.dp))
                .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "\u2B06 Update Available",
                color = Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "v${update.versionName}",
                color = Green,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (update.changelog.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = update.changelog,
                    color = Text,
                    fontSize = 12.sp,
                )
            }
            if (update.forceUpdate) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u26A0\uFE0F This update is required to continue using the app.",
                    color = Red,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!update.forceUpdate) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Muted, RoundedCornerShape(2.dp))
                            .background(Dim)
                            .clickable { onDismiss() }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Later",
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Cyan, RoundedCornerShape(2.dp))
                            .background(Cyan.copy(alpha = 0.08f))
                            .clickable { onDownload() }
                            .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\u2B07 Download",
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
