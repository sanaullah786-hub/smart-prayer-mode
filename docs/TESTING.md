# Testing

## Automated (JVM unit tests) — `./gradlew testDebugUnitTest`

`app/src/test/.../CoreLogicTest.kt` covers:

- all six times calculated, strictly increasing; five obligatory prayers exclude Sunrise
- Dhuhr sanity check for Makkah; Hanafi Asr later than Shafi Asr
- manual adjustment shifts a time by exactly the requested minutes
- different calculation methods differ; an invalid method does not crash
- next-prayer selection (before Fajr, after Isha → tomorrow's Fajr, skipping Sunrise)
- daylight-saving transition in London (clock hour moves 12 → 13)
- Hijri conversion round trip, Ramadan detection and countdown
- Qibla bearings (Karachi, London) and distance
- countdown formatting, default settings

Instrumented tests are not included: the features below depend on hardware, system services and
OEM behaviour and must be checked on a device.

## Manual device checklist

| # | Test | How | Expected |
|---|------|-----|----------|
| 1 | Location detection | Setup → *Detect my location* (GPS on) | City and country appear; times shown |
| 2 | Permission denial | Deny location | Message + manual city list works; app does not crash |
| 3 | Prayer times | Compare with a trusted source for your city/method | Within ~1–2 min |
| 4 | Five schedules | Set phone clock a minute before each prayer | Alarm fires each time and the next is scheduled |
| 5 | Azan playback | Add an MP3 to `assets/azan`, *Test Azan* | Plays, pause/stop/volume work |
| 6 | Audio interruption | Call/other app while Azan plays | Pauses/ducks, resumes or stops cleanly |
| 7 | Missing Azan file | Default build | Entry says "must be supplied"; prayer time plays the notification tone |
| 8 | Automatic Prayer Mode | Advanced → *Test Prayer Mode (20 s)*; or real prayer time | Full-screen screen, countdown, auto-ends |
| 9 | Countdown accuracy | Watch the timer for the whole test | Reaches 00:00 together with the auto-end |
| 10 | Device Owner | Follow `docs/DEVICE_OWNER.md` | Other apps/home blocked during test |
| 11 | Emergency access | Tap *Emergency Call* in lock mode | Dialer opens; return goes back to Prayer Mode |
| 12 | Automatic unlock | Wait for the end | Lock released, screen closes |
| 13 | Safe exit | Hold the exit button 3 s | Prayer Mode ends immediately |
| 14 | Reboot recovery | Reboot during Prayer Mode / before a prayer | Alarm re-armed; unexpired session resumes after unlock |
| 15 | Crash recovery | `adb shell am kill com.sanaullah.smartprayermode` during test | Session restored, still ends on time |
| 16 | Battery restrictions | Enable battery saver / restrict app | Check the permission screen warnings; allow unrestricted battery |
| 17 | Time zone change | Change device time zone (auto-location mode) | Times and alarm rescheduled |
| 18 | Auto Prayer Mode OFF | Turn the switch off, test prayer | Azan + notification only, no lock |
| 19 | Exact alarm denied | Revoke "Alarms & reminders" | Falls back to alarm-clock alarm; still fires |
| 20 | Qibla | Compare with a known direction | Arrow points to the Kaaba; handles no-sensor devices |

## CI

`.github/workflows/android.yml` runs unit tests and builds the debug APK on every push to `main`.
