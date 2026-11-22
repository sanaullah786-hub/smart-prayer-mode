package com.sanaullah.smartprayermode.domain

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/** Hijri (Umm al-Qura) helpers built on java.time. */
object HijriUtil {
    val monthNames = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Ula", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    const val RAMADAN = 9

    data class HijriParts(val year: Int, val month: Int, val day: Int) {
        val monthName: String get() = monthNames[month - 1]
        override fun toString() = "$day $monthName $year"
    }

    fun toHijri(date: LocalDate): HijriParts {
        val h = HijrahDate.from(date)
        return HijriParts(
            h.get(ChronoField.YEAR),
            h.get(ChronoField.MONTH_OF_YEAR),
            h.get(ChronoField.DAY_OF_MONTH)
        )
    }

    fun firstOfMonth(year: Int, month: Int): LocalDate =
        LocalDate.from(HijrahDate.of(year, month, 1))

    fun daysInMonth(year: Int, month: Int): Int =
        HijrahDate.of(year, month, 1).lengthOfMonth()

    /** Next 1 Ramadan on or after [today] (today counts when it is 1 Ramadan). */
    fun nextRamadanStart(today: LocalDate): LocalDate {
        val year = toHijri(today).year
        val thisYear = firstOfMonth(year, RAMADAN)
        return if (!thisYear.isBefore(today)) thisYear else firstOfMonth(year + 1, RAMADAN)
    }

    fun isRamadan(today: LocalDate): Boolean = toHijri(today).month == RAMADAN

    fun daysUntilRamadan(today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextRamadanStart(today))
}
