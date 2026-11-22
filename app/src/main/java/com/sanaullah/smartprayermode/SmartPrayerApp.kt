package com.sanaullah.smartprayermode

import android.app.Application
import com.sanaullah.smartprayermode.admin.DeviceOwnerHelper
import com.sanaullah.smartprayermode.audio.AzanPlayer
import com.sanaullah.smartprayermode.scheduler.PrayerSession
import com.sanaullah.smartprayermode.service.PrayerNotifications

class SmartPrayerApp : Application() {
    lateinit var azanPlayer: AzanPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        azanPlayer = AzanPlayer(this)
        PrayerNotifications.createChannels(this)
        PrayerSession.load(this) // recover an unexpired Prayer Mode session after a crash
        DeviceOwnerHelper.applyLockTaskPolicy(this)
    }
}
