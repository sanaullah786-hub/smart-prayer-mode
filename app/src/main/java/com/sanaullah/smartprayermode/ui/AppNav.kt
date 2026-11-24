package com.sanaullah.smartprayermode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanaullah.smartprayermode.ui.screens.AdvancedSettingsScreen
import com.sanaullah.smartprayermode.ui.screens.AudioPlayerScreen
import com.sanaullah.smartprayermode.ui.screens.AzanSelectionScreen
import com.sanaullah.smartprayermode.ui.screens.DeviceOwnerGuideScreen
import com.sanaullah.smartprayermode.ui.screens.HijriCalendarScreen
import com.sanaullah.smartprayermode.ui.screens.HomeScreen
import com.sanaullah.smartprayermode.ui.screens.LocationSetupScreen
import com.sanaullah.smartprayermode.ui.screens.PermissionSetupScreen
import com.sanaullah.smartprayermode.ui.screens.PrayerTimesScreen
import com.sanaullah.smartprayermode.ui.screens.NotificationSettingsScreen
import com.sanaullah.smartprayermode.ui.screens.QiblaScreen
import com.sanaullah.smartprayermode.ui.screens.RamadanScreen
import com.sanaullah.smartprayermode.ui.screens.SettingsScreen
import com.sanaullah.smartprayermode.ui.screens.SplashScreen
import com.sanaullah.smartprayermode.ui.screens.WelcomeScreen
import com.sanaullah.smartprayermode.ui.theme.DeepGreen
import com.sanaullah.smartprayermode.ui.theme.ForestGreen
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.IslamicBackground
import com.sanaullah.smartprayermode.ui.theme.Ivory

private object Route {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val PERMISSIONS = "permissions"
    const val LOCATION = "location"
    const val HOME = "home"
    const val TIMES = "times"
    const val AZAN = "azan"
    const val PLAYER = "player"
    const val QIBLA = "qibla"
    const val HIJRI = "hijri"
    const val RAMADAN = "ramadan"
    const val SETTINGS = "settings"
    const val ADVANCED = "advanced"
    const val DEVICE_OWNER = "device_owner"
    const val NOTIFICATIONS = "notifications"
    // Onboarding variants that finish by going to Home
    const val SETUP_PERMISSIONS = "setup_permissions"
    const val SETUP_LOCATION = "setup_location"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.HOME, "Home", Icons.Filled.Home),
    Tab(Route.QIBLA, "Qibla", Icons.Filled.Explore),
    Tab(Route.HIJRI, "Calendar", Icons.Filled.CalendarMonth),
    Tab(Route.SETTINGS, "Settings", Icons.Filled.Settings),
)

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(Route.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    Box(Modifier.fillMaxSize().background(IslamicBackground)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (tabs.any { it.route == route }) {
                    NavigationBar(containerColor = ForestGreen) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { nav.goTab(tab.route) },
                                icon = { Icon(tab.icon, tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepGreen,
                                    selectedTextColor = Gold,
                                    indicatorColor = Gold,
                                    unselectedIconColor = Ivory.copy(alpha = 0.7f),
                                    unselectedTextColor = Ivory.copy(alpha = 0.7f),
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = Route.SPLASH, modifier = Modifier.padding(padding)) {
                composable(Route.SPLASH) {
                    SplashScreen(settings) { done ->
                        nav.navigate(if (done) Route.HOME else Route.WELCOME) {
                            popUpTo(Route.SPLASH) { inclusive = true }
                        }
                    }
                }
                composable(Route.WELCOME) { WelcomeScreen { nav.navigate(Route.SETUP_PERMISSIONS) } }
                composable(Route.SETUP_PERMISSIONS) {
                    PermissionSetupScreen(onBack = null) { nav.navigate(Route.SETUP_LOCATION) }
                }
                composable(Route.SETUP_LOCATION) {
                    LocationSetupScreen(vm, onBack = null) {
                        vm.completeOnboarding()
                        nav.navigate(Route.HOME) { popUpTo(Route.WELCOME) { inclusive = true } }
                    }
                }

                composable(Route.HOME) {
                    HomeScreen(
                        vm,
                        onOpenTimes = { nav.navigate(Route.TIMES) },
                        onOpenAzan = { nav.navigate(Route.AZAN) },
                        onOpenLocation = { nav.navigate(Route.LOCATION) },
                        onOpenDeviceOwner = { nav.navigate(Route.DEVICE_OWNER) },
                    )
                }
                composable(Route.TIMES) { PrayerTimesScreen(vm) { nav.popBackStack() } }
                composable(Route.AZAN) {
                    AzanSelectionScreen(vm, onBack = { nav.popBackStack() }, onOpenPlayer = { nav.navigate(Route.PLAYER) })
                }
                composable(Route.PLAYER) { AudioPlayerScreen(vm) { nav.popBackStack() } }
                composable(Route.QIBLA) { QiblaScreen(vm) }
                composable(Route.HIJRI) { HijriCalendarScreen(vm) }
                composable(Route.RAMADAN) { RamadanScreen(vm) { nav.popBackStack() } }
                composable(Route.LOCATION) {
                    LocationSetupScreen(vm, onBack = { nav.popBackStack() }) { nav.popBackStack() }
                }
                composable(Route.PERMISSIONS) {
                    PermissionSetupScreen(onBack = { nav.popBackStack() }) { nav.popBackStack() }
                }
                composable(Route.SETTINGS) {
                    SettingsScreen(
                        vm,
                        onOpenAzan = { nav.navigate(Route.AZAN) },
                        onOpenLocation = { nav.navigate(Route.LOCATION) },
                        onOpenAdvanced = { nav.navigate(Route.ADVANCED) },
                        onOpenRamadan = { nav.navigate(Route.RAMADAN) },
                        onOpenTimes = { nav.navigate(Route.TIMES) },
                        onOpenNotifications = { nav.navigate(Route.NOTIFICATIONS) },
                    )
                }
                composable(Route.NOTIFICATIONS) {
                    NotificationSettingsScreen(vm, onBack = { nav.popBackStack() }, onOpenAzan = { nav.navigate(Route.AZAN) })
                }
                composable(Route.ADVANCED) {
                    AdvancedSettingsScreen(
                        vm,
                        onBack = { nav.popBackStack() },
                        onOpenDeviceOwner = { nav.navigate(Route.DEVICE_OWNER) },
                        onOpenPermissions = { nav.navigate(Route.PERMISSIONS) },
                    )
                }
                composable(Route.DEVICE_OWNER) { DeviceOwnerGuideScreen { nav.popBackStack() } }
            }
        }
    }
}
