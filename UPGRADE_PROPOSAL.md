# Radio Diaspora — state, changes applied, and what is still open

Last updated 2026-09-13, against commit `88dc59f` plus the two rounds of work below.

---

## 1. What the app is

One `:app` module, view-based (ViewBinding, no Compose).

- **Catalogue:** scraped from `radio.or.ke` with Jsoup.
- **Stream resolution:** `api.instant.audio/data/streams/81/{id}`; HLS preferred for
  playback, MP3/AAC preferred for recording.
- **Playback:** Media3 `ExoPlayer` in `RadioService`, driven through a `MediaController`.
- **Recording:** `RecordingService` copies the progressive stream to a file.
- **Cast:** `play-services-cast-framework`, with the SDK's own media notification.

Toolchain: AGP 8.11.1, Gradle 8.13, Kotlin 2.0.21, Java 11, minSdk 24,
target/compileSdk 36, Media3 1.3.1, Cast 21.5.0, Coil 2.6.0, jsoup 1.17.2.

---

## 2. Round one — interface

Material 3 palette derived from the logo's mauve `#A75B87`, replacing a template
palette whose `#e0b0ff` primary with white text sat at roughly 1.5:1 contrast; a real
dark theme (`values-night` previously overrode two roles); window insets in every
activity, because `targetSdk 36` forces edge-to-edge on Android 15+; a width-derived
grid span instead of a fixed three columns; `ListAdapter` + `DiffUtil`; search,
favourites, a now-playing badge, pull-to-refresh and loading/empty/error states;
`MediaLibraryService` downgraded to `MediaSessionService` since no browse tree was
implemented; back navigation on About and Settings, which had none under a
`NoActionBar` theme.

---

## 3. Round two — recording, settings, fixes

### Recording

**Android has no MP3 encoder.** `MediaCodec` decodes MP3 but cannot encode it, so
"always MP3" would mean bundling LAME through the NDK. That is not what happens here,
and the result is better than transcoding would be:

- The stream API already offers a progressive **MP3 or AAC** URL for most stations.
  Those bytes are a valid file as they stand, so `RecordingService` writes them
  verbatim. Bit-perfect, no CPU cost, and an `.mp3` file that really is MP3.
- The extension always matches what was written. An AAC stream is saved as `.aac`,
  never mislabelled `.mp3`.
- Stations offering **only HLS** cannot be recorded this way — concatenated segments
  are not a playable file — so the record button is disabled and says why. Remuxing
  HLS into an `.m4a` with `MediaMuxer` is the follow-up if those stations matter.
- No `Icy-MetaData` header is sent. Requesting it makes Shoutcast servers interleave
  title blocks into the audio, which corrupts the file.
- The recorder opens its own connection rather than tapping the player's, so pausing,
  switching station or a Cast hand-off does not affect a recording in progress.
- A dropped connection keeps whatever was written; a partial MP3 still plays.

Files land in `Music/Radio Diaspora` — MediaStore on API 29+ (no storage permission),
a scanned file below that — named `Citizen FM 91.5 - 2026-09-13 14-32.mp3`. Sortable,
readable, and stripped of every character FAT32 rejects. A recordings screen lists
them with play, share and delete.

### Settings

Theme (system/light/dark), resume last station, keep the screen on, Wi-Fi only,
recording length limit, clear image cache, replay onboarding. Every switch is read
somewhere — none are decorative.

### Two reported bugs

- **The mini player vanished after a rotation while the notification kept playing.**
  A `Player.Listener` added after the `MediaController` connects only hears about
  *changes*, so when the session was already playing nothing fired and the sheet stayed
  hidden. The controller's state is now read once on connect and rendered. The station
  id travels in the session's `MediaMetadata` extras so the now-playing badge survives
  too, and the catalogue is kept in saved instance state instead of being re-scraped on
  every rotation.
- **No way to stop casting without opening the app.** The app was not using
  `CastMediaOptions`, so the Cast SDK's own notification never appeared. It is now
  enabled with play/pause and a disconnect action, and the receiver session ends when
  the app is swiped from recents.

### Welcome screens

Five pages instead of three — welcome, search and favourites, background playback
and Cast, recording, and the user agreement, which stays last because the final
button is what accepts it. Adds a Skip that jumps *to* the agreement rather than
past it. The page list lives in the adapter, so the activity can no longer
disagree with it about which page is last; it previously hard-coded `== 2` in two
places.

### Offline and connection failures

"You are offline" and "the site is down" were the same stack trace and the same
message. They are distinct states now, and a `ConnectivityManager` callback
reloads the list by itself when the connection returns rather than leaving the
user to find the retry button. A failure with a usable list already on screen is a
Snackbar with a retry action, anchored above the mini player, instead of a toast
that cannot be acted on — and failing to resolve a stream offers a retry too. The
playing tile is marked with a pulsing dot, cancelled in `onViewRecycled` so a
recycled tile does not keep blinking for a station it no longer shows.

### The splash screen — deliberately not touched

`ic_splash_logo.xml` is a 240×360dp portrait artwork with a white plate filling
the whole canvas. Two things follow from that:

- Android 12+ masks `windowSplashScreenAnimatedIcon` to a circle — a 288dp icon
  with roughly the inner 192dp visible — so a 2:3 portrait is clipped top and
  bottom.
- The plate is load-bearing. The wordmark inside it is near-black (`#121416`,
  `#0B0C0D`), so deleting the plate to get a transparent background would make the
  wordmark invisible in dark mode. That is why `windowSplashScreenBackground` is
  white in *both* themes; it costs a white flash on a dark device, which is the
  lesser problem.

The fix is an artwork job, not a code edit: export a square splash icon with the
mark sized to fit the inner 192dp, a transparent background, and either a form
that reads on both light and dark or a `values-night` variant. Editing a 34KB
vector I cannot preview would have been guesswork.

### Also in this round

`applicationId` → `io.github.deaspo.radiodiaspora`; unused dependencies and the
`appdistribution` plugin removed; `StationParser` extracted and covered by unit tests;
a GitHub Actions workflow; a `network_security_config.xml`; a Swahili locale wired to
`android:localeConfig`; version **2.0.0** (`versionCode` 2) with a `CHANGELOG.md`.

Major rather than minor: semver's MAJOR is for breaking changes, and for an app the
equivalent is a rebuilt interface plus a changed identity. Every screen was
redrawn, the app gained a capability it did not have, and the `applicationId`
changed — a user upgrading would not recognise 1.2.0 as describing that. Since
nothing has been published there is no upgrade path to preserve, so `versionCode`
restarts at 2 against the new id.

---

## 4. Round three — the full-screen player and the language picker

### Full-screen player

The mini player was the only player. It is 88dp of a bottom sheet shared with a
record button and a Cast button, which is enough to confirm what is playing and
nothing more.

`PlayerActivity` is a real screen — in the back stack, in recents, rotatable —
that owns no player at all. It connects its own `MediaController` to the same
`RadioService` session, exactly as `MainActivity` does. That is what lets it be
opened, turned and closed without interrupting a byte of audio, and it is why it
simply finishes when the session goes idle: there is nothing left to show.

Two details worth recording, because both are easy to get wrong:

- It reads the session state **once on connect**. A `Player.Listener` added after
  the controller has connected only hears about *changes*; a screen that waits for
  one shows an empty player over audio that is already playing. This is the same
  bug that lost the mini player after a rotation.
- It does **not** close on an idle controller it has never seen active. A station
  tapped a moment earlier may still be resolving, and audio handed to a Cast device
  leaves the local controller idle by design. Closing on either would make the
  screen unopenable in exactly the cases where it is most wanted.

`RecordingLauncher` now holds the preconditions for recording — a recordable
stream, a connection, the Wi-Fi-only setting, storage permission below API 29 —
because both players offer the button, and the same rules written out twice is
how two screens come to disagree about when recording is allowed.

### The mini player, rebuilt

The Media3 `PlayerControlView` was built for video. On a live stream its timeline
and position label are meaningless, and it could not be laid out next to the
record and Cast buttons without crowding all three. It is gone, and with it the
`androidx.media3:media3-ui` dependency — nothing else referenced it.

In its place: an explicit play/pause button with the buffering ring drawn around
it, a live dot on the signal line while audio is actually flowing, and a grab
handle showing the card opens into something bigger. Tapping the card, or the
chevron on it, opens the full-screen player; the chevron exists so that is
discoverable and so TalkBack has something to announce.

### Language picker

Swahili shipped in round two but was reachable only by changing the whole device,
or through the Android 13+ per-app language screen that most people never open.
Settings now has a language row: English, Kiswahili, or follow the system.

AppCompat owns the value. On Android 13+ `setApplicationLocales` hands it to the
platform, so the app's own picker and the system screen show the same thing; below
that, `AppLocalesMetadataHolderService` — declared in the manifest with
`autoStoreLocales` — persists it. Nothing is written to `SharedPreferences`: a
second copy of the setting would only give the two somewhere to disagree.

`AppLanguageTest` asserts the offered locales match `res/xml/locales_config.xml`,
so shipping a translation that the picker does not offer, or the reverse, fails
the build.

---

## 5. Before this builds

1. **Firebase must know the new package.** The Google Services plugin matches on
   `applicationId`, and `app/google-services.json` still lists
   `com.example.kenyanradiostations`. Add `io.github.deaspo.radiodiaspora` to the
   `radio-diaspora` Firebase project and replace the file, or
   `:app:processDebugGoogleServices` fails with *"No matching client found for package
   name"*. Reverting the `applicationId` line is the other option.
2. **`app/src/main/java/com/kenyanradio/` should be deleted**, along with
   `PlayerViewModel.kt`. Both are emptied to comments — the tooling used here cannot
   remove files.
3. `./gradlew testDebugUnitTest :app:assembleDebug`.

---

## 6. Open items — how each is being closed

**Nothing in any round has been compiled here.** The Windows update of
8 September blocks the workspace from mounting the project folder. Resource
references, XML well-formedness and ViewBinding field names are verified
mechanically; the build is the real gate.

### R8 — resolved in code, needs one verification run

`proguard-rules.pro` is no longer empty. Two rules were genuinely needed and
neither was obvious:

- **`CastOptionsProvider` is referenced only as a string** in a manifest
  `<meta-data android:value>`. R8 cannot see that, strips the class, and
  `CastContext.getSharedInstance` then throws on first launch of a minified
  build. This is the sort of failure that only ever appears in release.
- **Crashlytics needs `SourceFile,LineNumberTable`** kept, or release stack
  traces arrive without line numbers.

Rules are written full-mode-correct (`-keep class A { <init>(); }`), because
`-keep class A` alone does not imply constructors under R8 full mode, which is
the AGP 8 default.

One latent bug was fixed while looking: `Settings.Theme` persisted
`Enum.name`, which R8 is free to rename — a user's saved theme would decode to
nothing after minification. It stores an explicit `key` now.

A **`staging` build type** was added: the release R8 pipeline, signed with the
debug key, so it installs without a release keystore.

```
./gradlew installStaging
```

Then exercise playback, recording, Cast and the station list on a minified build.
That is the verification the old note asked for, now one command instead of a
keystore setup.

### Swahili — reviewed and cleared

`docs/translation-review-sw.md` and `.csv` pair every English string with its
proposed Swahili and a Correction column, with the 16 riskiest entries — long
copy, legal wording, anything carrying a technical term — pulled to the top. A
reviewer fills in a column; nobody touches XML. There is a sign-off block at the
top, and the file says the locale should not ship until it is signed.

**Signed off by Polycarp Okock on 13 September 2026 — the locale is cleared to
ship.** The sheet stays with the strings: anything added to `values-sw` after
that date is unreviewed until it appears in the sheet with its own tick, and the
three share-confirmation strings added since are already listed as outstanding.

### Cleartext — resolved by measuring it

Whether any station actually uses `http` was never measured; it was a guess in
both directions. `tools/check-stream-schemes.ps1` settles it — it scrapes the
same station list the app does, queries the same stream API, and prints the
scheme per stream.

```
powershell -ExecutionPolicy Bypass -File .\tools\check-stream-schemes.ps1
```

If nothing is cleartext, the open item closes with no code change. If something
is, the script prints a ready-to-paste `<domain-config>` naming only those hosts
— which keeps cleartext off for everything else, including the station API. It
also reports stations whose stream lookup fails outright, which are broken in the
app too.

### Foreground service timeout — resolved in code

Verified against Android's documentation rather than memory: `dataSync` and
`mediaProcessing` foreground services get **six hours per 24-hour period** from
Android 15, the platform calls `Service.onTimeout(int, int)`, and a service that
does not stop within a few seconds is killed with
`RemoteServiceException`. The quota resets when the app is next brought to the
foreground.

`RecordingService` now overrides `onTimeout`, finalises the file and tells the
user why it stopped — so a six-hour recording is *saved* rather than lost to a
process kill. The default limit is 60 minutes and the longest preset is 120, so
only "No limit" can reach the cap; that option now says so.

### Recording and copyright — decided and implemented

**Option B, chosen 13 September 2026: sharing stays, behind a confirmation.**
`RecordingsActivity` now shows a dialog naming the issue before opening the
chooser, every time.

Stated plainly, because it matters more than the dialog does: recordings are
indexed in `Music/`, so any file manager can still share them. This prevents
nothing. What it changes is that the app no longer presents redistributing
someone else's broadcast as a frictionless one-tap feature, and the person doing
it sees the position first. Options and rationale are in
`docs/recording-and-copyright.md`.

Still worth settling: the CC BY-NC-SA licence, which Creative Commons advises
against for software and which rules out ever monetising this.

### Repository hygiene — resolved

The root ignore file was still the Android Studio template. It now covers
`.kotlin`, the whole `.idea` directory, `*.apk`/`*.aab`, and — before a signing
config exists rather than after — `*.jks`, `*.keystore` and `keystore.properties`.
`app/google-services.json` is ignored: not a secret by design, but it pins the
Firebase project and its API key into a public CC-licensed repository, CI writes a
placeholder, and a local build takes it from the console.

Two things stay tracked and must: the Gradle wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/`) and `gradle/libs.versions.toml`. A checkout cannot build
without them.

An ignore rule does not untrack anything already committed — `git rm --cached`
does that.

---

## 7. Still open

- **Toolchain and dependency bumps.** AGP 8.11.1 → 9.x, Media3 1.3.1 → 1.11.x,
  Cast 21.5.0 → 22.x. One commit each, in that order, AGP last.
- **`gradlew` is missing from the repository root.** Only `gradlew.bat` is
  present, so CI and every macOS or Linux checkout fail before Gradle starts.
  `git checkout -- gradlew` if git still tracks it, otherwise regenerate both
  scripts with `.\gradlew.bat wrapper --gradle-version 8.13`.
- **No release keystore or signing config.** `staging` covers testing; a real
  release still needs one.
- **No instrumentation tests.** The unit tests cover parsing, stream selection
  and file naming. The service, MediaStore writes and the Cast hand-off are
  untested.
- **The splash artwork** (see above) still clips in the Android 12+ circular
  mask and forces a white background in dark mode.
- **Eleven Swahili strings are unreviewed.** The player and language strings
  added in round three postdate the 13 September sign-off. They are listed under
  "Added after sign-off" in `docs/translation-review-sw.md`; the locale ships
  either way, but that table is the record of what a first-language speaker has
  actually checked.
