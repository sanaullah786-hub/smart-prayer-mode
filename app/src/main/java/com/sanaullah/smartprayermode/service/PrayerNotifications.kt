package com.sanaullah.smartprayermode.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sanaullah.smartprayermode.MainActivity
import com.sanaullah.smartprayermode.R
import com.sanaullah.smartprayermode.ui.prayermode.PrayerModeActivity
import java.text.DateFormat
import java.util.Date

object PrayerNotifications {
    const val CH_PRAYER = "prayer_alerts"
    const val CH_REMINDER = "prayer_reminders"
    const val CH_AZAN = "azan_playback"
    const val ID_AZAN = 1
    const val ID_PRAYER = 2
    const val ID_REMINDER = 3

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PRAYER, "Prayer times", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown when a prayer time starts and while Prayer Mode is active"
                setSound(null, null) // the Azan is played by the app itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Prayer reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A heads-up shortly before each prayer time (sound and vibration can be changed in system settings)"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_AZAN, "Azan playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown only while the Azan audio is playing"
                setSound(null, null)
            }
        )
    }

    private fun activity(ctx: Context, cls: Class<*>, rc: Int) = PendingIntent.getActivity(
        ctx, rc, Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** With the lock-screen option off, the lock screen shows only a generic line. */
    private fun applyVisibility(ctx: Context, b: NotificationCompat.Builder, lockScreen: Boolean, channel: String) {
        if (lockScreen) {
            b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        } else {
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            b.setPublicVersion(
                NotificationCompat.Builder(ctx, channel)
                    .setSmallIcon(R.drawable.ic_stat_prayer)
                    .setContentTitle("Smart Prayer Mode")
                    .setContentText("Prayer notification")
                    .build()
            )
        }
    }

    private fun show(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    /** Minimal notification required while the Azan foreground service runs. */
    fun azanPlaying(ctx: Context, displayName: String): Notification {
        val stop = PendingIntent.getService(
            ctx, 10,
            Intent(ctx, PrayerService::class.java).setAction(PrayerService.ACTION_STOP_AUDIO),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CH_AZAN)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle("$displayName – Azan")
            .setContentText("Playing Azan")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(activity(ctx, MainActivity::class.java, 11))
            .addAction(0, "Stop Azan", stop)
            .build()
    }

    /**
     * @param auto true when Prayer Mode is starting: adds the full-screen intent so the screen
     * opens even if a direct background activity start is refused.
     */
    fun postPrayerTime(
        ctx: Context,
        displayName: String,
        auto: Boolean,
        endMillis: Long = 0L,
        lockScreen: Boolean = true,
    ) {
        val builder = NotificationCompat.Builder(ctx, CH_PRAYER)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
        applyVisibility(ctx, builder, lockScreen, CH_PRAYER)
        if (auto) {
            val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endMillis))
            val pi = activity(ctx, PrayerModeActivity::class.java, 12)
            builder.setContentTitle("$displayName – Prayer Mode")
                .setContentText("Active until $until")
                .setOngoing(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
        } else {
            builder.setContentTitle("It is time for $displayName")
                .setContentText("May Allah accept your prayer")
                .setAutoCancel(true)
                .setContentIntent(activity(ctx, MainActivity::class.java, 13))
        }
        show(ctx, ID_PRAYER, builder)
    }

    /** "Dhuhr in 10 min" heads-up; removes itself when the prayer time arrives. */
    fun postReminder(ctx: Context, prayerLabel: String, timeMillis: Long, lockScreen: Boolean) {
        val now = System.currentTimeMillis()
        val minutes = ((timeMillis - now + 30_000L) / 60_000L).coerceAtLeast(0L)
        val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))
        val builder = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle(if (minutes <= 0L) "$prayerLabel time is now" else "$prayerLabel in $minutes min")
            .setContentText("$prayerLabel at $at")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setWhen(timeMillis)
            .setShowWhen(true)
            .setTimeoutAfter((timeMillis - now).coerceAtLeast(60_000L))
            .setContentIntent(activity(ctx, MainActivity::class.java, 14))
        applyVisibility(ctx, builder, lockScreen, CH_REMINDER)
        show(ctx, ID_REMINDER, builder)
    }

    fun cancelPrayerTime(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_PRAYER)
    }
}
