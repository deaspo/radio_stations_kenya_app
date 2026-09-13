# Changelog

## 2.0.0

A redesign plus recording. Major rather than minor: every screen was rebuilt, the
app gained a capability it did not have, and the application id changed.

### Added

- **Recording.** Tap record while a station is playing. The station's progressive
  stream is written straight to a file with no re-encoding, so it is MP3 where the
  station broadcasts MP3 and AAC otherwise — and the extension always matches what
  was actually written. Files land in `Music/Radio Diaspora`, named
  `Citizen FM 91.5 - 2026-09-13 14-32.mp3`. Stations that only offer HLS cannot be
  recorded this way and say so.
- **Full-screen player.** Tap the mini player to open it: large artwork, a live
  badge, and favourite, record, cast and stop in one place. It slides up from the
  bottom and back down on collapse, on back, or when playback stops. It drives the
  same media session as the mini player, so opening, rotating or closing it never
  interrupts the audio.
- **Recordings screen** with play, share and delete.
- **Settings**: theme, resume last station, keep the screen on, Wi-Fi only,
  recording length limit, clear image cache, replay the welcome screens.
- **Search and favourites** on the station list, with a favourites filter.
- **Stop casting from the notification**, via the Cast SDK's own media notification.
- **Swahili** translation, a language row in Settings (English, Kiswahili, or
  follow the system) and the per-app language picker in Android 13+ system
  settings. The choice is stored by AppCompat, which hands it to the platform on
  Android 13+ and persists it itself below that.
- Pull to refresh, and a distinct "you're offline" state that reloads by itself
  when the connection comes back.

### Changed

- Material 3 throughout, with a palette derived from the app's own logo and a dark
  theme that is actually dark.
- Every screen handles window insets, which Android 15 requires at this target SDK.
- Station tiles are sized by width rather than pinned to three columns, and the
  playing tile is marked with a pulsing dot.
- The mini player is built for radio: the Media3 `PlayerControlView` it used was
  built for video and brought a seek bar and position label that mean nothing for a
  live stream. It now has an explicit play/pause button with the buffering ring
  around it, a live dot on the signal line, and a handle showing it opens.
- Welcome screens cover the current feature set and can be skipped to the
  agreement.
- `applicationId` is now `io.github.deaspo.radiodiaspora`. `com.example.*` is
  rejected by Google Play and can never be changed after a first upload.

### Fixed

- The mini player disappeared after rotating the device while playback carried on
  in the notification.
- A station tapped in the first moment after launch was silently dropped.
- About and Settings had no back button.
- The station list was re-scraped on every rotation.
- `RadioService` advertised a browsable media tree it did not implement, which made
  Android Auto list the app and then fail to open it.
- Cast targets were undiscoverable on Android 13+ (missing `NEARBY_WIFI_DEVICES`).

### Removed

- `PlayerViewModel`, which built a second ExoPlayer alongside the real one.
- An unused second playback engine under `com.kenyanradio`.
- Unused dependencies: `androidx.media`, `navigation-ui-ktx`,
  `lifecycle-viewmodel-ktx`, Media3 DASH and SmoothStreaming, `media3-ui`, and the
  `appdistribution` plugin.
