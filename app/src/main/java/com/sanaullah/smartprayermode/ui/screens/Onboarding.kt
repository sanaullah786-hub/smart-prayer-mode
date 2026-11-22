package com.sanaullah.smartprayermode.ui.screens

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.location.CityCatalog
import com.sanaullah.smartprayermode.ui.AppViewModel
import com.sanaullah.smartprayermode.ui.GoldButton
import com.sanaullah.smartprayermode.ui.GoldCard
import com.sanaullah.smartprayermode.ui.LocationUi
import com.sanaullah.smartprayermode.ui.NavRow
import com.sanaullah.smartprayermode.ui.PermissionStatus
import com.sanaullah.smartprayermode.ui.ScreenScaffold
import com.sanaullah.smartprayermode.ui.SectionLabel
import com.sanaullah.smartprayermode.ui.openSystemSettings
import com.sanaullah.smartprayermode.ui.theme.Emerald
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.GoldSoft
import com.sanaullah.smartprayermode.ui.theme.IslamicBackground
import com.sanaullah.smartprayermode.ui.theme.Ivory
import com.sanaullah.smartprayermode.ui.theme.MosqueSilhouette
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(settings: SettingsEntity?, onReady: (onboardingDone: Boolean) -> Unit) {
    LaunchedEffect(settings != null) {
        if (settings != null) {
            delay(1400)
            onReady(settings.onboardingDone)
        }
    }
    Box(Modifier.fillMaxSize().background(IslamicBackground), contentAlignment = Alignment.Center) {
        MosqueSilhouette(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("اللهُ أَكْبَر", color = Gold, fontSize = 52.sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.height(16.dp))
            Text("Smart", style = MaterialTheme.typography.headlineMedium, color = Ivory)
            Text("Prayer Mode", style = MaterialTheme.typography.headlineLarge, color = Gold)
            Text(
                "Pray on time… Live better",
                color = Ivory.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    ScreenScaffold("Welcome") {
        MosqueSilhouette(Modifier.fillMaxWidth().height(160.dp))
        Text(
            "Assalamu Alaikum",
            style = MaterialTheme.typography.headlineMedium,
            color = Gold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            "Smart Prayer Mode calculates accurate prayer times for your location, plays the Azan, " +
                "and opens a calm full-screen Prayer Mode at each prayer — automatically, without " +
                "you having to open the app.",
            color = Ivory, textAlign = TextAlign.Center
        )
        GoldCard {
            listOf(
                "Accurate prayer times (Hanafi & Shafi, many methods)",
                "Azan at prayer time with your chosen recording",
                "Prayer Mode screen with countdown and emergency call",
                "Optional full device lock when set up as Device Owner",
                "No ads, no tracking, no data leaves your device",
            ).forEach {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Emerald, modifier = Modifier.padding(end = 8.dp))
                    Text(it, color = Ivory)
                }
            }
        }
        GoldButton("Get started", onContinue)
    }
}

@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
fun PermissionSetupScreen(onBack: (() -> Unit)?, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val resumeTick = rememberResumeTick()
    val status = remember(tick, resumeTick) { PermissionStatus.read(ctx) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    ScreenScaffold("Permissions", onBack) {
        Text(
            "Smart Prayer Mode only asks for what it needs. Nothing is ever shared; your location " +
                "stays on this device and is read only when you set up or refresh it.",
            color = Ivory.copy(alpha = 0.85f)
        )

        SectionLabel("Essential")
        PermissionRow(
            "Location", "Finds your city for accurate prayer times. Read once, never tracked.",
            status.location, "Allow"
        ) {
            locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        PermissionRow(
            "Notifications", "Shows the prayer-time alert.",
            status.notifications, "Allow"
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermissionRow(
            "Exact alarms", "Starts the Azan exactly on time. Without it the app uses a slightly less " +
                "battery-friendly alarm-clock alarm.",
            status.exactAlarm, "Open settings"
        ) { openSystemSettings(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }

        SectionLabel("Recommended for reliable automatic Prayer Mode")
        PermissionRow(
            "Display over other apps", "Lets Prayer Mode open on top of whatever app you are using.",
            status.overlay, "Open settings"
        ) { openSystemSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
        PermissionRow(
            "Full-screen alerts", "Lets Prayer Mode open over the lock screen (Android 14+).",
            status.fullScreen, "Open settings"
        ) {
            if (Build.VERSION.SDK_INT >= 34) {
                openSystemSettings(ctx, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
        PermissionRow(
            "Unrestricted battery", "Stops the system from delaying prayer alarms on some phones.",
            status.batteryUnrestricted, "Open settings"
        ) { openSystemSettings(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withPackage = false) }

        SectionLabel("Optional")
        PermissionRow(
            "Do Not Disturb access", "Only needed if you turn on “DND during prayer”.",
            status.dndAccess, "Open settings"
        ) { openSystemSettings(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, withPackage = false) }

        GoldButton("Continue", onContinue)
        Text(
            "You can change any of these later in Settings → Advanced.",
            color = Ivory.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    GoldCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                tint = if (granted) Emerald else Gold,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(title, color = Ivory, style = MaterialTheme.typography.titleMedium)
                Text(description, color = Ivory.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!granted) {
            OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel, color = Gold)
            }
        }
    }
}

@Composable
fun LocationSetupScreen(vm: AppViewModel, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.location.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) vm.refreshLocation() else vm.permissionDenied()
    }

    val s = settings ?: return
    ScreenScaffold("Location", onBack) {
        GoldCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = Gold, modifier = Modifier.padding(end = 12.dp))
                Column {
                    Text("Current location", color = Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text(s.locationLabel, color = Ivory, style = MaterialTheme.typography.titleMedium)
                    if (s.locationSet) {
                        Text(
                            if (s.locationIsAuto) "Detected automatically" else "Chosen manually",
                            color = GoldSoft, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        GoldButton(
            if (state is LocationUi.Loading) "Finding location…" else "Detect my location",
            onClick = {
                if (vm.hasLocationPermission()) {
                    vm.refreshLocation()
                } else {
                    launcher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            },
            enabled = state !is LocationUi.Loading,
            icon = Icons.Filled.LocationOn
        )
        when (val st = state) {
            is LocationUi.Error -> Text(st.message, color = Gold)
            is LocationUi.Success -> Text("Location set to ${st.label}", color = Emerald)
            else -> Unit
        }

        SectionLabel("Or choose a city")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search city or country") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ivory, unfocusedTextColor = Ivory,
                focusedBorderColor = Gold, unfocusedBorderColor = Gold.copy(alpha = 0.4f),
                focusedLabelColor = Gold, unfocusedLabelColor = Ivory.copy(alpha = 0.7f),
                cursorColor = Gold,
            )
        )
        GoldCard {
            val results = CityCatalog.search(query)
            if (results.isEmpty()) {
                Text("No matching city. Try another name.", color = Ivory.copy(alpha = 0.7f))
            }
            results.take(15).forEach { city ->
                NavRow(city.name, city.country) { vm.selectCity(city) }
            }
            if (results.size > 15) {
                Text("Keep typing to narrow ${results.size} results…", color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }

        GoldButton("Continue", onClick = onDone, enabled = s.locationSet)
        Spacer(Modifier.height(8.dp))
    }
}
