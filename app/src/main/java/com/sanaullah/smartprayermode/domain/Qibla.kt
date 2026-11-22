package com.sanaullah.smartprayermode.domain

import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaUtil {
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    /** Initial great-circle bearing to the Kaaba, degrees clockwise from true north. */
    fun bearing(lat: Double, lon: Double): Double =
        (Qibla(Coordinates(lat, lon)).direction + 360.0) % 360.0

    fun distanceKm(lat: Double, lon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(KAABA_LAT - lat)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(KAABA_LAT)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
