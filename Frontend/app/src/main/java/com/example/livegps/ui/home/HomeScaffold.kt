package com.example.livegps.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.livegps.ui.alerts.AlertsScreen
import com.example.livegps.ui.components.AccuracySegmentedButtons
import com.example.livegps.ui.components.AppHeader
import com.example.livegps.ui.dashboard.DashboardScreen
import com.example.livegps.ui.devices.DevicesScreen
import com.example.livegps.ui.history.HistoryScreen
import com.example.livegps.ui.more.MoreScreen

private data class HomeTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

/**
 * The post-onboarding shell: a shared "Live GPS" header + accuracy control on
 * top, a 5-tab bottom navigation bar, and the active tab's content between.
 */
@Composable
fun HomeScaffold(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val highAccuracy by viewModel.highAccuracy.collectAsStateWithLifecycle()
    val unackedAlerts by viewModel.unackedAlerts.collectAsStateWithLifecycle()

    val tabs = listOf(
        HomeTab("dashboard", "Dashboard", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
        HomeTab("devices", "Devices", Icons.Filled.Devices, Icons.Outlined.Devices),
        HomeTab("history", "History", Icons.Filled.History, Icons.Outlined.History),
        HomeTab("alerts", "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
        HomeTab("more", "More", Icons.Filled.MoreHoriz, Icons.Filled.MoreHoriz),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                    val backStack by navController.currentBackStackEntryAsState()
                    val current = backStack?.destination?.route
                    tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (current != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                val iconComposable = @Composable {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label,
                                    )
                                }
                                if (tab.route == "alerts" && unackedAlerts > 0) {
                                    BadgedBox(badge = { Badge() }) { iconComposable() }
                                } else {
                                    iconComposable()
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppHeader(onSettingsClick = onOpenSettings)
            AccuracySegmentedButtons(
                highAccuracy = highAccuracy,
                onChange = viewModel::setHighAccuracy,
            )
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(140)) },
            ) {
                composable("dashboard") { DashboardScreen() }
                composable("devices") { DevicesScreen() }
                composable("history") { HistoryScreen() }
                composable("alerts") { AlertsScreen() }
                composable("more") { MoreScreen(onOpenSettings = onOpenSettings) }
            }
        }
    }
}
