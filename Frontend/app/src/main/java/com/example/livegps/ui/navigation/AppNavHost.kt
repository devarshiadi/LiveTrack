package com.example.livegps.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.livegps.data.local.SettingsStore
import com.example.livegps.ui.home.HomeScaffold
import com.example.livegps.ui.onboarding.BatteryOptimizationScreen
import com.example.livegps.ui.onboarding.PermissionScreen
import com.example.livegps.ui.onboarding.WelcomeScreen
import com.example.livegps.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first

/** Navigation route ids. */
object Routes {
    const val WELCOME = "welcome"
    const val PERMISSIONS = "permissions"
    const val BATTERY = "battery"
    const val MAP = "map"
    const val SETTINGS = "settings"
}

/**
 * App navigation graph. Onboarding (welcome → permissions → battery) is shown
 * on first run; afterwards the app opens straight to the map.
 */
@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Decide the start destination once, after reading the onboarding flag.
    var startRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val onboarded = SettingsStore(context).onboarded.first()
        startRoute = if (onboarded) Routes.MAP else Routes.WELCOME
    }
    val start = startRoute ?: return // brief blank frame while the flag loads

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(Routes.PERMISSIONS) })
        }
        composable(Routes.PERMISSIONS) {
            PermissionScreen(onContinue = { navController.navigate(Routes.BATTERY) })
        }
        composable(Routes.BATTERY) {
            BatteryOptimizationScreen(
                onContinue = {
                    // Mark onboarding done so future launches open straight to the map.
                    scope.launch { SettingsStore(context).setOnboarded(true) }
                    navController.navigate(Routes.MAP) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAP) {
            HomeScaffold(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
