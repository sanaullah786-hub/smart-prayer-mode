package com.sanaullah.smartprayermode.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanaullah.smartprayermode.SmartPrayerApp
import com.sanaullah.smartprayermode.audio.AzanSource
import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.data.SettingsRepository
import com.sanaullah.smartprayermode.location.City
import com.sanaullah.smartprayermode.location.LocationHelper
import com.sanaullah.smartprayermode.scheduler.PrayerScheduler
import com.sanaullah.smartprayermode.service.PrayerNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LocationUi {
    data object Idle : LocationUi
    data object Loading : LocationUi
    data class Error(val message: String) : LocationUi
    data class Success(val label: String) : LocationUi
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository.get(app)
    private val locationHelper = LocationHelper(app)
    private val azanPlayer get() = (app as SmartPrayerApp).azanPlayer

    /** null until the database has been read once. */
    val settings: StateFlow<SettingsEntity?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val azanState = (app as SmartPrayerApp).azanPlayer.ui

    private val _location = MutableStateFlow<LocationUi>(LocationUi.Idle)
    val location: StateFlow<LocationUi> = _location

    init {
        // Use location only when necessary: refresh once on launch if the last fix is stale.
        viewModelScope.launch {
            val s = repo.current()
            val stale = System.currentTimeMillis() - s.locationUpdatedAt > 12 * 3_600_000L
            if (s.autoLocation && s.locationIsAuto && stale && locationHelper.hasPermission()) {
                refreshLocation()
            }
            PrayerScheduler.scheduleNext(app)
        }
    }

    /** Applies a settings change, persists it and re-arms the prayer alarm. */
    fun update(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch {
            repo.update(transform)
            PrayerScheduler.scheduleNext(app)
        }
    }

    fun completeOnboarding() = update { it.copy(onboardingDone = true) }

    fun resetToDefaults() = update {
        SettingsEntity.defaults().copy(
            onboardingDone = true,
            locationSet = it.locationSet, latitude = it.latitude, longitude = it.longitude,
            cityName = it.cityName, countryName = it.countryName, timezoneId = it.timezoneId,
            locationIsAuto = it.locationIsAuto, locationUpdatedAt = it.locationUpdatedAt,
        )
    }

    // ---- Location ----------------------------------------------------------------------

    fun hasLocationPermission() = locationHelper.hasPermission()

    fun permissionDenied() {
        _location.value = LocationUi.Error(
            "Location permission was denied. You can choose your city manually instead."
        )
    }

    fun refreshLocation() {
        viewModelScope.launch {
            if (!locationHelper.hasPermission()) {
                permissionDenied()
                return@launch
            }
            _location.value = LocationUi.Loading
            val fix = locationHelper.currentLocation()
            if (fix == null) {
                _location.value = LocationUi.Error(
                    "Could not get a location fix. Turn on GPS / location services, or choose a city manually."
                )
                return@launch
            }
            val place = locationHelper.reverseGeocode(fix.latitude, fix.longitude)
            val updated = repo.update {
                it.copy(
                    locationSet = true, latitude = place.latitude, longitude = place.longitude,
                    cityName = place.city, countryName = place.country,
                    timezoneId = "", // follow the device time zone
                    locationIsAuto = true, locationUpdatedAt = System.currentTimeMillis(),
                )
            }
            PrayerScheduler.scheduleNext(app)
            _location.value = LocationUi.Success(updated.locationLabel)
        }
    }

    fun selectCity(city: City) {
        update {
            it.copy(
                locationSet = true, latitude = city.lat, longitude = city.lon,
                cityName = city.name, countryName = city.country, timezoneId = city.timezone,
                locationIsAuto = false, locationUpdatedAt = System.currentTimeMillis(),
            )
        }
        _location.value = LocationUi.Success("${city.name}, ${city.country}")
    }

    // ---- Azan audio -------------------------------------------------------------------

    fun previewAzan(source: AzanSource) {
        val s = settings.value ?: return
        azanPlayer.play(source, s.azanUri(source.name), s.azanVolume)
    }

    fun playSelectedAzan() {
        val s = settings.value ?: return
        val src = AzanSource.fromName(s.azanSource)
        azanPlayer.play(src, s.azanUri(src.name), s.azanVolume)
    }

    fun togglePause() = azanPlayer.togglePause()
    fun stopAzan() = azanPlayer.stop()
    fun setLiveVolume(v: Float) = azanPlayer.setVolume(v)

    /** Stores (or clears, when [uri] is null) the user-supplied audio file for an Azan slot. */
    fun setAzanFile(source: AzanSource, uri: Uri?, select: Boolean = true) {
        val value = uri?.toString()
        update {
            var n = when (source) {
                AzanSource.MADINAH -> it.copy(userUriMadinah = value)
                AzanSource.MAKKAH -> it.copy(userUriMakkah = value)
                AzanSource.EGYPTIAN -> it.copy(userUriEgyptian = value)
                AzanSource.TURKISH -> it.copy(userUriTurkish = value)
                AzanSource.CUSTOM -> it.copy(customAzanUri = value)
            }
            if (select && value != null) n = n.copy(azanSource = source.name)
            n
        }
    }

    // ---- Notifications ----------------------------------------------------------------

    fun sendTestNotifications() {
        val s = settings.value ?: return
        PrayerNotifications.postReminder(app, "Dhuhr", System.currentTimeMillis() + 10 * 60_000L, s.notifOnLockScreen)
        PrayerNotifications.postPrayerTime(app, "Dhuhr", auto = false, lockScreen = s.notifOnLockScreen)
    }

    override fun onCleared() {
        azanPlayer.stop() // release audio resources when the app UI goes away
        super.onCleared()
    }
}

/** Snapshot of every permission/exemption the app cares about. */
data class PermissionStatus(
    val location: Boolean,
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val overlay: Boolean,
    val fullScreen: Boolean,
    val batteryUnrestricted: Boolean,
    val dndAccess: Boolean,
) {
    companion object {
        fun read(ctx: Context): PermissionStatus {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val pm = ctx.getSystemService(PowerManager::class.java)
            fun granted(p: String) =
                ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            return PermissionStatus(
                location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    granted(Manifest.permission.ACCESS_COARSE_LOCATION),
                notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                exactAlarm = PrayerScheduler.canScheduleExact(ctx),
                overlay = Settings.canDrawOverlays(ctx),
                fullScreen = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
                batteryUnrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName),
                dndAccess = nm.isNotificationPolicyAccessGranted,
            )
        }
    }
}

/** Opens a system settings page for this app; silently ignores devices that lack it. */
fun openSystemSettings(ctx: Context, action: String, withPackage: Boolean = true) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (withPackage) intent.data = Uri.parse("package:${ctx.packageName}")
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
