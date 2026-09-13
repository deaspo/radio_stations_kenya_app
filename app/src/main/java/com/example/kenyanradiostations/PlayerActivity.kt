package com.example.kenyanradiostations

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.example.kenyanradiostations.databinding.ActivityPlayerBinding
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The full-screen player.
 *
 * It owns nothing: playback lives in [RadioService] and is reached through a
 * [MediaController], exactly as [MainActivity] reaches it. That is what lets
 * this screen be opened, rotated and closed without interrupting a single byte
 * of audio, and why it simply finishes when the session goes idle - there is
 * nothing left to show.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var settings: Settings
    private lateinit var favourites: FavouritesStore

    private var station: RadioStation? = null
    private var details: StationDetails? = null

    private var mediaController: MediaController? = null

    /**
     * Whether the session has ever reported an active state while this screen
     * has been open. Until it has, an idle controller means "not started yet"
     * - the station may still be resolving, or the audio may be on a Cast
     * device - and closing on it would make the screen unopenable.
     */
    private var hasBeenActive = false

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                toggleRecording()
            } else {
                toast(getString(R.string.recording_needs_storage_permission))
            }
        }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            mediaController?.let { render(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mediaController?.let { render(it) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            applyMetadata(mediaMetadata)
        }
    }

    // region Lifecycle

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_up,
                R.anim.fade_out
            )
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.fade_in,
                R.anim.slide_out_down
            )
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)
        favourites = FavouritesStore(this)

        station = intent.getParcelableExtraCompat(EXTRA_STATION)
        details = intent.getParcelableExtraCompat(EXTRA_DETAILS)

        applyWindowInsets()
        bindControls()
        showStation()
        renderFavourite()
        renderRecordingState(RecordingService.state.value)
        observeRecording()
    }

    override fun onStart() {
        super.onStart()
        initializeController()
    }

    override fun onStop() {
        super.onStop()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Covers the collapse button, the system back gesture and the automatic
     * close when playback stops, so all three slide away the same way.
     */
    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_down)
        }
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    // endregion

    // region Controls

    @OptIn(UnstableApi::class)
    private fun bindControls() {
        CastButtonFactory.setUpMediaRouteButton(applicationContext, binding.castButton)

        binding.buttonCollapse.setOnClickListener { finish() }

        binding.buttonPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
        }

        binding.buttonStop.setOnClickListener {
            mediaController?.stop()
            finish()
        }

        binding.buttonFavourite.setOnClickListener {
            val id = station?.id ?: return@setOnClickListener
            favourites.toggle(id)
            renderFavourite()
        }

        binding.buttonRecord.setOnClickListener { toggleRecording() }
    }

    private fun showStation() {
        val current = station
        binding.stationName.text = current?.name.orEmpty()
        binding.stationSignal.text = details?.signal.orEmpty()
            .ifEmpty { getString(R.string.player_connecting) }
        binding.artwork.load(details?.logoUrl?.ifEmpty { null } ?: current?.logoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_radio_icon)
            error(R.drawable.ic_radio_icon)
        }
        binding.buttonFavourite.isEnabled = current != null
        binding.buttonRecord.isEnabled = details?.recordableUrl != null
    }

    private fun renderFavourite() {
        val id = station?.id
        val isFavourite = id != null && id in favourites.ids()
        binding.buttonFavourite.setIconResource(
            if (isFavourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        val name = station?.name.orEmpty()
        binding.buttonFavourite.contentDescription = getString(
            if (isFavourite) R.string.remove_from_favourites else R.string.add_to_favourites,
            name
        )
    }

    // endregion

    // region Session

    @OptIn(UnstableApi::class)
    private fun initializeController() {
        val token = SessionToken(this, ComponentName(this, RadioService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(this, token).buildAsync()

        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            val controller = runCatching { future.get() }
                .onFailure { Log.e(TAG, "Could not connect to the playback session", it) }
                .getOrNull() ?: return@addListener

            mediaController = controller
            controller.addListener(playerListener)
            // A listener added after the connection only hears about changes,
            // so the state has to be read once by hand.
            render(controller)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun render(controller: MediaController) {
        val state = controller.playbackState
        val active = state != Player.STATE_IDLE && state != Player.STATE_ENDED

        if (active) {
            hasBeenActive = true
        } else if (hasBeenActive) {
            // Playback has stopped; there is no full-screen player left to be.
            finish()
            return
        }

        binding.buffering.visibility =
            if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE

        val playing = controller.isPlaying
        binding.buttonPlayPause.isEnabled = controller.currentMediaItem != null
        binding.buttonPlayPause.setIconResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.buttonPlayPause.contentDescription =
            getString(if (playing) R.string.player_pause else R.string.player_play)

        binding.liveBadge.visibility = if (playing) View.VISIBLE else View.GONE

        applyMetadata(controller.mediaMetadata)

        if (settings.keepScreenOn && playing) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyMetadata(metadata: MediaMetadata) {
        metadata.title?.let { binding.stationName.text = it }
        metadata.artist?.let { binding.stationSignal.text = it }
        metadata.artworkUri?.let { uri ->
            binding.artwork.load(uri) {
                crossfade(true)
                placeholder(R.drawable.ic_radio_icon)
                error(R.drawable.ic_radio_icon)
            }
        }
    }

    // endregion

    // region Recording

    private fun observeRecording() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    renderRecordingState(RecordingService.state.value)
                    delay(RECORDING_TICK_MS)
                }
            }
        }
    }

    private fun renderRecordingState(state: RecordingState) {
        when (state) {
            is RecordingState.Active -> {
                val elapsed = SystemClock.elapsedRealtime() - state.startedAtElapsedRealtime
                binding.recordingStatus.visibility = View.VISIBLE
                binding.recordingStatus.text =
                    getString(R.string.recording_in_progress, Elapsed.format(elapsed))
                binding.buttonRecord.setIconResource(R.drawable.ic_stop)
                binding.buttonRecord.contentDescription = getString(R.string.recording_stop)
                binding.buttonRecord.isEnabled = true
            }

            RecordingState.Idle -> {
                binding.recordingStatus.visibility = View.GONE
                binding.buttonRecord.setIconResource(R.drawable.ic_record)
                binding.buttonRecord.contentDescription = getString(R.string.recording_start)
                binding.buttonRecord.isEnabled = details?.recordableUrl != null
            }
        }
    }

    private fun toggleRecording() {
        when (val outcome = RecordingLauncher.toggle(this, station, details, settings)) {
            is RecordingLauncher.Outcome.Started ->
                toast(getString(R.string.recording_started, outcome.stationName))

            RecordingLauncher.Outcome.Stopped -> Unit

            is RecordingLauncher.Outcome.Blocked -> toast(getString(outcome.messageRes))

            RecordingLauncher.Outcome.NeedsStoragePermission ->
                requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        renderRecordingState(RecordingService.state.value)
    }

    // endregion

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(
        name: String
    ): T? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        getParcelableExtra(name) as? T
    }

    companion object {
        private const val TAG = "RadioApp"
        private const val RECORDING_TICK_MS = 1_000L
        private const val EXTRA_STATION = "extra_station"
        private const val EXTRA_DETAILS = "extra_details"

        fun intent(
            context: Context,
            station: RadioStation,
            details: StationDetails?
        ): Intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_STATION, station)
            .putExtra(EXTRA_DETAILS, details)
    }
}
