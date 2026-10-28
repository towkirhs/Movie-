# MovieLive

Minimal Android app (Kotlin, Jetpack Compose, Media3). Dark UI with Home, Movies, TV Shows, Live and My List tabs. Movie and TV data come from TMDB.

## Setup
1. Get a free TMDB API key and put it in `local.properties` (see `local.properties.example`).
2. Add your own video links in `app/src/main/assets/sources.json` (see the `_help` field). Only add streams you have the right to watch.
3. Open in Android Studio, build and run.

## Notes
- Watch History and My List are stored on the device.
- The Live tab lists the channels from `sources.json`, grouped by `time`.
