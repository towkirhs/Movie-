# MovieLive

Minimal Android app (Kotlin, Jetpack Compose, Media3). Dark UI with Home, Movies, TV Shows, Live and My List tabs. Movie and TV data come from TMDB.

## Setup
Build and run in Android Studio, then open **Settings** (gear icon) and enter:
- **TMDB API key** — free for personal use at themoviedb.org (account > Settings > API).
- **Movie link** — URL template with `{id}` (TMDB movie id).
- **TV show link** — URL template with `{id}`, `{s}` (season), `{e}` (episode).
- **Live playlists (M3U)** (a URL or an imported file) and/or single live channels.

Only add links to content you have the right to watch. Everything is stored on the device.

This product uses the TMDB API but is not endorsed or certified by TMDB.
