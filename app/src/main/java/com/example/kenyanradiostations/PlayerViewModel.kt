// Intentionally empty.
//
// This held an AndroidViewModel that built a second ExoPlayer in the activity
// process while RadioService already owned the real one - two players, double
// buffering, double audio focus. Nothing referenced it. The MediaController in
// MainActivity is the only handle on playback.
//
// Emptied rather than deleted only because the tooling used here cannot remove
// files; delete PlayerViewModel.kt when convenient.
