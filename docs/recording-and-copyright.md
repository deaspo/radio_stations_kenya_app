# Recording third-party broadcasts — the decision to make

This is the one open item with no technical answer. It needs a call from you,
because the liability is yours. What follows is the shape of the problem and a
recommendation, not legal advice — and I could not verify Google Play's current
Intellectual Property policy wording from here, so check the Play Policy Center
yourself rather than taking a summary on trust.

## What the app actually does today

Four facts, all verifiable in the code:

1. `RecordingService` copies a broadcaster's live stream to a file. The app does
   not own that content and says so on the onboarding agreement page.
2. Recordings land in `Music/Radio Diaspora` and are indexed in MediaStore.
   **Every app on the device can therefore see and share them.** Any file
   manager will do it.
3. `RecordingsActivity` also offers its own share action — `ACTION_SEND` with a
   content URI.
4. The onboarding agreement already states that recordings are for personal use
   only, and the user must accept it before reaching the app.

Point 2 is the one that matters most, and it cuts both ways. Removing the
in-app share button does **not** stop anyone redistributing a recording; it only
stops the app from offering that as a one-tap affordance. That is a difference in
posture, not capability — but posture is most of what a policy reviewer or a
rights holder is looking at.

## The distinction that matters

Recording a broadcast you are lawfully receiving, to listen to later yourself, is
time-shifting. It is treated permissively in many jurisdictions. Redistributing
that recording is a different act, and no private-copying exception covers it.

The app currently supports both. The question is whether it should *offer* the
second one.

## Options

| | Change | Effect | Cost |
|---|---|---|---|
| **A** | Remove the in-app share action | The app's own affordances stop at "record and keep". Files remain the user's to do as they wish. | One button; ~5 lines |
| **B** | Keep share, add a confirmation dialog | Friction, and a record that the user was warned | Users click through warnings; little real effect |
| **C** | Keep share as-is | Nothing | The app advertises redistribution as a feature |
| **D** | Store recordings app-privately (`getExternalFilesDir`) instead of in `Music/` | Other apps cannot see them at all | Files disappear on uninstall and no music player can open them — which defeats the point of recording |

## Decision: B — keep share, behind a confirmation

**Chosen by Polycarp Okock, 13 September 2026. Implemented.**

`RecordingsActivity.share()` now shows a dialog naming the issue — that this is a
broadcast the user does not own and sharing it may infringe the broadcaster's
copyright — before opening the chooser. It is shown every time; a "don't ask
again" option would undo the only thing it is for.

What this achieves and what it does not, stated plainly so nobody is surprised
later: the recordings are indexed in `Music/`, so any file manager can still
share them. The dialog does not prevent redistribution. It stops the app
*presenting* redistribution as a frictionless feature, and puts the position in
front of the person doing it.

The recommendation below is kept for the record.

## Recommendation was: A

Remove the share action, keep everything else. It costs one button and almost no
user value — the recordings are in the device's music library, so anyone who
genuinely wants to send one still can, through the file manager or their music
app. What it buys is a defensible position: this app records for personal
listening, and does not present sharing other people's broadcasts as a feature.

D is the only option that meaningfully changes what is possible, and it is the
wrong trade: "save the recording so it vanishes when you reinstall and no music
app can play it" is not the feature that was asked for.

## Two related things worth settling at the same time

- **The licence.** The project is CC BY-NC-SA 4.0. Creative Commons explicitly
  advises against CC licences for software, and NonCommercial rules out ads,
  sponsorship and paid placement. If this is ever meant to earn anything,
  relicense now — GPL-3.0 or MPL-2.0 match the intent better — because
  relicensing gets harder with every outside contributor.
- **Ask.** A short message to the larger broadcasters asking whether they object
  to an app that streams and time-shifts their public broadcast costs nothing. A
  "no objection" reply is worth more than any wording in an agreement screen, and
  a refusal tells you something you would rather learn now.

## If you later want option A after all

Delete the share button from `res/layout/item_recording.xml`, the
`recordingShare` binding in `RecordingsActivity`, `share()` and
`startShareChooser()`, and the `recordings_share*` strings. A two-minute change.
