# Azan audio assets

**No Azan recordings are included in this repository.** Only recordings you are legally
allowed to use may be added here.

The app looks for these exact file names (MP3, ideally 128 kbps or higher):

| Azan option                 | Expected file              | Status                         |
|-----------------------------|----------------------------|--------------------------------|
| Madinah (Masjid an-Nabawi)  | `azan/madinah.mp3`         | **MUST BE SUPPLIED** (default) |
| Makkah (Masjid al-Haram)    | `azan/makkah.mp3`          | **MUST BE SUPPLIED**           |
| Egyptian                    | `azan/egyptian.mp3`        | **MUST BE SUPPLIED**           |
| Turkish                     | `azan/turkish.mp3`         | **MUST BE SUPPLIED**           |
| Custom                      | chosen in the app          | picked from the device by the user |

Place files in `app/src/main/assets/azan/`. Until a file exists, that Azan is shown as
unavailable in the app, its preview button is disabled, and at prayer time the app plays the
device notification tone instead (it is never described as an Azan).

Before adding a file:

1. Obtain it under a licence that allows redistribution inside an app (public domain, CC0,
   CC-BY with attribution, or written permission from the rights holder).
2. Do **not** rip audio from YouTube or from sites that do not grant such a licence.
3. Do **not** label a synthesized or re-recorded voice as the Masjid an-Nabawi recording.
4. Record the source, author, licence and URL in `docs/AUDIO_LICENSES.md`.
