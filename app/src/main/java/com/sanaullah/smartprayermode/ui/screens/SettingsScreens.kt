package com.sanaullah.smartprayermode.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanaullah.smartprayermode.admin.DeviceOwnerHelper
import com.sanaullah.smartprayermode.audio.AzanSource
import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.domain.PrayerCalculator
import com.sanaullah.smartprayermode.scheduler.PrayerSessionManager
import com.sanaullah.smartprayermode.service.PrayerService
import com.sanaullah.smartprayermode.ui.AppViewModel
import com.sanaullah.smartprayermode.ui.Chip
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
import com.sanaullah.smartprayermode.ui.theme.GoldSoft
import com.sanaullah.smartprayermode.ui.theme.Ivory

private val dialogColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ivory, unfocusedTextColor = Ivory,
        focusedBorderColor = Gold, unfocusedBorderColor = Gold.copy(alpha = 0.4f),
        focusedLabelColor = Gold, unfocusedLabelColor = Ivory.copy(alpha = 0.7f), cursorColor = Gold,
    )

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value); onDismiss() }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value); onDismiss() },
                            colors = RadioButtonDefaults.colors(selectedColor = Gold)
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Gold) } }
    )
}

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenAzan: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRamadan: () -> Unit,
    onOpenTimes: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = settings ?: return
    var dialog by remember { mutableStateOf<String?>(null) }
    var customMinutes by remember { mutableStateOf("") }

    ScreenScaffold("Settings") {
        SectionLabel("Prayer Mode")
        GoldCard {
            SwitchRow(
                "Automatic Prayer Mode", "Lock screen at Azan time without opening the app",
                s.autoPrayerMode, { v -> vm.update { it.copy(autoPrayerMode = v) } },
                icon = Icons.Filled.Lock
            )
            NavRow("Prayer duration", "${s.prayerDurationMinutes} minutes", Icons.Filled.Timer) { dialog = "duration" }
            SwitchRow(
                "DND during prayer", "Silence notifications while Prayer Mode is active",
                s.dndDuringPrayer,
                { v ->
                    if (v && !PermissionStatus.read(ctx).dndAccess) {
                        openSystemSettings(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, withPackage = false)
                    }
                    vm.update { it.copy(dndDuringPrayer = v) }
                },
                icon = Icons.Filled.NightsStay
            )
            SwitchRow(
                "Vibration", "Vibrate when the prayer time starts",
                s.vibration, { v -> vm.update { it.copy(vibration = v) } },
                icon = Icons.Filled.Vibration
            )
            SwitchRow(
                "Emergency access", "Always enabled — the emergency call button is never removed",
                true, {}, icon = Icons.Filled.Call, enabled = false
            )
        }

        SectionLabel("Location")
        GoldCard {
            SwitchRow(
                "Automatic location", "Refresh once when the app opens (never tracked in the background)",
                s.autoLocation, { v -> vm.update { it.copy(autoLocation = v) } },
                icon = Icons.Filled.LocationOn
            )
            NavRow("Current location", s.locationLabel, Icons.Filled.LocationOn, onOpenLocation)
        }

        SectionLabel("Azan & notifications")
        GoldCard {
            NavRow(
                "Notification settings",
                if (!s.notifEnabled) "Off" else if (s.reminderMinutes > 0) "On · reminder ${s.reminderMinutes} min before" else "On · no reminder",
                Icons.Filled.Notifications, onOpenNotifications
            )
            SwitchRow(
                "Azan audio", "Play the Azan at prayer time",
                s.azanEnabled, { v -> vm.update { it.copy(azanEnabled = v) } },
                icon = Icons.Filled.MusicNote
            )
            NavRow("Select Azan", AzanSource.fromName(s.azanSource).label, Icons.Filled.MusicNote, onOpenAzan)
        }

        SectionLabel("Calculation")
        GoldCard {
            NavRow("Calculation method", PrayerCalculator.methodLabel(s.calculationMethod)) { dialog = "method" }
            NavRow("School of law", if (s.madhab == "HANAFI") "Hanafi" else "Shafi (Shafi, Maliki, Hanbali)") { dialog = "madhab" }
            NavRow("Prayer times", "View and verify today's times", onClick = onOpenTimes)
            NavRow("Ramadan, Sehri & Iftar", icon = Icons.Filled.Alarm, onClick = onOpenRamadan)
        }

        NavRow("Advanced settings", "Adjustments, permissions, Device Owner", onClick = onOpenAdvanced)
    }

    when (dialog) {
        "duration" -> {
            val presets = listOf(5, 10, 15, 20, 30)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Prayer duration") },
                text = {
                    Column {
                        presets.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    vm.update { it.copy(prayerDurationMinutes = m) }; dialog = null
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = s.prayerDurationMinutes == m,
                                    onClick = { vm.update { it.copy(prayerDurationMinutes = m) }; dialog = null },
                                    colors = RadioButtonDefaults.colors(selectedColor = Gold)
                                )
                                Text("$m minutes")
                            }
                        }
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter(Char::isDigit).take(2) },
                            label = { Text("Custom (1–60 minutes)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = dialogColors,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            customMinutes.toIntOrNull()?.let { m ->
                                vm.update {
                                    it.copy(
                                        prayerDurationMinutes = m.coerceIn(
                                            SettingsEntity.MIN_DURATION, SettingsEntity.MAX_DURATION
                                        )
                                    )
                                }
                            }
                            customMinutes = ""
                            dialog = null
                        }
                    ) { Text("Set custom", color = Gold) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel", color = Gold) } }
            )
        }
        "method" -> ChoiceDialog(
            "Calculation method",
            PrayerCalculator.methods.map { it.id to it.label },
            s.calculationMethod,
            { id -> vm.update { it.copy(calculationMethod = id) } },
            { dialog = null }
        )
        "madhab" -> ChoiceDialog(
            "School of law (Asr time)",
            listOf("SHAFI" to "Shafi (Shafi, Maliki, Hanbali)", "HANAFI" to "Hanafi"),
            s.madhab,
            { id -> vm.update { it.copy(madhab = id) } },
            { dialog = null }
        )
    }
}

@Composable
private fun AdjustRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ivory, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((minutes - 1).coerceAtLeast(-30)) }) {
            Text("−", color = Gold, fontSize = 24.sp)
        }
        Text("%+d min".format(minutes), color = if (minutes == 0) Ivory.copy(alpha = 0.7f) else GoldSoft)
        IconButton(onClick = { onChange((minutes + 1).coerceAtMost(30)) }) {
            Text("+", color = Gold, fontSize = 24.sp)
        }
    }
}

@Composable
fun AdvancedSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenDeviceOwner: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = settings ?: return
    var confirmReset by remember { mutableStateOf(false) }

    ScreenScaffold("Advanced settings", onBack) {
        SectionLabel("Manual time adjustments (minutes)")
        GoldCard {
            AdjustRow("Fajr", s.adjFajr) { v -> vm.update { it.copy(adjFajr = v) } }
            AdjustRow("Sunrise", s.adjSunrise) { v -> vm.update { it.copy(adjSunrise = v) } }
            AdjustRow("Dhuhr", s.adjDhuhr) { v -> vm.update { it.copy(adjDhuhr = v) } }
            AdjustRow("Asr", s.adjAsr) { v -> vm.update { it.copy(adjAsr = v) } }
            AdjustRow("Maghrib", s.adjMaghrib) { v -> vm.update { it.copy(adjMaghrib = v) } }
            AdjustRow("Isha", s.adjIsha) { v -> vm.update { it.copy(adjIsha = v) } }
        }

        SectionLabel("Reliability")
        GoldCard {
            NavRow("Permissions & battery", "Exact alarms, overlay, battery, notifications", onClick = onOpenPermissions)
            NavRow("Device Owner / kiosk lock", if (DeviceOwnerHelper.isDeviceOwner(ctx)) "Active" else "Not set up", Icons.Filled.Lock, onOpenDeviceOwner)
        }

        SectionLabel("Testing")
        GoldCard {
            Text(
                "Starts a 20-second Prayer Mode right now (without Azan) so you can check the screen, " +
                    "emergency button and automatic unlock.",
                color = Ivory.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            GoldButton("Test Prayer Mode (20 s)", { PrayerService.startTest(ctx, 20) })
            Text(
                "To check scheduling end-to-end, set the phone clock to just before a prayer time.",
                color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        SectionLabel("Reset")
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset settings to default", color = Gold)
        }
        Text(
            "Your saved location is kept. No personal data is collected or sent anywhere.",
            color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("Azan, duration, calculation method, adjustments and behaviour switches return to their defaults.") },
            confirmButton = {
                TextButton(onClick = { vm.resetToDefaults(); confirmReset = false }) { Text("Reset", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Gold) } }
        )
    }
}

@Composable
fun DeviceOwnerGuideScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var confirmRelease by remember { mutableStateOf(false) }
    val isOwner = remember(refresh) { DeviceOwnerHelper.isDeviceOwner(ctx) }
    val canLock = remember(refresh) { DeviceOwnerHelper.canLock(ctx) }

    ScreenScaffold("Device Owner setup", onBack) {
        GoldCard(highlight = isOwner) {
            Text(
                if (isOwner) "Device Owner: ACTIVE" else "Device Owner: not set up",
                color = if (isOwner) Emerald else Gold, style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (isOwner) {
                    if (canLock) "Prayer Mode will lock the device (Lock Task). Emergency calling stays available."
                    else "Device Owner is active but Lock Task is not yet allowlisted. Tap “Apply lock policy”."
                } else {
                    "Without Device Owner, Prayer Mode is a full-screen screen only. Android does not allow " +
                        "a normal app to block other apps, so nothing is locked."
                },
                color = Ivory.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp)
            )
        }

        SectionLabel("Setup steps")
        GoldCard {
            listOf(
                "1. Install this app.",
                "2. Remove every account from the phone (Settings → Accounts / Passwords & accounts), including Google, Samsung and Xiaomi accounts. Android refuses Device Owner setup while accounts exist on most phones. A freshly reset phone is easiest.",
                "3. Turn on Developer options and USB debugging (tap Build number 7 times).",
                "4. Connect the phone to a computer with adb installed and run the command below.",
                "5. Reopen this screen; it should say “Device Owner: ACTIVE”.",
                "6. Use Advanced → Test Prayer Mode to verify the lock and the hold-to-exit button.",
            ).forEach {
                Text(it, color = Ivory, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        GoldCard {
            Text("adb command", color = Gold, style = MaterialTheme.typography.labelLarge)
            Text(
                DeviceOwnerHelper.ADB_COMMAND,
                color = Ivory, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            GoldButton("Copy command", {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", DeviceOwnerHelper.ADB_COMMAND))
            })
        }

        SectionLabel("Safety")
        GoldCard {
            listOf(
                "The Emergency Call button (dialer is allowlisted) is always on the Prayer Mode screen.",
                "Press and hold “Hold to end Prayer Mode” for 3 seconds to leave at any time.",
                "Prayer Mode always ends by itself at the configured time, even after a crash or restart.",
                "Device Owner cannot be removed from Android Settings. Use the button below, which gives it up cleanly.",
            ).forEach { Text("• $it", color = Ivory, modifier = Modifier.padding(vertical = 3.dp)) }
        }

        if (isOwner) {
            GoldButton("Apply lock policy", {
                DeviceOwnerHelper.applyLockTaskPolicy(ctx); refresh++
            })
            OutlinedButton(onClick = { confirmRelease = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Release Device Owner", color = Gold)
            }
        }
        GoldButton("Test Prayer Mode (20 s)", { PrayerService.startTest(ctx, 20) })
        Chip("Needs Android 10+ · adb required")
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("Release Device Owner?") },
            text = { Text("The phone returns to normal management and Prayer Mode will no longer lock the device.") },
            confirmButton = {
                TextButton(onClick = {
                    PrayerSessionManager.end(ctx)
                    DeviceOwnerHelper.releaseDeviceOwner(ctx)
                    confirmRelease = false
                    refresh++
                }) { Text("Release", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text("Cancel", color = Gold) } }
        )
    }
}
