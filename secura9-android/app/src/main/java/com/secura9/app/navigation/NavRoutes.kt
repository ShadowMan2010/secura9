package com.secura9.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    data object LiveView : Screen("live_view", "Live View", Icons.Filled.Videocam)
    data object Approvals : Screen("approvals", "Approvals", Icons.Filled.PersonSearch)
    data object History : Screen("history", "History", Icons.Filled.History)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Login : Screen("login", "Login", Icons.Filled.Login)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.LiveView,
    Screen.Approvals,
    Screen.History,
    Screen.Settings,
)
