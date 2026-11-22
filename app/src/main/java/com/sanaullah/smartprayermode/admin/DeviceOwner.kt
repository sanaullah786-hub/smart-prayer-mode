package com.sanaullah.smartprayermode.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log

/** Empty receiver: its only job is to be registered as the Device Owner component. */
class PrayerDeviceAdminReceiver : DeviceAdminReceiver()

/**
 * Lock Task (kiosk) support. Real device locking is only possible when this app is the
 * Device Owner; on every other device Prayer Mode is a normal full-screen activity.
 */
object DeviceOwnerHelper {
    private const val TAG = "DeviceOwnerHelper"

    const val ADB_COMMAND =
        "adb shell dpm set-device-owner com.sanaullah.smartprayermode/.admin.PrayerDeviceAdminReceiver"

    fun admin(context: Context) = ComponentName(context, PrayerDeviceAdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    /** True when Prayer Mode can really lock the device right now. */
    fun canLock(context: Context): Boolean =
        runCatching { dpm(context).isLockTaskPermitted(context.packageName) }.getOrDefault(false)

    /**
     * Allowlists this app and the default dialer (for emergency calls) for Lock Task mode
     * and disables every other lock-task system feature (home, recents, notifications).
     */
    fun applyLockTaskPolicy(context: Context) {
        if (!isDeviceOwner(context)) return
        try {
            val packages = linkedSetOf(context.packageName)
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                ?.let { packages.add(it) }
            val manager = dpm(context)
            manager.setLockTaskPackages(admin(context), packages.toTypedArray())
            manager.setLockTaskFeatures(admin(context), DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        } catch (e: Exception) {
            Log.e(TAG, "Could not apply lock task policy", e)
        }
    }

    /** Gives up Device Owner status so the device is returned to normal management. */
    @Suppress("DEPRECATION")
    fun releaseDeviceOwner(context: Context): Boolean = try {
        dpm(context).setLockTaskPackages(admin(context), emptyArray())
        dpm(context).clearDeviceOwnerApp(context.packageName)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Could not release device owner", e)
        false
    }
}
