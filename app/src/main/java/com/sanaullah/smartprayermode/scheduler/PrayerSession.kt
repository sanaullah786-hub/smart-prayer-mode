package com.sanaullah.smartprayermode.scheduler

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sanaullah.smartprayermode.SmartPrayerApp
import com.sanaullah.smartprayermode.admin.DeviceOwnerHelper
import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.service.PrayerNotifications
import com.sanaullah.smartprayermode.service.PrayerService
import com.sanaullah.smartprayermode.ui.prayermode.PrayerModeActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The active Prayer Mode session. It is persisted in SharedPreferences so it survives a
 * process death or reboot, and always carries an absolute end time: Prayer Mode can never
 * outlive [Active.endMillis].
 */
object PrayerSession {
    data class Active(val prayerName: String, val startMillis: Long, val endMillis: Long)

    private const val PREFS = "prayer_session"
    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reloads the persisted session; expired sessions are discarded. */
    fun load(ctx: Context): Active? {
        val p = prefs(ctx)
        val end = p.getLong("end", 0L)
        val result = if (end > System.currentTimeMillis()) {
            Active(p.getString("name", "Prayer") ?: "Prayer", p.getLong("start", 0L), end)
        } else {
            if (end != 0L) p.edit().clear().apply()
            null
        }
        _active.value = result
        return result
    }

    fun set(ctx: Context, a: Active) {
        prefs(ctx).edit()
            .putString("name", a.prayerName).putLong("start", a.startMillis).putLong("end", a.endMillis)
            .apply()
        _active.value = a
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove("name").remove("start").remove("end").apply()
        _active.value = null
    }

    fun saveDnd(ctx: Context, filter: Int) = prefs(ctx).edit().putInt("dnd_prev", filter).apply()
    fun takeSavedDnd(ctx: Context): Int {
        val p = prefs(ctx)
        val v = p.getInt("dnd_prev", -1)
        p.edit().remove("dnd_prev").apply()
        return v
    }
}

/** Starts, resumes and ends Prayer Mode. Safe to call repeatedly. */
object PrayerSessionManager {
    private const val TAG = "PrayerSessionManager"

    fun begin(ctx: Context, s: SettingsEntity, prayerName: String, displayName: String, durationMs: Long) {
        val app = ctx.applicationContext
        if (PrayerSession.active.value != null) end(app)

        val now = System.currentTimeMillis()
        val session = PrayerSession.Active(prayerName, now, now + durationMs)
        PrayerSession.set(app, session)
        PrayerScheduler.scheduleEnd(app, session.endMillis)

        if (s.dndDuringPrayer) enableDnd(app)
        DeviceOwnerHelper.applyLockTaskPolicy(app)
        PrayerNotifications.postPrayerTime(app, displayName, auto = true, endMillis = session.endMillis)
        launchActivity(app)
    }

    /** Re-establishes an unexpired session after a reboot or crash. */
    fun resume(ctx: Context) {
        val app = ctx.applicationContext
        val a = PrayerSession.load(app)
        if (a == null) {
            restoreDnd(app)
            return
        }
        PrayerScheduler.scheduleEnd(app, a.endMillis)
        DeviceOwnerHelper.applyLockTaskPolicy(app)
        PrayerNotifications.postPrayerTime(app, a.prayerName, auto = true, endMillis = a.endMillis)
        launchActivity(app)
    }

    fun end(ctx: Context) {
        val app = ctx.applicationContext
        (app as? SmartPrayerApp)?.azanPlayer?.stop()
        PrayerScheduler.cancelEnd(app)
        PrayerSession.clear(app)
        restoreDnd(app)
        PrayerNotifications.cancelPrayerTime(app)
        runCatching { app.stopService(Intent(app, PrayerService::class.java)) }
    }

    fun launchActivity(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(ctx, PrayerModeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            // Blocked background start: the full-screen-intent notification is the fallback.
            Log.w(TAG, "Direct activity start blocked: ${e.message}")
        }
    }

    private fun enableDnd(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        PrayerSession.saveDnd(ctx, nm.currentInterruptionFilter)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun restoreDnd(ctx: Context) {
        val previous = PrayerSession.takeSavedDnd(ctx)
        if (previous < 0) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(previous)
    }
}
