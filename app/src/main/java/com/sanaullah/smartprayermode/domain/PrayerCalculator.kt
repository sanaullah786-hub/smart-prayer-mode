package com.sanaullah.smartprayermode.domain

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerAdjustments
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.sanaullah.smartprayermode.data.SettingsEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Prayer(val label: String, val obligatory: Boolean) {
    FAJR("Fajr", true),
    SUNRISE("Sunrise", false),
    DHUHR("Dhuhr", true),
    ASR("Asr", true),
    MAGHRIB("Maghrib", true),
    ISHA("Isha", true);

    companion object {
        fun fromName(name: String?): Prayer? = entries.firstOrNull { it.name == name }
    }
}

data class PrayerTime(val prayer: Prayer, val time: Instant)

data class PrayerDay(val date: LocalDate, val times: List<PrayerTime>) {
    fun of(prayer: Prayer): Instant = times.first { it.prayer == prayer }.time
}

data class MethodInfo(val id: String, val label: String)

object PrayerCalculator {

    val methods: List<MethodInfo> = listOf(
        MethodInfo("MUSLIM_WORLD_LEAGUE", "Muslim World League"),
        MethodInfo("EGYPTIAN", "Egyptian General Authority of Survey"),
        MethodInfo("KARACHI", "University of Islamic Sciences, Karachi"),
        MethodInfo("UMM_AL_QURA", "Umm al-Qura University, Makkah"),
        MethodInfo("DUBAI", "Dubai"),
        MethodInfo("MOON_SIGHTING_COMMITTEE", "Moonsighting Committee"),
        MethodInfo("NORTH_AMERICA", "ISNA (North America)"),
        MethodInfo("KUWAIT", "Kuwait"),
        MethodInfo("QATAR", "Qatar"),
        MethodInfo("SINGAPORE", "Singapore"),
    )

    /** Adhan already rounds to the minute but leaves a jittering millisecond; drop it so times are stable. */
    private fun minute(date: java.util.Date): Instant = date.toInstant().truncatedTo(ChronoUnit.MINUTES)

    fun methodLabel(id: String): String = methods.firstOrNull { it.id == id }?.label ?: id

    fun zoneOf(s: SettingsEntity): ZoneId = try {
        if (s.timezoneId.isBlank()) ZoneId.systemDefault() else ZoneId.of(s.timezoneId)
    } catch (e: Exception) {
        ZoneId.systemDefault()
    }

    /** Calculates all six times for [date] (the calendar date at the user's location). */
    fun calculate(s: SettingsEntity, date: LocalDate): PrayerDay {
        val params = (try {
            CalculationMethod.valueOf(s.calculationMethod)
        } catch (e: IllegalArgumentException) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE
        }).parameters
        params.madhab = if (s.madhab == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
        params.adjustments = PrayerAdjustments(
            s.adjFajr, s.adjSunrise, s.adjDhuhr, s.adjAsr, s.adjMaghrib, s.adjIsha
        )
        val times = PrayerTimes(
            Coordinates(s.latitude, s.longitude),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params
        )
        return PrayerDay(
            date,
            listOf(
                PrayerTime(Prayer.FAJR, minute(times.fajr)),
                PrayerTime(Prayer.SUNRISE, minute(times.sunrise)),
                PrayerTime(Prayer.DHUHR, minute(times.dhuhr)),
                PrayerTime(Prayer.ASR, minute(times.asr)),
                PrayerTime(Prayer.MAGHRIB, minute(times.maghrib)),
                PrayerTime(Prayer.ISHA, minute(times.isha)),
            )
        )
    }

    /** The next obligatory prayer strictly after [after]; looks up to three days ahead. */
    fun nextObligatory(s: SettingsEntity, after: Instant): PrayerTime? {
        val start = after.atZone(zoneOf(s)).toLocalDate()
        for (offset in 0L..2L) {
            val day = calculate(s, start.plusDays(offset))
            day.times.firstOrNull { it.prayer.obligatory && it.time.isAfter(after) }?.let { return it }
        }
        return null
    }
}
