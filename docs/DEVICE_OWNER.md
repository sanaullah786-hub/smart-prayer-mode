# Device Owner / Kiosk setup

Android only lets a **Device Owner** app use Lock Task mode to block other apps. Smart Prayer Mode
supports it but never requires it.

| Mode | Behaviour at prayer time |
|------|--------------------------|
| Device Owner active | Prayer Mode starts Lock Task: home, recents, notifications and other apps are blocked. Only this app and the default dialer (emergency calls) are allowlisted. |
| Not Device Owner | Prayer Mode is a normal full-screen activity (shown over the lock screen, screen kept on). Nothing is locked; the user can leave it. |

## Requirements

- Android 10+ phone or tablet you control (personal phones work, but see warnings).
- A computer with [adb](https://developer.android.com/tools/adb) and a USB cable.
- On most devices **no accounts** may be signed in when setting the Device Owner (remove Google,
  Samsung, Xiaomi… accounts first). A freshly factory-reset device is the most reliable.
- Some manufacturers add extra switches (e.g. Xiaomi: "USB debugging (Security settings)").

## Steps

1. Install the APK.
2. Remove all accounts: *Settings → Accounts*.
3. Enable *Developer options* (tap *Build number* 7×) and *USB debugging*.
4. Connect the phone and run:

   ```bash
   adb shell dpm set-device-owner com.sanaullah.smartprayermode/.admin.PrayerDeviceAdminReceiver
   ```

   Success prints `Success: Device owner set to package ...`.
5. Open the app → *Settings → Advanced → Device Owner*. It should show **Device Owner: ACTIVE**.
   Tap **Apply lock policy** if it says Lock Task is not yet allowlisted.
6. Use **Test Prayer Mode (20 s)** to see it work.
7. Re-add your accounts afterwards if you like (Device Owner stays).

Common errors: `Not allowed to set the device owner because there are already some accounts`
(remove accounts), `already several users` (remove secondary users/work profiles).

## What is configured

- `setLockTaskPackages`: this app + `TelecomManager.getDefaultDialerPackage()`.
- `setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)`: no home, overview, status-bar, or notification access.
- The Prayer Mode activity calls `startLockTask()` only if `isLockTaskPermitted()`.
- It calls `stopLockTask()` when the session ends, on hold-to-exit, and when the activity is destroyed.

## Never trapped

- **Emergency Call** button opens the dialer, which is allowlisted.
- **Hold to end Prayer Mode** (3 s) always works.
- Sessions have an absolute end time, a backup end alarm, and are cleared on app start/boot if expired.
- If the app crashes, the session is restored from storage and still ends at the same time.
- If you disable *Automatic Prayer Mode* nothing locks.
- **Release Device Owner** (in-app) returns the device to normal management. Android Settings cannot
  remove a Device Owner, and `adb shell dpm remove-active-admin` only works for test-only builds, so keep this
  button available. As a last resort a factory reset removes Device Owner.

## Warnings

- Do not set Device Owner on a phone you cannot reset or reach with adb.
- Prayer Mode blocks other apps for the configured duration (default 15 min); choose it deliberately.
- Android does not let any app disable the emergency call function; this app does not try to.
