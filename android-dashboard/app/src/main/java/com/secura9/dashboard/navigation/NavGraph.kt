package com.secura9.dashboard.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secura9.dashboard.ui.screens.DashboardScreen
import com.secura9.dashboard.ui.screens.ApprovalScreen
import com.secura9.dashboard.ui.screens.EventsScreen
import com.secura9.dashboard.ui.screens.SettingsScreen
import com.secura9.dashboard.ui.screens.CallScreen
import com.secura9.dashboard.ui.screens.LoadingScreen

object Routes {
    const val LOADING = "loading"
    const val DASHBOARD = "dashboard"
    const val APPROVAL = "approval?name={name}&confidence={confidence}&image={image}"
    const val EVENTS = "events"
    const val SETTINGS = "settings"
    const val CALL = "call/{sessionId}"

    fun approval(name: String = "", confidence: Double = 0.0, image: String = "") =
        "approval?name=$name&confidence=$confidence&image=$image"
    fun call(sessionId: String) = "call/$sessionId"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LOADING,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(tween(200)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)) },
    ) {
        composable(Routes.LOADING) {
            LoadingScreen(
                onLoaded = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOADING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToApproval = { navController.navigate(Routes.approval()) },
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToCall = { sessionId -> navController.navigate(Routes.call(sessionId)) },
            )
        }
        composable(
            route = Routes.APPROVAL,
            arguments = listOf(
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("confidence") { type = NavType.FloatType; defaultValue = 0f },
                navArgument("image") { type = NavType.StringType; defaultValue = "" },
            )
        ) { backStackEntry ->
            ApprovalScreen(
                name = backStackEntry.arguments?.getString("name") ?: "",
                confidence = backStackEntry.arguments?.getFloat("confidence")?.toDouble() ?: 0.0,
                imageBase64 = backStackEntry.arguments?.getString("image") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.EVENTS) {
            EventsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.CALL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            CallScreen(
                sessionId = backStackEntry.arguments?.getString("sessionId") ?: "",
                onEnd = { navController.popBackStack() },
            )
        }
    }
}
