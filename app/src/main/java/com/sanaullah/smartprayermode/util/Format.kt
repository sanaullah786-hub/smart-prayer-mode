package com.sanaullah.smartprayermode.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    /** 00:12:05 style countdown; negative values clamp to zero. */
    fun hms(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000 // round up so 0 shows only at the end
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return "%02d:%02d:%02d".format(Locale.US, h, m, s)
    }

    /** mm:ss countdown (used on the Prayer Mode screen). */
    fun ms(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        return "%02d:%02d".format(Locale.US, total / 60, total % 60)
    }

    fun clock(instant: Instant, zone: ZoneId, is24: Boolean, seconds: Boolean = false): String {
        val pattern = when {
            is24 && seconds -> "HH:mm:ss"
            is24 -> "HH:mm"
            seconds -> "h:mm:ss a"
            else -> "h:mm a"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(instant.atZone(zone))
    }

    fun mmss(positionMs: Int): String {
        val total = positionMs / 1000
        return "%d:%02d".format(Locale.US, total / 60, total % 60)
    }
}
