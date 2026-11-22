package com.sanaullah.smartprayermode.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class City(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val timezone: String,
)

/** Offline catalogue for manual city selection. */
object CityCatalog {
    val cities: List<City> = listOf(
        City("Makkah", "Saudi Arabia", 21.4225, 39.8262, "Asia/Riyadh"),
        City("Madinah", "Saudi Arabia", 24.4686, 39.6142, "Asia/Riyadh"),
        City("Riyadh", "Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        City("Jeddah", "Saudi Arabia", 21.4858, 39.1925, "Asia/Riyadh"),
        City("Karachi", "Pakistan", 24.8607, 67.0011, "Asia/Karachi"),
        City("Lahore", "Pakistan", 31.5204, 74.3587, "Asia/Karachi"),
        City("Islamabad", "Pakistan", 33.6844, 73.0479, "Asia/Karachi"),
        City("Peshawar", "Pakistan", 34.0151, 71.5249, "Asia/Karachi"),
        City("Quetta", "Pakistan", 30.1798, 66.9750, "Asia/Karachi"),
        City("Hyderabad", "Pakistan", 25.3960, 68.3578, "Asia/Karachi"),
        City("Multan", "Pakistan", 30.1575, 71.5249, "Asia/Karachi"),
        City("Faisalabad", "Pakistan", 31.4504, 73.1350, "Asia/Karachi"),
        City("Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
        City("Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
        City("Dhaka", "Bangladesh", 23.8103, 90.4125, "Asia/Dhaka"),
        City("Kabul", "Afghanistan", 34.5553, 69.2075, "Asia/Kabul"),
        City("Tehran", "Iran", 35.6892, 51.3890, "Asia/Tehran"),
        City("Baghdad", "Iraq", 33.3152, 44.3661, "Asia/Baghdad"),
        City("Dubai", "United Arab Emirates", 25.2048, 55.2708, "Asia/Dubai"),
        City("Abu Dhabi", "United Arab Emirates", 24.4539, 54.3773, "Asia/Dubai"),
        City("Doha", "Qatar", 25.2854, 51.5310, "Asia/Qatar"),
        City("Kuwait City", "Kuwait", 29.3759, 47.9774, "Asia/Kuwait"),
        City("Muscat", "Oman", 23.5880, 58.3829, "Asia/Muscat"),
        City("Manama", "Bahrain", 26.2285, 50.5860, "Asia/Bahrain"),
        City("Amman", "Jordan", 31.9454, 35.9284, "Asia/Amman"),
        City("Jerusalem", "Palestine", 31.7683, 35.2137, "Asia/Jerusalem"),
        City("Damascus", "Syria", 33.5138, 36.2765, "Asia/Damascus"),
        City("Beirut", "Lebanon", 33.8938, 35.5018, "Asia/Beirut"),
        City("Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        City("Alexandria", "Egypt", 31.2001, 29.9187, "Africa/Cairo"),
        City("Istanbul", "Türkiye", 41.0082, 28.9784, "Europe/Istanbul"),
        City("Ankara", "Türkiye", 39.9334, 32.8597, "Europe/Istanbul"),
        City("Baku", "Azerbaijan", 40.4093, 49.8671, "Asia/Baku"),
        City("Tashkent", "Uzbekistan", 41.2995, 69.2401, "Asia/Tashkent"),
        City("Jakarta", "Indonesia", -6.2088, 106.8456, "Asia/Jakarta"),
        City("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        City("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        City("Casablanca", "Morocco", 33.5731, -7.5898, "Africa/Casablanca"),
        City("Algiers", "Algeria", 36.7538, 3.0588, "Africa/Algiers"),
        City("Tunis", "Tunisia", 36.8065, 10.1815, "Africa/Tunis"),
        City("Khartoum", "Sudan", 15.5007, 32.5599, "Africa/Khartoum"),
        City("Lagos", "Nigeria", 6.5244, 3.3792, "Africa/Lagos"),
        City("Nairobi", "Kenya", -1.2921, 36.8219, "Africa/Nairobi"),
        City("Johannesburg", "South Africa", -26.2041, 28.0473, "Africa/Johannesburg"),
        City("London", "United Kingdom", 51.5074, -0.1278, "Europe/London"),
        City("Birmingham", "United Kingdom", 52.4862, -1.8904, "Europe/London"),
        City("Paris", "France", 48.8566, 2.3522, "Europe/Paris"),
        City("Berlin", "Germany", 52.5200, 13.4050, "Europe/Berlin"),
        City("Moscow", "Russia", 55.7558, 37.6173, "Europe/Moscow"),
        City("New York", "United States", 40.7128, -74.0060, "America/New_York"),
        City("Chicago", "United States", 41.8781, -87.6298, "America/Chicago"),
        City("Los Angeles", "United States", 34.0522, -118.2437, "America/Los_Angeles"),
        City("Toronto", "Canada", 43.6532, -79.3832, "America/Toronto"),
        City("Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney"),
    ).sortedBy { it.name }

    fun search(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return cities
        return cities.filter {
            it.name.contains(q, ignoreCase = true) || it.country.contains(q, ignoreCase = true)
        }
    }
}

data class Place(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val country: String,
)

/**
 * One-shot location lookups only; nothing here keeps a location listener alive
 * after a fix (or timeout), so the app never tracks the user continuously.
 */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns a fresh fix (GPS first, then network, then last known) or null. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        if (hasFine() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            withTimeoutOrNull(15_000) { singleFix(lm, LocationManager.GPS_PROVIDER) }?.let { return it }
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            withTimeoutOrNull(10_000) { singleFix(lm, LocationManager.NETWORK_PROVIDER) }?.let { return it }
        }
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleFix(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    /** Reverse geocodes to city + country; falls back to blank names when offline. */
    suspend fun reverseGeocode(lat: Double, lon: Double): Place {
        val addr = runCatching { lookup(lat, lon) }.getOrNull()
        val city = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea ?: ""
        return Place(lat, lon, city, addr?.countryName ?: "")
    }

    private suspend fun lookup(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
        }
    }
}
