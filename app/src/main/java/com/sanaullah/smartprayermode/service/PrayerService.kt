package com.sanaullah.smartprayermode.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.ServiceCompat
import com.sanaullah.smartprayermode.SmartPrayerApp
import com.sanaullah.smartprayermode.audio.AzanSource
import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.data.SettingsRepository
import com.sanaullah.smartprayermode.domain.Prayer
import com.sanaullah.smartprayermode.scheduler.PrayerSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service started by the prayer alarm. It exists only to keep the
 * Azan playing while the screen is off; it stops itself as soon as the audio finishes.
 * Prayer Mode itself is driven by [PrayerSessionManager] and its absolute end alarm, so no
 * service runs for the duration of the prayer.
 */
class PrayerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as SmartPrayerApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP_AUDIO -> {
                app.azanPlayer.stop()
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val rawName = intent.getStringExtra(EXTRA_PRAYER) ?: "FAJR"
        val prayer = Prayer.fromName(rawName)
        val displayName = prayer?.label ?: rawName
        val durationOverride = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val playAzan = intent.getBooleanExtra(EXTRA_PLAY_AZAN, true)

        // Must be called promptly after startForegroundService().
        ServiceCompat.startForeground(
            this, PrayerNotifications.ID_AZAN,
            PrayerNotifications.azanPlaying(this, displayName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        scope.launch {
            val s = try {
                SettingsRepository.get(applicationContext).current()
            } catch (e: Exception) {
                Log.e(TAG, "Could not read settings", e)
                SettingsEntity.defaults()
            }

            if (s.vibration) vibrate()

            if (s.autoPrayerMode) {
                val minutes = s.prayerDurationMinutes
                    .coerceIn(SettingsEntity.MIN_DURATION, SettingsEntity.MAX_DURATION)
                val duration = if (durationOverride > 0) durationOverride else minutes * 60_000L
                PrayerSessionManager.begin(applicationContext, s, rawName, displayName, duration)
            } else {
                PrayerNotifications.postPrayerTime(applicationContext, displayName, auto = false)
            }

            if (s.azanEnabled && playAzan) {
                app.azanPlayer.play(
                    AzanSource.fromName(s.azanSource), s.customAzanUri, s.azanVolume,
                    useFallbackTone = true
                ) { stopSelf() }
            } else {
                stopSelf()
            }
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (v.hasVibrator()) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PrayerService"
        const val ACTION_START = "com.sanaullah.smartprayermode.action.START"
        const val ACTION_STOP_AUDIO = "com.sanaullah.smartprayermode.action.STOP_AUDIO"
        const val EXTRA_PRAYER = "extra_prayer"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_PLAY_AZAN = "extra_play_azan"

        /** Starts a short Prayer Mode session without waiting for a real prayer time. */
        fun startTest(ctx: Context, seconds: Int = 20) {
            androidx.core.content.ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, PrayerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PRAYER, "Test Prayer")
                    .putExtra(EXTRA_DURATION_MS, seconds * 1000L)
                    .putExtra(EXTRA_PLAY_AZAN, false)
            )
        }
    }
}
