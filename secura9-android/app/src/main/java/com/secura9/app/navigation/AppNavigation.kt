package com.secura9.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secura9.app.data.firebase.FirebaseRepository
import com.secura9.app.data.model.AppUpdate
import com.secura9.app.ui.screens.*
import com.secura9.app.ui.theme.*

@Composable
fun AppNavigation(
    repository: FirebaseRepository,
    onUpdateAvailable: (AppUpdate) -> Unit = {},
) {
    val navController = rememberNavController()
    var showBoot by remember { mutableStateOf(true) }
    val startDest = if (repository.isLoggedIn()) Screen.Dashboard.route else Screen.Login.route

    val context = LocalContext.current

    LaunchedEffect(showBoot) {
        if (!showBoot && repository.isLoggedIn()) {
            repository.pushPendingFcmToken(context)
        }
    }

    if (showBoot) {
        BootScreen(
            onBootComplete = { showBoot = false }
        )
    } else {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (repository.isLoggedIn()) {
                    CyberBottomBar(navController = navController)
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDest,
                modifier = Modifier.padding(padding),
                enterTransition = {
                    fadeIn(animationSpec = tween(400)) +
                        slideInHorizontally(animationSpec = tween(400)) { it / 4 }
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(300)) +
                        slideOutHorizontally(animationSpec = tween(300)) { -it / 4 }
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300)) +
                        slideInHorizontally(animationSpec = tween(300)) { -it / 4 }
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(300)) +
                        slideOutHorizontally(animationSpec = tween(300)) { it / 4 }
                },
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoggedIn = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        },
                        repository = repository,
                    )
                }

                composable(Screen.Dashboard.route) {
                    DashboardScreen(repository = repository)
                }

                composable(Screen.LiveView.route) {
                    LiveViewScreen(repository = repository)
                }

                composable(Screen.Approvals.route) {
                    ApprovalsScreen(repository = repository)
                }

                composable(Screen.History.route) {
                    HistoryScreen(repository = repository)
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        repository = repository,
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun CyberBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val infiniteTransition = rememberInfiniteTransition(label = "barGlow")
    val barGlow by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "barGlowAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Box {
            // Top glow line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Cyan.copy(alpha = barGlow))
                    .align(Alignment.TopCenter)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bottomNavScreens.forEach { screen ->
                    val selected = currentRoute == screen.route

                    val iconColor = if (selected) Cyan else Muted
                    val bgColor = if (selected) Cyan.copy(alpha = 0.08f) else Color.Transparent

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .then(
                                if (selected) Modifier
                                    .background(Cyan.copy(alpha = 0.03f))
                                    .border(0.5.dp, Cyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                else Modifier
                            ),
                    ) {
                        IconButton(
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = iconColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = screen.title,
                            fontSize = 7.sp,
                            color = iconColor,
                            letterSpacing = 1.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
