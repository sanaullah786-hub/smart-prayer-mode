package com.sanaullah.smartprayermode.ui.screens

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanaullah.smartprayermode.domain.HijriUtil
import com.sanaullah.smartprayermode.domain.Prayer
import com.sanaullah.smartprayermode.domain.PrayerCalculator
import com.sanaullah.smartprayermode.domain.QiblaUtil
import com.sanaullah.smartprayermode.ui.AppViewModel
import com.sanaullah.smartprayermode.ui.GoldCard
import com.sanaullah.smartprayermode.ui.ScreenScaffold
import com.sanaullah.smartprayermode.ui.SectionLabel
import com.sanaullah.smartprayermode.ui.theme.Emerald
import com.sanaullah.smartprayermode.ui.theme.Gold
import com.sanaullah.smartprayermode.ui.theme.GoldSoft
import com.sanaullah.smartprayermode.ui.theme.Ivory
import com.sanaullah.smartprayermode.ui.theme.MosqueSilhouette
import com.sanaullah.smartprayermode.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------------------
// Qibla compass
// ---------------------------------------------------------------------------------------

/** Compass heading (degrees clockwise from TRUE north), or null when no sensor/location. */
@Composable
private fun rememberHeading(lat: Double, lon: Double, enabled: Boolean): Pair<Float, Boolean> {
    val ctx = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(enabled, lat, lon) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            hasSensor = false
            return@DisposableEffect onDispose { }
        }
        val declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val azimuth = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + declination) + 360f) % 360f
                smoothed = if (smoothed.isNaN()) azimuth else {
                    // low-pass filter on the shortest angular difference
                    var diff = azimuth - smoothed
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    (smoothed + diff * 0.15f + 360f) % 360f
                }
                heading = smoothed
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) } // sensor released as soon as the screen leaves
    }
    return heading to hasSensor
}

@Composable
fun QiblaScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return

    ScreenScaffold("Qibla direction") {
        if (!s.locationSet) {
            Text("Set your location first (Home → Set location) to find the Qibla.", color = Ivory)
            return@ScreenScaffold
        }
        val bearing = remember(s.latitude, s.longitude) { QiblaUtil.bearing(s.latitude, s.longitude).toFloat() }
        val distance = remember(s.latitude, s.longitude) { QiblaUtil.distanceKm(s.latitude, s.longitude) }
        val (heading, hasSensor) = rememberHeading(s.latitude, s.longitude, enabled = true)
        val relative = (bearing - heading + 360f) % 360f
        val aligned = hasSensor && (relative < 5f || relative > 355f)

        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
            // rotating dial (N/E/S/W)
            Box(Modifier.fillMaxSize().rotate(-heading)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Gold.copy(alpha = 0.15f))
                    drawCircle(Gold, style = Stroke(width = 4f))
                    for (i in 0 until 72) {
                        val a = Math.toRadians(i * 5.0)
                        val long = i % 6 == 0
                        val r = size.minDimension / 2
                        val inner = r * if (long) 0.88f else 0.93f
                        val dx = Math.sin(a).toFloat()
                        val dy = -Math.cos(a).toFloat()
                        drawLine(
                            Gold.copy(alpha = if (long) 0.9f else 0.5f),
                            Offset(center.x + dx * inner, center.y + dy * inner),
                            Offset(center.x + dx * r * 0.98f, center.y + dy * r * 0.98f),
                            strokeWidth = if (long) 4f else 2f
                        )
                    }
                }
                Text("N", color = Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
                Text("S", color = Ivory, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
                Text("E", color = Ivory, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
                Text("W", color = Ivory, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp))
            }
            // Qibla pointer
            Box(Modifier.fillMaxSize().rotate(if (hasSensor) relative else bearing)) {
                Icon(
                    Icons.Filled.Navigation, "Qibla direction",
                    tint = if (aligned) Emerald.copy(red = 0.4f, green = 1f, blue = 0.5f) else Gold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp).size(56.dp)
                )
            }
            Box(
                Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(4.dp),
                contentAlignment = Alignment.Center
            ) { Text("🕋", fontSize = 30.sp) }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Qibla angle", color = Ivory.copy(alpha = 0.8f))
            Text(
                "${"%.0f".format(bearing)}°",
                color = Emerald.copy(red = 0.5f, green = 1f, blue = 0.5f),
                fontSize = 42.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
            )
            Text("Distance to Kaaba ≈ ${"%,.0f".format(distance)} km", color = Ivory.copy(alpha = 0.8f))
            Text(
                when {
                    !hasSensor -> "No compass sensor on this device. Face ${"%.0f".format(bearing)}° from true north using a separate compass."
                    aligned -> "You are facing the Qibla"
                    else -> "Turn your phone until the arrow points up"
                },
                color = if (aligned) Emerald else GoldSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Hold the phone flat and away from magnets or metal. Wave it in a figure-8 if the arrow drifts.",
                color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Hijri calendar
// ---------------------------------------------------------------------------------------

@Composable
fun HijriCalendarScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val zone = PrayerCalculator.zoneOf(s)
    val today = Instant.now().atZone(zone).toLocalDate()
    val todayHijri = HijriUtil.toHijri(today)

    var year by remember { mutableStateOf(todayHijri.year) }
    var month by remember { mutableStateOf(todayHijri.month) }

    fun shift(delta: Int) {
        var m = month + delta
        var y = year
        if (m > 12) { m = 1; y++ }
        if (m < 1) { m = 12; y-- }
        month = m; year = y
    }

    ScreenScaffold("Hijri calendar") {
        GoldCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shift(-1) }) { Text("‹", color = Gold, fontSize = 28.sp) }
                Text(
                    "${HijriUtil.monthNames[month - 1]} $year",
                    color = Ivory, style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { shift(1) }) { Text("›", color = Gold, fontSize = 28.sp) }
            }

            val first = HijriUtil.firstOfMonth(year, month)
            val length = HijriUtil.daysInMonth(year, month)
            val offset = first.dayOfWeek.value % 7 // Sunday = 0
            val cells: List<Int?> = List(offset) { null } + (1..length).toList()
            val weeks = cells.chunked(7)

            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                    Text(it, color = Gold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        val isToday = day != null && year == todayHijri.year && month == todayHijri.month && day == todayHijri.day
                        Box(
                            Modifier.weight(1f).height(48.dp).padding(2.dp)
                                .background(
                                    if (isToday) Emerald else Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val g: LocalDate = first.plusDays((day - 1).toLong())
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$day", color = Ivory,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        "${g.dayOfMonth}", color = Ivory.copy(alpha = 0.5f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Small numbers are the Gregorian day of the month. Dates follow the Umm al-Qura calendar and " +
                    "may differ by a day from local moon sighting.",
                color = Ivory.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        GoldCard {
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                color = Ivory, style = MaterialTheme.typography.titleMedium
            )
            Text(todayHijri.toString(), color = GoldSoft)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Ramadan, Sehri & Iftar
// ---------------------------------------------------------------------------------------

@Composable
fun RamadanScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val zone = PrayerCalculator.zoneOf(s)
    val today = Instant.now().atZone(zone).toLocalDate()

    ScreenScaffold("Ramadan", onBack) {
        val inRamadan = HijriUtil.isRamadan(today)
        val start = HijriUtil.nextRamadanStart(today)
        val daysLeft = HijriUtil.daysUntilRamadan(today)
        val hijri = HijriUtil.toHijri(today)

        MosqueSilhouette(Modifier.fillMaxWidth().height(110.dp))
        GoldCard(highlight = true) {
            if (inRamadan) {
                Text("Ramadan Mubarak", color = Gold, style = MaterialTheme.typography.headlineSmall)
                Text("Day ${hijri.day} of Ramadan ${hijri.year}", color = Ivory, style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Ramadan countdown", color = GoldSoft)
                Text(
                    if (daysLeft == 0L) "Starts today" else "$daysLeft days left",
                    color = Gold, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif
                )
                Text(
                    "1 Ramadan ${HijriUtil.toHijri(start).year} ≈ " +
                        start.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
                    color = Ivory.copy(alpha = 0.85f)
                )
            }
        }

        SectionLabel("Sehri & Iftar")
        if (!s.locationSet) {
            Text("Set your location to see Sehri and Iftar times.", color = Ivory)
            return@ScreenScaffold
        }
        GoldCard {
            Text(
                "Sehri ends at Fajr; Iftar is at Maghrib, using your calculation settings.",
                color = Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(Modifier.fillMaxWidth()) {
                Text("Date", color = Gold, modifier = Modifier.weight(1.2f))
                Text("Sehri ends", color = Gold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("Iftar", color = Gold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            for (i in 0L until 7L) {
                val d = today.plusDays(i)
                val day = PrayerCalculator.calculate(s, d)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (i == 0L) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth,
                        color = Ivory, modifier = Modifier.weight(1.2f),
                        fontWeight = if (i == 0L) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(Fmt.clock(day.of(Prayer.FAJR), zone, is24), color = Ivory, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(Fmt.clock(day.of(Prayer.MAGHRIB), zone, is24), color = Ivory, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}
