package com.sanaullah.smartprayermode.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanaullah.smartprayermode.audio.AzanSource
import com.sanaullah.smartprayermode.service.PrayerNotifications
import com.sanaullah.smartprayermode.ui.AppViewModel
import com.sanaullah.smartprayermode.ui.GoldButton
import com.sanaullah.smartprayermode.ui.GoldCard
import com.sanaullah.smartprayermode.ui.NavRow
import com.sanaullah.smartprayermode.ui.PermissionStatus
import com.sanaullah.smartprayermode.ui.ScreenScaffold
import com.sanaullah.smartprayermode.ui.SectionLabel
import com.sanaullah.smartprayermode.ui.SwitchRow
import com.sanaullah.smartprayermode.ui.openSystemSettings
import com.sanaullah.smartprayermode.ui.theme.Emerald
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.Ivory

private fun openAppNotificationSettings(ctx: Context, channelId: String? = null) {
    val intent = if (channelId == null) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    }
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        openSystemSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
}

@Composable
private fun StatusRow(title: String, detail: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
            tint = if (ok) Emerald else Gold, modifier = Modifier.padding(end = 12.dp)
        )
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, color = Ivory, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) {
            OutlinedButton(onClick = onAction) { Text(actionLabel, color = Gold) }
        }
    }
}

@Composable
fun NotificationSettingsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenAzan: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    val status = remember(tick, resume) { PermissionStatus.read(ctx) }
    var reminderDialog by remember { mutableStateOf(false) }
    var testSent by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val s = settings ?: return

    ScreenScaffold("Notifications", onBack) {
        SectionLabel("Status")
        GoldCard {
            StatusRow(
                "Notifications", "Needed to show prayer alerts and reminders",
                status.notifications, "Allow"
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotificationSettings(ctx)
                }
            }
            StatusRow(
                "Exact alarms", "Alerts and reminders fire on the exact minute",
                status.exactAlarm, "Open settings"
            ) { openSystemSettings(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
            StatusRow(
                "Full-screen alerts", "Lets Prayer Mode open over the lock screen (Android 14+)",
                status.fullScreen, "Open settings"
            ) {
                if (Build.VERSION.SDK_INT >= 34) {
                    openSystemSettings(ctx, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }
            }
            StatusRow(
                "Unrestricted battery", "Stops the system from delaying alerts",
                status.batteryUnrestricted, "Open settings"
            ) { openSystemSettings(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withPackage = false) }
        }

        SectionLabel("Prayer alerts")
        GoldCard {
            SwitchRow(
                "Prayer notifications", "Master switch for alerts and reminders",
                s.notifEnabled, { v -> vm.update { it.copy(notifEnabled = v) } },
                icon = Icons.Filled.Notifications
            )
            SwitchRow("Fajr", "Notification, reminder and Azan", s.notifFajr, { v -> vm.update { it.copy(notifFajr = v) } }, enabled = s.notifEnabled)
            SwitchRow("Dhuhr", null, s.notifDhuhr, { v -> vm.update { it.copy(notifDhuhr = v) } }, enabled = s.notifEnabled)
            SwitchRow("Asr", null, s.notifAsr, { v -> vm.update { it.copy(notifAsr = v) } }, enabled = s.notifEnabled)
            SwitchRow("Maghrib", null, s.notifMaghrib, { v -> vm.update { it.copy(notifMaghrib = v) } }, enabled = s.notifEnabled)
            SwitchRow("Isha", null, s.notifIsha, { v -> vm.update { it.copy(notifIsha = v) } }, enabled = s.notifEnabled)
            Text(
                "Turning a prayer off silences its notification, reminder and Azan. Prayer Mode has its own switch in Settings.",
                color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        SectionLabel("Reminder")
        GoldCard {
            NavRow(
                "Remind me before each prayer",
                if (s.reminderMinutes <= 0) "Off" else "${s.reminderMinutes} minutes before",
                Icons.Filled.Timer
            ) { reminderDialog = true }
        }

        SectionLabel("Sound, vibration & privacy")
        GoldCard {
            NavRow(
                "Azan audio", if (s.azanEnabled) AzanSource.fromName(s.azanSource).label else "Off",
                Icons.Filled.MusicNote, onOpenAzan
            )
            SwitchRow(
                "Vibration", "Vibrate when a prayer time starts",
                s.vibration, { v -> vm.update { it.copy(vibration = v) } },
                icon = Icons.Filled.Vibration
            )
            SwitchRow(
                "Show details on lock screen", "When off, the lock screen shows only “Prayer notification”",
                s.notifOnLockScreen, { v -> vm.update { it.copy(notifOnLockScreen = v) } },
                icon = Icons.Filled.Lock
            )
        }

        SectionLabel("System controls")
        GoldCard {
            NavRow("All notification settings", "Open Android's settings for this app", Icons.Filled.Notifications) {
                openAppNotificationSettings(ctx)
            }
            NavRow("Prayer alert channel", "Importance, lock screen, override Do Not Disturb", Icons.Filled.NotificationsActive) {
                openAppNotificationSettings(ctx, PrayerNotifications.CH_PRAYER)
            }
            NavRow("Reminder channel", "Reminder sound, vibration and importance", Icons.Filled.Alarm) {
                openAppNotificationSettings(ctx, PrayerNotifications.CH_REMINDER)
            }
        }

        SectionLabel("Test")
        GoldButton("Send test notifications", {
            vm.sendTestNotifications()
            testSent = true
        }, icon = Icons.Filled.NotificationsActive)
        if (testSent) {
            Text(
                if (status.notifications) "Sent a sample reminder and prayer alert. Pull down the notification shade to see them."
                else "Sent, but notifications are blocked. Tap “Allow” above first.",
                color = if (status.notifications) Emerald else Gold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (reminderDialog) {
        ChoiceDialog(
            "Reminder before prayer",
            listOf(0 to "Off", 5 to "5 minutes", 10 to "10 minutes", 15 to "15 minutes", 20 to "20 minutes", 30 to "30 minutes"),
            s.reminderMinutes,
            { m -> vm.update { it.copy(reminderMinutes = m) } },
            { reminderDialog = false }
        )
    }
}
