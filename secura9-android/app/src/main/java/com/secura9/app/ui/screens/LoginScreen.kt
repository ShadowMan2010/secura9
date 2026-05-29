package com.secura9.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.secura9.app.R
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.ui.components.*
import com.secura9.app.ui.theme.*
import com.secura9.app.utils.SoundManager

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    repository: FirebaseRepository,
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val activity = context as android.app.Activity
    val infiniteTransition = rememberInfiniteTransition(label = "login")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow"
    )
    val taglineAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tagline"
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                repository.signInWithGoogle(token) { success, _ ->
                    if (success) {
                        soundManager.playSuccess()
                        repository.pushPendingFcmToken(context)
                        onLoggedIn()
                    } else {
                        soundManager.playError()
                    }
                }
            }
        } catch (_: ApiException) {
            soundManager.playError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        MatrixRain(color = Green.copy(alpha = 0.15f))
        CyberGrid(color = Cyan)
        FloatingParticles(color = Cyan, count = 20)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Cyan.copy(alpha = 0.05f))
                    .border(1.dp, Cyan.copy(alpha = glowAlpha * 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PulseRing(color = Cyan, size = 100)
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "SECURA-9 Logo",
                    modifier = Modifier.size(64.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            GlitchText(
                text = "SECURA-9",
                fontSize = 32,
                letterSpacing = 10,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "SMART DOOR ACCESS",
                fontSize = 11.sp,
                color = Muted.copy(alpha = taglineAlpha),
                letterSpacing = 6.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )

            Spacer(Modifier.height(8.dp))

            NeonDivider(color = Cyan)

            Spacer(Modifier.height(48.dp))

            GlowButton(
                text = "SIGN IN WITH GOOGLE",
                onClick = {
                    soundManager.playClick()
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("355487120144-onq52bprs60f8s90n1u8bo73ubdo5ueu.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(activity, gso)
                    launcher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                color = Cyan,
            )

            Spacer(Modifier.height(24.dp))

            TypewriterText(
                text = "SECURE  .  REAL-TIME  .  SMART",
                color = Muted,
                fontSize = 8,
                delayMs = 80,
            )
        }
    }
}
