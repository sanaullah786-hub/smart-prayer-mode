# Smart Prayer Mode

An Android app (Kotlin, Jetpack Compose, Material 3) that calculates prayer times from your
location, plays the Azan, and automatically opens a calm **Prayer Mode** screen at each prayer —
without you opening the app. With Device Owner set up it becomes a true kiosk lock; without it,
it is a normal full-screen prayer screen. No ads, no tracking, no network access.

- **Platform:** Android 10+ (minSdk 29, target/compile SDK 35)
- **Build:** Gradle Kotlin DSL, AGP 8.7.3, Kotlin 2.0.21, JDK 17
- **Package:** `com.sanaullah.smartprayermode`

## Features

| Area | What it does |
|------|--------------|
| Prayer times | Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha via the [Adhan](https://github.com/batoulapps/adhan-java) library. 10 calculation methods, Hanafi / Shafi Asr, per-prayer manual adjustments, DST-safe (java.time). |
| Location | One-shot GPS/network fix with city + country lookup, offline manual city list, timezone handling. Never tracked continuously; refreshed only on setup, manual refresh, or at launch when older than 12 h. Permission denial falls back to manual selection. |
| Azan | Madinah (default), Makkah, Egyptian, Turkish and Custom file. Play/pause/stop, preview, test, volume, ON/OFF, audio-focus handling (pause on calls, duck, stop on loss), resources released on stop. **No audio is bundled (none with a clear licence was found); add your own licensed file to any slot, and Fajr can use a different Azan.** |
| Prayer Mode | At prayer time: Azan + full-screen dark-green/gold screen with prayer name, clock, countdown, greeting, **Emergency Call** and a hold-to-exit button. Ends automatically after the configured duration (5/10/15/20/30/custom). |
| Device lock | Lock Task (kiosk) mode when the app is Device Owner; dialer allowlisted for emergencies. Otherwise a normal full-screen fallback. |
| Extras | Qibla compass (rotation-vector sensor, declination-corrected), Hijri (Umm al-Qura) calendar, Ramadan countdown, Sehri/Iftar table, notifications, 20 s Prayer Mode test button. |
| Notifications | Master switch, per-prayer switches (Fajr/Dhuhr/Asr/Maghrib/Isha), reminder 5-30 min before each prayer, lock-screen privacy, vibration, permission status, shortcuts to Android's channel settings, test button. |
| Background | `AlarmManager` exact alarms (falls back to `setAlarmClock`), one alarm only (no duplicates), boot / time-zone / time-change receivers, a short-lived media foreground service only while the Azan plays. No always-on service. |
| Storage | Room database (settings, location, method, school, Azan, duration, adjustments). `allowBackup=false`. |

## Screens

Splash · Welcome · Permission setup · Location setup · Home dashboard · Prayer times ·
Prayer Mode · Azan selection · Audio player · Qibla · Hijri calendar · Ramadan · Settings ·
Advanced settings · Device Owner guide.

## Azan audio — action required

No recordings are included because I could not verify their licences. Add licensed MP3s as
described in [`app/src/main/assets/azan/README.md`](app/src/main/assets/azan/README.md) and record
the licence in [`docs/AUDIO_LICENSES.md`](docs/AUDIO_LICENSES.md). Until then the Madinah/Makkah/
Egyptian/Turkish entries show **"must be supplied"** and are disabled; users can still pick a
**Custom** file. When a file is missing at prayer time the device notification tone plays instead
(never presented as an Azan).

## Build

```bash
# Requires JDK 17 and the Android SDK (platform 35). Android Studio does this for you.
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

Open the folder in Android Studio (Koala or newer) and press Run. GitHub Actions
(`.github/workflows/android.yml`) builds the debug APK, runs the unit tests and uploads the APK as
the **smart-prayer-mode-debug-apk** artifact on every push to `main`.

## Device Owner (full lock)

A normal Android app cannot block other apps. Real locking needs Device Owner, set once with adb:

```bash
adb shell dpm set-device-owner com.sanaullah.smartprayermode/.admin.PrayerDeviceAdminReceiver
```

Full steps, requirements and safety notes: [`docs/DEVICE_OWNER.md`](docs/DEVICE_OWNER.md).
Without Device Owner, Prayer Mode is a full-screen screen and **nothing is locked**.

## Safety design

- The **Emergency Call** button is always shown; the dialer is allowlisted for Lock Task.
- Hold **"Hold to end Prayer Mode"** for 3 s to leave at any time.
- Every session has an absolute end time, a backup end alarm, and is re-checked on app start,
  crash recovery and reboot, so Prayer Mode can never outlast its duration.
- Turning off *Automatic Prayer Mode* disables all automatic locking (Azan and notification still work).
- Device Owner can be given up from inside the app.

## Known limitations

- Reboot recovery runs after the first unlock (`BOOT_COMPLETED`), not before it (credential-encrypted storage).
- Opening Prayer Mode over another app needs "Display over other apps" (or Device Owner); the setup screen guides the user. If the system still blocks it, a full-screen notification is shown.
- Some manufacturers (Xiaomi, Huawei, Oppo, Vivo…) kill background work aggressively; allow "Unrestricted battery" and auto-start.
- Hijri dates follow Umm al-Qura and can differ by a day from local moon sighting.
- UI strings are in Kotlin (English only) for now.
- See [`docs/TESTING.md`](docs/TESTING.md) for what is automatically tested versus what needs a device.

## Privacy

No ads, analytics or network permission. Location is read only on demand and stored on-device.
See `docs/AUDIO_LICENSES.md` for third-party licences.
