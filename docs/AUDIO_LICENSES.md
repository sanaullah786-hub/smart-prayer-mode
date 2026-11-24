# Audio licences and attribution

This project **ships without any Azan audio**. Each slot below must be filled with a
recording whose licence permits inclusion in this app. Update the table when you add a file.

| Asset file (`app/src/main/assets/azan/`) | Included? | Reciter / source | Licence | URL | Attribution text |
|------------------------------------------|-----------|------------------|---------|-----|------------------|
| `madinah.mp3`   | No — **supply** | – | – | – | – |
| `makkah.mp3`    | No — **supply** | – | – | – | – |
| `egyptian.mp3`  | No — **supply** | – | – | – | – |
| `turkish.mp3`   | No — **supply** | – | – | – | – |

## Sources reviewed (2026-11-24) and why none are bundled

| Source | What it is | Licence finding | Verdict |
|--------|------------|-----------------|---------|
| [abodehq/Athan-MP3](https://github.com/abodehq/Athan-MP3) (GitHub) | A zip of Athan MP3s | No LICENSE file, no reciter credits, no usage statement | Not usable: no permission to redistribute |
| [Islamic Network community thread](https://community.islamic.network/d/256-audio-licensing-query-for-azan-pro-app) | Developer Q&A about Azan audio | Says files are "public domain" but also that "the copyright belongs to the mu'adhins" - contradictory, no named licence | Unclear: ask Islamic Network / the mu'adhin for written permission first |
| [Freesound 255231](https://freesound.org/people/RJStefanski/sounds/255231) | Street recording of a call to prayer in Hamtramck, Michigan, ~100 ft from a loudspeaker | CC BY 3.0 (credit "RJStefanski") | Licensed, but ambient field audio, not a Madinah/Makkah Azan, and Freesound requires login to download |
| [Cambridge repository, Cairo "City Wide Adhan"](https://www.repository.cam.ac.uk/handle/1810/237156) | Field recordings by Anna Kipervaser / World Oral Literature Project | No licence stated on the page | Not usable without permission; also not Madinah/Makkah |

No genuine, clearly licensed Masjid an-Nabawi recording was found. The app therefore lets you
**add your own licensed file to each Azan slot** (Settings -> Azan voice -> "Add my licensed file").
The file stays on the device and is never uploaded.

## Rules

- Only public-domain, CC0, CC-BY (with attribution shown here and in the app's About text), or
  explicitly licensed recordings may be committed.
- Recordings taken from YouTube or other unlicensed sources must never be committed.
- A genuine Masjid an-Nabawi recording must be sourced with permission; never label any other
  or synthesized audio as such.
- A user's own "Custom Azan" file is chosen on the device, is never uploaded, and is the
  user's responsibility.

## Software licences

- Prayer times and Qibla: [Adhan (Java)](https://github.com/batoulapps/adhan-java) by Batoul Apps — MIT.
- AndroidX, Jetpack Compose, Room, Navigation — Apache 2.0.
- Kotlin and kotlinx.coroutines — Apache 2.0.
