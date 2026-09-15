# Changelog

Neither release below has been published. They are build labels: 2.0.0 is the
rebuild, 2.1.0 the visual language on top of it, and a tester holding both APKs
can tell which is which.

## 2.1.0

Mauve Editorial. Visual language only — no screen, component, data flow or
behaviour changed.

### Changed

- **Both themes are now two ends of one tonal ramp** at the logo's hue, with
  identical token names in each, so components reference tokens and dark mode is
  a re-skin rather than a second palette. Surfaces are paper-warm
  (`#F8F3F7` / `#1B131A`) rather than white and black.
- **Brand and text colours are deliberately different.** The logo's `#A75B87`
  measures 4.27:1 on the light surface — fine for a large graphic, short of
  4.5:1 as text — so it stays on artwork and text uses `#964C74`, one ramp step
  deeper, at 5.35:1.
- A serif display face on screen titles and the now-playing station name;
  everything at 17sp and below stays sans, where a serif costs legibility
  without buying character.
- An explicit 8/10/14/16/18dp shape scale, a type scale, and snackbar colours
  that no longer inherit whatever Material picked.
- Station tiles are 14dp with 4:3.1 artwork — station logos are wordmarks, which
  a square crop cut in half — and the playing tile gains an inset ring.
- The language row is the first row in Settings, and the Settings cards use the
  same 14dp outlined card as every other screen.
- **Dark mode is designed, not derived.** Light separates layers by a luminance
  jump — white cards on paper — that dark cannot copy. Dark uses a stepped tonal
  ladder on the brand hue instead: page, card (1.21:1), raised mini player and
  dialogs (1.50:1), snackbar (2.03:1), hairline (2.41:1), with Material's
  elevation overlay off so those are the colours that render. Primary becomes a
  pale saturated mauve with plum text rather than a greyed-out ink. Worst text
  pair 5.40:1; nothing pure black or white.
- The live dot inside a "Live" pill was the same hue as the pill and invisible in
  both themes; it now takes the pill's text colour.

### Added

- **Generated station artwork** as the logo fallback. Real scraped logos are
  still what gets shown; what changed is what appears while one loads or when
  one 404s. Every station used to fall back to the same radio glyph, so a slow
  or broken row read as a list of duplicates. Deterministic per station, so it
  does not flicker to a new colour on each bind.

### Fixed

- The live-dot pulse now stops when the device's animator duration scale is set
  to off. It is an infinite hand-rolled animation, so unlike a view animation it
  did not honour that setting by itself.
- `values-night/themes.xml` is gone. It redeclared the entire theme so that one
  boolean could differ, while every colour in it already swapped through
  `values-night/colors.xml` — two copies that had to be kept in step by hand,
  and this round is where they would have drifted.

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

- **Mauve Editorial.** Material 3 throughout, with both themes derived as the two
  ends of one tonal ramp at the logo's hue rather than two separate palettes —
  identical token names in each, so every component references tokens and dark
  mode is a re-skin rather than a second design. Paper-warm surfaces instead of
  white and black, a serif display face on screen titles and the now-playing
  station name, and a 8/10/14/16/18dp shape scale.
- Brand and text colours are deliberately different: the logo's `#A75B87` is
  4.27:1 on the light surface — fine for artwork, short of 4.5:1 as text — so it
  stays on artwork and text uses `#964C74`, one ramp step deeper, at 5.35:1.
- Stations fall back to their own generated artwork instead of one shared radio
  glyph, so a slow or broken row no longer reads as a list of duplicates.
- The live-dot pulse stops when the device's animation scale is set to off.
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
