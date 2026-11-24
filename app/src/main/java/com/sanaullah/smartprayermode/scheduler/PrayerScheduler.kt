package com.sanaullah.smartprayermode.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.sanaullah.smartprayermode.MainActivity
import com.sanaullah.smartprayermode.data.SettingsRepository
import com.sanaullah.smartprayermode.data.SettingsEntity
import com.sanaullah.smartprayermode.domain.Prayer
import com.sanaullah.smartprayermode.domain.PrayerCalculator
import com.sanaullah.smartprayermode.domain.PrayerTime
import com.sanaullah.smartprayermode.service.PrayerNotifications
import com.sanaullah.smartprayermode.service.PrayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Only ONE prayer alarm and ONE end alarm ever exist. Each uses a fixed request code with
 * FLAG_UPDATE_CURRENT, so rescheduling replaces rather than duplicates. After a prayer fires,
 * the receiver schedules the following one.
 */
object PrayerScheduler {
    const val ACTION_PRAYER = "com.sanaullah.smartprayermode.action.PRAYER_TIME"
    const val ACTION_END = "com.sanaullah.smartprayermode.action.PRAYER_END"
    const val ACTION_REMINDER = "com.sanaullah.smartprayermode.action.PRAYER_REMINDER"
    const val EXTRA_PRAYER = "extra_prayer"
    const val EXTRA_TIME = "extra_time"

    private const val RC_PRAYER = 1001
    private const val RC_END = 1002
    private const val RC_SHOW = 1003
    private const val RC_REMIND = 1004
    private const val TAG = "PrayerScheduler"

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager(ctx).canScheduleExactAlarms()

    private fun receiverIntent(ctx: Context, action: String) =
        Intent(ctx, PrayerAlarmReceiver::class.java).setAction(action)

    private fun pending(ctx: Context, rc: Int, intent: Intent, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) =
        PendingIntent.getBroadcast(ctx, rc, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    /** Schedules the next obligatory prayer after [after]. Returns it, or null if no location. */
    suspend fun scheduleNext(ctx: Context, after: Instant = Instant.now()): PrayerTime? {
        val app = ctx.applicationContext
        val s = SettingsRepository.get(app).current()
        if (!s.locationSet) {
            cancelPrayer(app)
            return null
        }
        val next = PrayerCalculator.nextObligatory(s, after) ?: return null
        val intent = receiverIntent(app, ACTION_PRAYER)
            .putExtra(EXTRA_PRAYER, next.prayer.name)
            .putExtra(EXTRA_TIME, next.time.toEpochMilli())
        setAlarm(app, next.time.toEpochMilli(), pending(app, RC_PRAYER, intent))
        scheduleReminder(app, s, next)
        return next
    }

    /** One reminder alarm for the next prayer, N minutes before it (replaced, never duplicated). */
    private fun scheduleReminder(ctx: Context, s: SettingsEntity, next: PrayerTime) {
        val intent = receiverIntent(ctx, ACTION_REMINDER)
            .putExtra(EXTRA_PRAYER, next.prayer.name)
            .putExtra(EXTRA_TIME, next.time.toEpochMilli())
        val pi = pending(ctx, RC_REMIND, intent)
        val at = next.time.toEpochMilli() - s.reminderMinutes * 60_000L
        if (!s.notifEnabled || s.reminderMinutes <= 0 || !s.notifFor(next.prayer) ||
            at <= System.currentTimeMillis()
        ) {
            alarmManager(ctx).cancel(pi)
            return
        }
        setAlarm(ctx, at, pi)
    }

    fun scheduleEnd(ctx: Context, endMillis: Long) {
        val app = ctx.applicationContext
        setAlarm(app, endMillis, pending(app, RC_END, receiverIntent(app, ACTION_END)))
    }

    fun cancelEnd(ctx: Context) {
        val app = ctx.applicationContext
        alarmManager(app).cancel(pending(app, RC_END, receiverIntent(app, ACTION_END)))
    }

    private fun cancelPrayer(ctx: Context) {
        alarmManager(ctx).cancel(pending(ctx, RC_PRAYER, receiverIntent(ctx, ACTION_PRAYER)))
    }

    private fun setAlarm(ctx: Context, triggerAt: Long, pi: PendingIntent) {
        val am = alarmManager(ctx)
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // No exact-alarm permission: setAlarmClock is exact and needs no permission.
                val show = PendingIntent.getActivity(
                    ctx, RC_SHOW, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using inexact alarm", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}

/** Runs [block] off the main thread while keeping the receiver alive. */
fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("PrayerReceiver", "Async work failed", e)
        } finally {
            pending.finish()
        }
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PrayerScheduler.ACTION_PRAYER -> {
                val prayer = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER) ?: "FAJR"
                val time = intent.getLongExtra(PrayerScheduler.EXTRA_TIME, System.currentTimeMillis())
                val svc = Intent(context, PrayerService::class.java)
                    .setAction(PrayerService.ACTION_START)
                    .putExtra(PrayerService.EXTRA_PRAYER, prayer)
                try {
                    ContextCompat.startForegroundService(context, svc)
                } catch (e: Exception) {
                    Log.e("PrayerReceiver", "Could not start PrayerService", e)
                }
                // Schedule the following prayer; never earlier than just after this one.
                val after = Instant.ofEpochMilli(maxOf(System.currentTimeMillis(), time)).plusSeconds(1)
                runAsync { PrayerScheduler.scheduleNext(context, after) }
            }
            PrayerScheduler.ACTION_END -> PrayerSessionManager.end(context)
            PrayerScheduler.ACTION_REMINDER -> runAsync {
                val s = SettingsRepository.get(context).current()
                val prayer = Prayer.fromName(intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER))
                    ?: return@runAsync
                if (!s.notifEnabled || !s.notifFor(prayer)) return@runAsync
                val time = intent.getLongExtra(PrayerScheduler.EXTRA_TIME, 0L)
                if (time > System.currentTimeMillis()) {
                    PrayerNotifications.postReminder(context, prayer.label, time, s.notifOnLockScreen)
                }
            }
        }
    }
}

/** Restores schedules and any unexpired Prayer Mode session after reboot, update or time changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> runAsync {
                PrayerScheduler.scheduleNext(context)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
                ) {
                    withContext(Dispatchers.Main) { PrayerSessionManager.resume(context) }
                }
            }
        }
    }
}
