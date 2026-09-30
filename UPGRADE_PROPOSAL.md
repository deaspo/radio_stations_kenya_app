# Radio Diaspora — state, changes applied, and what is still open

Last updated 2026-09-13, against commit `88dc59f` plus the four rounds of work
below. The branch builds: `assembleStaging` is green.

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
(Round four raises this to **2.1.0** / `versionCode` 3 - see section 5.)

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

## 5. Round four — Mauve Editorial

Implementation of `plan.md` / `RadioDiaspora_design_spec_A.md`, Option A. Visual
language only: no screen, component, data flow or behaviour changed.

### What actually did the work

Nothing in the layouts referenced a colour directly — every one went through a
theme attribute already — so replacing the values in `colors.xml` and
`values-night/colors.xml` re-skinned all nine screens at once. That is why this
round is mostly two files plus a type and shape scale, rather than a pass over
every layout.

The colour resources keep their `md_` names. The spec lists token names without
the prefix, and renaming them was considered and dropped: outside `themes.xml`
and four lines of `StationAdapter`, nothing refers to them, so the rename buys
nothing and touches everything.

### The brand/primary split

The one decision worth not undoing. The logo's `#A75B87` measures **4.27:1** on
the light surface — fine for a large graphic, short of 4.5:1 for text. So the
logo colour stays on artwork, and text and icons use `#964C74`, one ramp step
deeper, at **5.35:1**. Flatten them back together and the contrast failure
returns silently.

Every ratio in both colour files was measured, against the surface the colour
actually sits on rather than the nominal background — `on_surface_variant` on
white is 6.32:1, not the 5.76:1 it scores on the surface. One figure in the
plan needed correcting: `on_primary` is quoted at 6.41:1 against `#85476B`,
which is a ramp step and not the primary token. Against the real primary it is
**5.57:1** — still passing, so the token stands.

### Two bugs in the plan's code, fixed rather than copied

- `abs(id.hashCode()) % palettes.size` returns a **negative index** when the
  hash is `Int.MIN_VALUE`, because `abs(Int.MIN_VALUE)` is still
  `Int.MIN_VALUE`. One string in four billion crashes the tile bind, and the
  ids come off a scraped page. `StationArt` uses `Math.floorMod`, and
  `StationArtTest` pins the case with a string whose hash is exactly that.
- `android.R.bool.config_reduceMotion` is an internal framework resource, not
  public API; it does not compile. `Motion` reads
  `Settings.Global.ANIMATOR_DURATION_SCALE`, which is what the accessibility
  guidance actually points at.

### Station artwork: fallback, not replacement

The plan assumes no station imagery exists. It does — the catalogue scrapes a
logo URL per station and Coil loads it. Generated art would have thrown that
away. `StationArt` is instead the **placeholder and error** drawable, replacing
the single generic glyph every station used to fall back to, which made a slow
or broken row look like a list of duplicates. Deterministic per station id, so
it does not flicker to a different colour on each bind.

Initials are the first two letters of the first word, matching the mockups. The
plan's code computes word initials while its own test asserts `"Ci"` for
`"Citizen FM"`; the two disagree, and word initials give a grid of tiles all
ending in F, because most names here are `<Name> FM <frequency>`.

### The serif

Substituted: the platform serif, not a bundled Lora. Bundling costs ~400KB and
an OFL attribution obligation, and the argument for Lora was editorial feel at
display sizes, which Noto Serif provides. Swapping Lora in later is three
`fontFamily` lines in `type.xml`. Because nothing is bundled, the About screen
needs no attribution line and the licence question the plan raises does not
arise.

Applied through `textAppearanceHeadlineSmall` and `textAppearanceTitleLarge`
only. Everything at `titleMedium` and below stays sans — the spec's own rule,
which its recordings-row entry then contradicts by asking for a 12.5sp serif.
The rule won.

### One theme, not two

`values-night/themes.xml` is deleted. It redeclared all thirty-odd colour items
identically to the light theme so that a single boolean could differ; every one
of those colours already swapped through `values-night/colors.xml`. The boolean
is now `@bool/window_light_system_bars`. Two copies of a theme that must stay
identical is a drift bug waiting to happen, and this round would have been where
it happened.

### Version

The ladder so far, and where it goes next:

| Branch | Version | Code | What it is |
|---|---|---|---|
| rounds 1-3 | 2.0.0 | 2 | rebuild: every screen, recording, new `applicationId` |
| round 4 (this one) | **2.1.0** | **3** | visual language only |
| toolchain next | 2.2.0 | 4 | Media3, Cast, AGP/Gradle — see section 8 |

**2.1.0**, `versionCode` 3. Minor rather than major: nothing about the app's
shape or behaviour moved, it is the same product in a different skin.

Worth being straight about what these numbers are. Nothing has been published,
so neither 2.0.0 nor 2.1.0 has reached a user, and semver's contract - which is
about what an upgrade does to someone already running the old version - is not
engaged. They are build labels. The reason to have two rather than fold the UI
round into 2.0.0 is narrow and practical: a tester holding both APKs can tell
which is which, and the About screen says so. If that is not worth a second
number, it is one line in `commit-changes.ps1` to collapse them.

### Second pass, 15 September — "the new UI is not much visible"

Reported after a device test in dark mode. Re-audited every layout against the
spec. Two findings, both real.

**The Settings cards were never restyled.** Six `MaterialCardView`s in
`activity_settings.xml` were still `Widget.Material3.CardView.Filled` at 20dp
with no outline. Every other screen had moved to the 14dp outlined card; this
one was missed. Fixed.

**The spec's dark palette collapsed on a real screen — so dark is now designed
rather than derived.** The Option A mockup is light-only; its dark values were
computed on paper and never rendered. On a device the spec's card colour sat at
**1.09:1** against the background and its snackbar at **1.04:1** against the
mini player it rests on. A card that is not visibly a card, and a snackbar the
same colour as the thing under it, is what "the new UI is not much visible"
looks like.

The light theme separates its layers by a luminance jump — white cards on warm
paper — that dark cannot copy, because there is no white to jump to. Dark uses
a **stepped tonal ladder** instead, every step on the brand hue (OKLCH h=340,
low chroma so it reads as ink rather than purple):

| layer | colour | vs page |
|---|---|---|
| page | `#1F111A` | — |
| card, row, field | `#33212D` | 1.21:1 |
| raised: mini player, dialog | `#43303C` | 1.50:1 |
| snackbar | `#584351` | 2.03:1 |
| hairline outline | `#614F5B` | 2.41:1 |

Each step reads as its own layer with no outline doing the work; the outline is
a quiet hairline rather than the thing that makes a card visible. Material's
elevation overlay is switched off in the theme so these are the colours that
actually render — otherwise Material tints every elevated surface towards
primary by its elevation, on top of the ladder, and two systems decide one
colour.

Primary flips role. In light it is an ink, `#964C74` on paper. In dark it is a
pale, saturated mauve `#E8A6D1` with deep plum text on it — the brand kept alive
at night rather than greyed out. The live dot is brighter than in light, because
on a dark page it has to glow, not sit. Nothing is pure black or pure white.
Every text pair measures **5.40:1 or better**; the raised layer needed a new
token, `colorSurfaceRaised`, which in light is simply white (the 3dp shadow does
the separating there).

One bug this surfaced in both themes: the live dot inside the "Live" pill on a
tile was `colorLiveDot` on a `colorPrimary` background — same hue, about 1.5:1,
invisible. Inside a pill the dot now takes the pill's own text colour
(`bg_live_dot_on_primary`); on a card it keeps the live-dot colour.

**Confirmed implemented, per screen:** catalogue (search field, tiles, playing
ring, badge, empty/offline states, retry button), mini player, full-screen
player, recordings rows and dialog, settings (language first, cards), onboarding
(plate, serif title, filled Next), About (serif title via the theme). Toolbar
titles on every screen pick up the serif through `textAppearanceTitleLarge`.

### Deliberately not done

- **Frequency on tiles.** `RadioStation` carries id, name and logo; the
  frequency lives in `StationDetails`, fetched per station when it is played.
  Putting it on the catalogue means one request per tile — a behaviour change,
  and out of scope.
- **Skeleton shimmer.** Needs a loading view type in the adapter. Structural.
  The existing loading state is a progress indicator.
- **Screenshot tests.** Goldens have to be generated from a run. Adding the
  dependency without them lands a red build; adding fabricated ones is worse.
- **Pill-shaped active onboarding dot.** The dots are sized in Kotlin layout
  params, so this needs a code change for a very small visual gain.
- **Dropping the mini player's stop button.** The mockup has no stop in the
  mini player. Removing it would leave no way to stop playback without opening
  the full player — a functional regression dressed as a visual one.
- **Buffering ring.** Kept as `CircularProgressIndicator` rather than a dashed
  ring driven by a hand-rolled rotation: it already honours the animator scale,
  so reduce-motion needs no special case.

---

## 6. It builds, and it runs — 15 September 2026

`assembleStaging` succeeds: the release R8 pipeline, signed with the debug key.
That closes the three things this section used to list.

- **Firebase knows the new package.** `processStagingGoogleServices` runs, so
  `app/google-services.json` and `io.github.deaspo.radiodiaspora` agree.
- **`com/kenyanradio/` is gone.** `PlayerViewModel.kt` is still on disk, emptied
  to comments; `commit-changes.ps1` deletes it and stages the deletion.
- **`gradlew` is back.** Both wrapper scripts were regenerated, so CI and
  non-Windows checkouts can build.

**The device pass is done.** Reported 15 September: the app opens, playback
works, recording works, Cast works, rotation survives, and the full-screen
player works.

Cast working is the one that mattered most. `CastOptionsProvider` is named only
as a string in a manifest `<meta-data>`, so R8 cannot see the reference; if the
keep rule were wrong the class would be stripped and
`CastContext.getSharedInstance` would throw on first launch — never at build
time, and never in a debug build, because debug does not run R8.

That closes the keep-rule item **on the condition that the build tested was the
staging one**. A debug APK exercising Cast proves the code is right and says
nothing about the keep rules, since R8 never ran. If the tested build came from
`installStaging`, the item is closed. If it came from `installDebug`, one more
run of `installStaging` closes it.

The JDK is worth recording: Gradle 8.13 supports up to Java 24, and current
Android Studio bundles JBR 25. The Gradle JDK has to be set to 17 or 21 until
the AGP 9 / Gradle 9.6 migration below.

---

## 7. Open items — how each is being closed

**What has been verified by machine, and what has not.** The workspace this was
written in still cannot mount the project folder — a Windows update of
8 September — so resource references, XML well-formedness, ViewBinding field
names, string parity and placeholder arity were all checked mechanically there.
The compile and the R8 run happened on the development machine and passed. No
part of the app has been exercised on a device.

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

`assembleStaging` passes, so R8 runs clean and the keep rules parse, and the
device pass of 15 September exercised playback, recording and Cast without a
crash on launch. Subject to the staging-versus-debug caveat in section 6, this
item is closed: Cast reaching a receiver is exactly the signal that
`CastOptionsProvider` survived minification.

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

## 8. Still open

- **Toolchain and dependency bumps — the next branch, and its own release.**
  Media3 1.3.1 → 1.11.x, Cast 21.5.0 → 22.x, then AGP 8.11.1 → 9.4.0 with
  Gradle 8.13 → 9.6.0. One commit each, in that order, AGP last: it is a
  migration with breaking changes, not a version bump, and it is what lets the
  bundled JBR 25 be used directly instead of pinning the Gradle JDK to 17 or 21
  by hand on every machine.

  **That branch ends in its own release commit: 2.2.0, `versionCode` 4.** The
  pattern for this repo is one release per branch, and it is deliberate — a
  dependency branch is exactly the kind that looks inert and is not. Media3 owns
  playback and the session the notification and Cast both hang off; a regression
  there surfaces on a device, not in a build log. A separate version number is
  what lets a tester say *which* build broke, and lets the branch be reverted as
  one thing.

  Nothing about the app's behaviour is meant to change, so minor rather than
  major — the same reasoning as 2.1.0. The device pass in section 6 has to be
  repeated in full on that branch, Cast included, because Media3 and Cast are
  precisely what it exercises.
- **No release keystore or signing config.** `staging` covers testing; a real
  release still needs one.
- **The JDK is pinned by the toolchain, not by choice.** Gradle 8.13 tops out at
  Java 24; Android Studio now bundles JBR 25, so the Gradle JDK must be set to 17
  or 21 by hand on every machine. The AGP 9 / Gradle 9.6 upgrade above is what
  removes that.
- **The 15 September device pass did not cover the new visual language.** It
  covered behaviour — playback, recording, Cast, rotation, the full-screen
  player. Still unlooked-at on a real screen: every screen in dark mode, the
  generated artwork fallback, and the app with the device's animation scale set
  to off.
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
