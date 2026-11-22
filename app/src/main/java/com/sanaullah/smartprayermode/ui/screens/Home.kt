package com.sanaullah.smartprayermode.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanaullah.smartprayermode.admin.DeviceOwnerHelper
import com.sanaullah.smartprayermode.domain.HijriUtil
import com.sanaullah.smartprayermode.domain.Prayer
import com.sanaullah.smartprayermode.domain.PrayerCalculator
import com.sanaullah.smartprayermode.scheduler.PrayerSession
import com.sanaullah.smartprayermode.ui.AppViewModel
import com.sanaullah.smartprayermode.ui.Chip
import com.sanaullah.smartprayermode.ui.GoldButton
import com.sanaullah.smartprayermode.ui.GoldCard
import com.sanaullah.smartprayermode.ui.LocationUi
import com.sanaullah.smartprayermode.ui.ScreenScaffold
import com.sanaullah.smartprayermode.ui.rememberNowMillis
import com.sanaullah.smartprayermode.ui.theme.Emerald
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.GoldSoft
import com.sanaullah.smartprayermode.ui.theme.Ivory
import com.sanaullah.smartprayermode.ui.theme.MosqueSilhouette
import com.sanaullah.smartprayermode.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenTimes: () -> Unit,
    onOpenAzan: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenDeviceOwner: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locState by vm.location.collectAsStateWithLifecycle()
    val session by PrayerSession.active.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val now by rememberNowMillis(1000L)

    val zone = PrayerCalculator.zoneOf(s)
    val nowInstant = Instant.ofEpochMilli(now)
    val zoned = nowInstant.atZone(zone)
    val today = zoned.toLocalDate()

    ScreenScaffold("Smart Prayer Mode") {
        // Location row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, null, tint = Gold)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(s.locationLabel, color = Ivory, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !s.locationSet -> "Tap refresh to set your location"
                        s.locationIsAuto -> "Auto location · ${zone.id}"
                        else -> "Manual location · ${zone.id}"
                    },
                    color = Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = {
                if (s.autoLocation && vm.hasLocationPermission()) vm.refreshLocation() else onOpenLocation()
            }) { Icon(Icons.Filled.Refresh, "Refresh location", tint = Gold) }
        }
        if (locState is LocationUi.Loading) Text("Finding your location…", color = GoldSoft)
        if (locState is LocationUi.Error) Text((locState as LocationUi.Error).message, color = Gold)

        // Clock
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Fmt.clock(nowInstant, zone, is24, seconds = true),
                color = Ivory, fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
            )
            Text(
                "${DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()).format(zoned)}  |  " +
                    HijriUtil.toHijri(today),
                color = Ivory.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        MosqueSilhouette(Modifier.fillMaxWidth().height(110.dp))

        if (!s.locationSet) {
            GoldCard {
                Text("Set your location to see prayer times", color = Ivory, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use GPS once, or pick your city manually.",
                    color = Ivory.copy(alpha = 0.75f), modifier = Modifier.padding(vertical = 8.dp)
                )
                GoldButton("Set location", onOpenLocation, icon = Icons.Filled.LocationOn)
            }
            return@ScreenScaffold
        }

        val next = PrayerCalculator.nextObligatory(s, nowInstant)
        val day = remember(s, today) { PrayerCalculator.calculate(s, today) }

        // Next prayer card
        GoldCard(highlight = true) {
            Text("Next prayer", color = GoldSoft, style = MaterialTheme.typography.bodyMedium)
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(next.prayer.label, color = Ivory, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            Fmt.clock(next.time, zone, is24),
                            color = Ivory.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        Fmt.hms(next.time.toEpochMilli() - now),
                        color = Emerald.copy(red = 0.55f, green = 1f, blue = 0.55f),
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                    )
                }
            }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (s.azanEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, null,
                    tint = Gold, modifier = Modifier.padding(end = 0.dp)
                )
                Chip(if (s.azanEnabled) "Azan ON" else "Azan OFF")
                Chip(
                    when {
                        session != null -> "Prayer Mode ACTIVE"
                        s.autoPrayerMode -> "Auto Prayer Mode: armed"
                        else -> "Auto Prayer Mode: off"
                    },
                    if (session != null) Emerald else Gold
                )
            }
        }

        // Prayer list
        GoldCard {
            Prayer.entries.forEach { prayer ->
                val time = day.of(prayer)
                val isNext = next != null && next.prayer == prayer && next.time.atZone(zone).toLocalDate() == today
                val passed = time.isBefore(nowInstant)
                PrayerRow(prayer.label, Fmt.clock(time, zone, is24), isNext, passed)
            }
        }

        // Prayer Mode readiness
        GoldCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Gold, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (DeviceOwnerHelper.isDeviceOwner(ctx)) "Full device lock ready"
                        else "Standard Prayer Mode (no device lock)",
                        color = Ivory, style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (DeviceOwnerHelper.isDeviceOwner(ctx)) "Other apps are blocked during prayer; emergency calls stay available."
                        else "Opens a full-screen prayer screen. Set up Device Owner for a true lock.",
                        color = Ivory.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoldButton("Prayer times", onOpenTimes, Modifier.weight(1f))
            GoldButton("Azan audio", onOpenAzan, Modifier.weight(1f), icon = Icons.Filled.NotificationsActive)
        }
        GoldButton("Device Owner setup", onOpenDeviceOwner)
    }
}

@Composable
private fun PrayerRow(name: String, time: String, isNext: Boolean, passed: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = if (isNext) Gold else Ivory.copy(alpha = if (passed) 0.55f else 1f),
            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            time,
            color = if (isNext) Gold else Ivory.copy(alpha = if (passed) 0.55f else 1f),
            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.titleMedium
        )
        if (passed) {
            Icon(Icons.Filled.CheckCircle, null, tint = Emerald, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun PrayerTimesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val zone = PrayerCalculator.zoneOf(s)
    var date by remember { mutableStateOf(Instant.now().atZone(zone).toLocalDate()) }

    ScreenScaffold("Prayer times", onBack) {
        if (!s.locationSet) {
            Text("Set your location first (Home → Set location).", color = Ivory)
            return@ScreenScaffold
        }
        val day = remember(s, date) { PrayerCalculator.calculate(s, date) }
        val today = Instant.now().atZone(zone).toLocalDate()

        GoldCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = date.minusDays(1) }) {
                    Text("‹", color = Gold, fontSize = 28.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()).format(date),
                        color = Ivory, style = MaterialTheme.typography.titleMedium
                    )
                    Text(HijriUtil.toHijri(date).toString(), color = GoldSoft, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { date = date.plusDays(1) }) {
                    Text("›", color = Gold, fontSize = 28.sp)
                }
            }
            if (date != today) {
                GoldButton("Back to today", { date = LocalDate.now(zone) }, Modifier.padding(top = 8.dp))
            }
        }
        GoldCard {
            Prayer.entries.forEach { p ->
                PrayerRow(p.label, Fmt.clock(day.of(p), zone, is24), isNext = false, passed = false)
            }
        }
        GoldCard {
            Text("Calculation", color = Gold, style = MaterialTheme.typography.labelLarge)
            Text(PrayerCalculator.methodLabel(s.calculationMethod), color = Ivory)
            Text(
                "School: ${if (s.madhab == "HANAFI") "Hanafi" else "Shafi (Shafi, Maliki, Hanbali)"}",
                color = Ivory.copy(alpha = 0.8f)
            )
            val adj = listOf(
                "Fajr" to s.adjFajr, "Sunrise" to s.adjSunrise, "Dhuhr" to s.adjDhuhr,
                "Asr" to s.adjAsr, "Maghrib" to s.adjMaghrib, "Isha" to s.adjIsha
            ).filter { it.second != 0 }
            if (adj.isNotEmpty()) {
                Text(
                    "Manual adjustments: " + adj.joinToString { "${it.first} ${"%+d".format(it.second)} min" },
                    color = Ivory.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall
                )
            }
            Text("Times shown in ${zone.id}", color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
