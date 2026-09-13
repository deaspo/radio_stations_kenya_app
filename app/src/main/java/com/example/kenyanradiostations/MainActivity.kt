package com.example.kenyanradiostations

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.example.kenyanradiostations.databinding.ActivityMainBinding
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL
import java.util.Locale

/** Which of the mutually exclusive list states is on screen. */
private enum class ListState { LOADING, CONTENT, EMPTY, ERROR }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var favourites: FavouritesStore
    private lateinit var settings: Settings

    private val adapter = StationAdapter(
        onItemClick = { station -> playStation(station) },
        onToggleFavourite = { station -> toggleFavourite(station) }
    )

    private var allStations: List<RadioStation> = emptyList()
    private var searchTerm: String = ""
    private var favouritesOnly: Boolean = false

    /** The station the user last asked for, and its resolved stream URLs. */
    private var currentStation: RadioStation? = null
    private var currentDetails: StationDetails? = null

    /** Set when a station is tapped before the media session has connected. */
    private var pendingPlayback: Pair<RadioStation, StationDetails>? = null

    private var hasResumedLastStation = false

    private var mediaController: MediaController? = null
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private val sessionManagerListener = SessionManagerListenerImpl()

    private var isDataReady = false

    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Back online while the error state is up: reload rather than make
            // the user go and find the retry button.
            runOnUiThread {
                if (!isFinishing && allStations.isEmpty()) fetchStations()
            }
        }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                toast(getString(R.string.error_notification_permission))
            }
        }

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startRecording()
            } else {
                toast(getString(R.string.recording_needs_storage_permission))
            }
        }

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            mediaController?.let { renderPlayerState(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateKeepScreenOn(isPlaying)
            mediaController?.let { renderTransport(it) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            applyMetadata(mediaMetadata)
        }
    }

    // region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !isDataReady }

        super.onCreate(savedInstanceState)

        val appPrefs = getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        if (!appPrefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        analytics = Firebase.analytics
        favourites = FavouritesStore(this)
        settings = Settings(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        applyWindowInsets()
        setUpList()
        setUpFilters()
        setUpPlayerSheet()
        observeRecording()

        askNotificationPermission()
        setUpCast()

        restore(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!this::binding.isInitialized) return
        // Keeping the catalogue across a rotation; without this the site was
        // re-scraped every time the device was turned.
        outState.putParcelableArrayList(STATE_STATIONS, ArrayList(allStations))
        outState.putParcelable(STATE_STATION, currentStation)
        outState.putParcelable(STATE_DETAILS, currentDetails)
        outState.putBoolean(STATE_RESUMED, hasResumedLastStation)
    }

    @Suppress("DEPRECATION")
    private fun restore(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            hasResumedLastStation = savedInstanceState.getBoolean(STATE_RESUMED, false)
            currentStation = savedInstanceState.getParcelable(STATE_STATION)
            currentDetails = savedInstanceState.getParcelable(STATE_DETAILS)
            val saved = savedInstanceState
                .getParcelableArrayList<RadioStation>(STATE_STATIONS)
                .orEmpty()
            if (saved.isNotEmpty()) {
                allStations = saved
                isDataReady = true
                applyFilters()
                return
            }
        }
        fetchStations()
    }

    override fun onStart() {
        super.onStart()
        initializeController()
        registerNetworkCallback()
    }

    @OptIn(UnstableApi::class)
    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
    }

    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.addSessionManagerListener(
            sessionManagerListener, CastSession::class.java
        )
        // The full-screen player can favourite a station and start or stop a
        // recording, so neither can be assumed unchanged on the way back.
        adapter.setFavourites(favourites.ids())
        renderRecordingState(RecordingService.state.value)
    }

    override fun onPause() {
        super.onPause()
        castContext?.sessionManager?.removeSessionManagerListener(
            sessionManagerListener, CastSession::class.java
        )
    }

    // endregion

    // region Window insets

    /**
     * targetSdk 36 means Android 15 and newer always lay the window out edge to
     * edge, so the app bar would sit under the status bar and the mini player
     * under the gesture bar unless the insets are consumed here.
     */
    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val night = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val listReserve = resources.getDimensionPixelSize(R.dimen.player_overlay_reserved)
        val sheetPadding = resources.getDimensionPixelSize(R.dimen.player_sheet_padding_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = bars.top)
            binding.recyclerView.updatePadding(bottom = bars.bottom + listReserve)
            binding.playerControlsContainer.playerContent.updatePadding(
                bottom = bars.bottom + sheetPadding
            )
            insets
        }
    }

    // endregion

    // region List, search and filters

    private fun setUpList() {
        binding.recyclerView.layoutManager = GridLayoutManager(this, spanCount())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)

        binding.swipeRefresh.setOnRefreshListener { fetchStations(isRefresh = true) }
        binding.buttonRetry.setOnClickListener { fetchStations() }
    }

    /** Tiles are sized by content, not by a hard-coded column count. */
    private fun spanCount(): Int {
        val minWidth = resources.getDimensionPixelSize(R.dimen.station_tile_min_width)
        val gutter = resources.getDimensionPixelSize(R.dimen.grid_gutter)
        val usable = resources.displayMetrics.widthPixels - (2 * gutter)
        return (usable / minWidth).coerceAtLeast(2)
    }

    private fun setUpFilters() {
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            searchTerm = text?.toString().orEmpty()
            applyFilters()
        }
        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            favouritesOnly = checkedIds.contains(R.id.chip_favourites)
            applyFilters()
        }
    }

    private fun applyFilters() {
        val term = searchTerm.trim().lowercase(Locale.getDefault())
        val favouriteIds = favourites.ids()

        val filtered = allStations.filter { station ->
            val matchesFilter = !favouritesOnly || favouriteIds.contains(station.id)
            val matchesTerm = term.isEmpty() ||
                station.name.lowercase(Locale.getDefault()).contains(term)
            matchesFilter && matchesTerm
        }

        adapter.setFavourites(favouriteIds)
        adapter.submitList(filtered)

        if (filtered.isEmpty()) showEmptyState() else setState(ListState.CONTENT)
    }

    private fun toggleFavourite(station: RadioStation) {
        adapter.setFavourites(favourites.toggle(station.id))
        if (favouritesOnly) applyFilters()
    }

    private fun showEmptyState() {
        val noFavouritesYet = favouritesOnly && searchTerm.isBlank()
        binding.emptyIcon.setImageResource(
            if (noFavouritesYet) R.drawable.ic_favorite_border else R.drawable.ic_search
        )
        binding.emptyTitle.setText(
            if (noFavouritesYet) R.string.empty_favourites_title else R.string.empty_title
        )
        binding.emptyMessage.setText(
            if (noFavouritesYet) R.string.empty_favourites_message else R.string.empty_message
        )
        setState(ListState.EMPTY)
    }

    /** "You are offline" and "the site is down" are different messages. */
    private fun showErrorState(offline: Boolean) {
        binding.errorTitle.setText(
            if (offline) R.string.offline_title else R.string.error_title
        )
        binding.errorMessage.setText(
            if (offline) R.string.offline_message else R.string.error_message
        )
        setState(ListState.ERROR)
    }

    private fun showRetrySnackbar(messageRes: Int, retry: () -> Unit) {
        val snackbar = Snackbar.make(binding.content, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.error_retry) { retry() }
        // Keep it clear of the mini player when one is showing.
        if (binding.playerControlsContainer.root.visibility == View.VISIBLE) {
            snackbar.setAnchorView(binding.playerControlsContainer.root)
        }
        snackbar.show()
    }

    private fun setState(state: ListState) {
        binding.progressIndicator.visibility =
            if (state == ListState.LOADING) View.VISIBLE else View.GONE
        binding.stateEmpty.visibility =
            if (state == ListState.EMPTY) View.VISIBLE else View.GONE
        binding.stateError.visibility =
            if (state == ListState.ERROR) View.VISIBLE else View.GONE
    }

    // endregion

    // region Loading stations

    private fun fetchStations(isRefresh: Boolean = false) {
        if (!isRefresh) setState(ListState.LOADING)

        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { scrapeStations() } }

            binding.swipeRefresh.isRefreshing = false
            // Release the splash screen whatever happened, so a network failure
            // cannot strand the user on it.
            isDataReady = true

            result
                .onSuccess { stations ->
                    allStations = stations
                    applyFilters()
                    maybeResumeLastStation()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load stations", error)
                    val offline = !Connectivity.isOnline(this@MainActivity)
                    if (allStations.isEmpty()) {
                        showErrorState(offline)
                    } else {
                        // The list on screen is still usable, so this is a
                        // passing message rather than a whole error screen.
                        showRetrySnackbar(
                            if (offline) R.string.offline_message else R.string.error_message
                        ) { fetchStations() }
                    }
                }
        }
    }

    /** Blocking; always called on [Dispatchers.IO]. */
    private fun scrapeStations(): List<RadioStation> {
        val html = Jsoup.connect(STATIONS_URL)
            .timeout(NETWORK_TIMEOUT_MS)
            .execute()
            .body()
        return StationParser.parseStations(html)
    }

    /** Blocking; always called on [Dispatchers.IO]. */
    private fun loadStationDetails(station: RadioStation): StationDetails? {
        val json = URL(STREAM_API_PREFIX + station.id).openStream()
            .bufferedReader()
            .use { it.readText() }
        return StationParser.parseStationDetails(json, station)
    }

    // endregion

    // region Playback

    private fun maybeResumeLastStation() {
        if (hasResumedLastStation || !settings.resumeLastStation) return
        if (mediaController?.isPlaying == true) return
        val last = settings.lastStation() ?: return
        hasResumedLastStation = true
        // Prefer the freshly scraped entry: its logo URL may have changed.
        val station = allStations.firstOrNull { it.id == last.id } ?: last
        playStation(station)
    }

    private fun playStation(station: RadioStation) {
        if (!Connectivity.isOnline(this)) {
            showRetrySnackbar(R.string.offline_message) { playStation(station) }
            return
        }
        if (settings.wifiOnly && !Connectivity.isUnmetered(this)) {
            toast(getString(R.string.error_wifi_only))
            return
        }

        currentStation = station
        currentDetails = null
        settings.rememberLastStation(station)
        adapter.setPlayingStation(station.id)
        showPlayerSheet(station)
        renderRecordingState(RecordingService.state.value)

        lifecycleScope.launch {
            val details = runCatching {
                withContext(Dispatchers.IO) { loadStationDetails(station) }
            }.getOrElse { error ->
                Log.e(TAG, "Failed to resolve stream for ${station.id}", error)
                null
            }

            if (details == null) {
                if (currentStation?.id == station.id) {
                    clearPlaybackUi()
                }
                val offline = !Connectivity.isOnline(this@MainActivity)
                showRetrySnackbar(
                    if (offline) R.string.offline_message else R.string.error_stream_unavailable
                ) { playStation(station) }
                return@launch
            }

            currentDetails = details
            renderRecordingState(RecordingService.state.value)
            startPlayback(station, details)
        }
    }

    private fun showPlayerSheet(station: RadioStation) {
        val player = binding.playerControlsContainer
        player.root.visibility = View.VISIBLE
        player.stationNamePlayer.text = station.name
        player.stationSignalPlayer.setText(R.string.player_connecting)
        player.stationLogoPlayer.load(station.logoUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_radio_icon)
            error(R.drawable.ic_radio_icon)
        }
    }

    private fun clearPlaybackUi() {
        currentStation = null
        currentDetails = null
        pendingPlayback = null
        adapter.setPlayingStation(null)
        binding.playerControlsContainer.root.visibility = View.GONE
        updateKeepScreenOn(false)
    }

    private fun startPlayback(station: RadioStation, details: StationDetails) {
        val session = castSession
        if (session != null && session.isConnected) {
            mediaController?.volume = 0f
            session.remoteMediaClient?.load(details.toCastMediaInfo(), true)
            return
        }

        val controller = mediaController
        if (controller == null) {
            // Connecting to the session is asynchronous, so a station tapped in
            // the first moments after launch used to be silently dropped.
            pendingPlayback = station to details
            return
        }

        controller.volume = 1f
        controller.setMediaItem(details.toMediaItem(station))
        controller.prepare()
        controller.play()
    }

    @OptIn(UnstableApi::class)
    private fun setUpPlayerSheet() {
        val player = binding.playerControlsContainer

        CastButtonFactory.setUpMediaRouteButton(applicationContext, player.castButton)

        player.closeButton.setOnClickListener {
            mediaController?.stop()
            clearPlaybackUi()
        }
        player.recordButton.setOnClickListener { onRecordClicked() }

        player.buttonPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
        }

        // The whole card opens the full-screen player; the chevron is there so
        // that is discoverable, and so TalkBack has something to announce.
        player.root.setOnClickListener { openFullScreenPlayer() }
        player.buttonExpand.setOnClickListener { openFullScreenPlayer() }
    }

    @Suppress("DEPRECATION")
    private fun openFullScreenPlayer() {
        val station = currentStation ?: return
        startActivity(PlayerActivity.intent(this, station, currentDetails))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // From Android 14 the started activity declares its own transition.
            overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out)
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeController() {
        val sessionToken = SessionToken(this, ComponentName(this, RadioService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            if (isFinishing || isDestroyed) return@addListener
            val controller = runCatching { controllerFuture.get() }
                .onFailure { Log.e(TAG, "Could not connect to the playback session", it) }
                .getOrNull() ?: return@addListener

            mediaController = controller
            controller.addListener(playerListener)

            // A listener added after connection only hears about *changes*, so
            // the current state has to be read once by hand. Without this the
            // mini player stayed hidden after a rotation while the notification
            // carried on playing.
            renderPlayerState(controller)

            pendingPlayback?.let { (station, details) ->
                pendingPlayback = null
                startPlayback(station, details)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Brings the whole player UI in line with whatever the session is doing. */
    private fun renderPlayerState(controller: MediaController) {
        val playbackState = controller.playbackState
        val active = playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

        binding.playerControlsContainer.root.visibility = if (active) View.VISIBLE else View.GONE

        if (active) {
            val metadata = controller.mediaMetadata
            applyMetadata(metadata)
            val extras = metadata.extras
            val stationId = extras?.getString(EXTRA_STATION_ID)
            adapter.setPlayingStation(stationId)
            if (currentStation == null && stationId != null) {
                currentStation = RadioStation(
                    id = stationId,
                    name = metadata.title?.toString().orEmpty(),
                    logoUrl = extras?.getString(EXTRA_STATION_LOGO).orEmpty()
                )
            }
        } else {
            adapter.setPlayingStation(null)
        }

        renderTransport(controller)
        updateKeepScreenOn(controller.isPlaying)
        renderRecordingState(RecordingService.state.value)
    }

    /** The play/pause button and the buffering ring in the mini player. */
    private fun renderTransport(controller: MediaController) {
        val player = binding.playerControlsContainer
        val playing = controller.isPlaying

        player.buffering.visibility =
            if (controller.playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
        player.buttonPlayPause.isEnabled = controller.currentMediaItem != null
        player.buttonPlayPause.setIconResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
        player.buttonPlayPause.contentDescription =
            getString(if (playing) R.string.player_pause else R.string.player_play)
        player.stationSignalPlayer.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (playing) R.drawable.bg_live_dot else 0, 0, 0, 0
        )
    }

    private fun applyMetadata(metadata: androidx.media3.common.MediaMetadata) {
        val player = binding.playerControlsContainer
        metadata.title?.let { player.stationNamePlayer.text = it }
        player.stationSignalPlayer.text = metadata.artist ?: ""
        metadata.artworkUri?.let { uri ->
            player.stationLogoPlayer.load(uri) {
                crossfade(true)
                placeholder(R.drawable.ic_radio_icon)
                error(R.drawable.ic_radio_icon)
            }
        }
    }

    private fun updateKeepScreenOn(isPlaying: Boolean) {
        if (settings.keepScreenOn && isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // endregion

    // region Recording

    private fun observeRecording() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Doubles as the ticker for the elapsed-time label.
                while (true) {
                    renderRecordingState(RecordingService.state.value)
                    delay(RECORDING_TICK_MS)
                }
            }
        }
    }

    private fun renderRecordingState(state: RecordingState) {
        if (!this::binding.isInitialized) return
        val player = binding.playerControlsContainer

        when (state) {
            is RecordingState.Active -> {
                val elapsed = SystemClock.elapsedRealtime() - state.startedAtElapsedRealtime
                player.recordingStatus.visibility = View.VISIBLE
                player.recordingStatus.text = getString(
                    R.string.recording_in_progress,
                    Elapsed.format(elapsed)
                )
                player.recordButton.setIconResource(R.drawable.ic_stop)
                player.recordButton.contentDescription = getString(R.string.recording_stop)
                player.recordButton.isEnabled = true
            }

            RecordingState.Idle -> {
                player.recordingStatus.visibility = View.GONE
                player.recordButton.setIconResource(R.drawable.ic_record)
                player.recordButton.contentDescription = getString(R.string.recording_start)
                player.recordButton.isEnabled = currentDetails?.recordableUrl != null
            }
        }
    }

    private fun onRecordClicked() {
        when (val outcome = RecordingLauncher.toggle(this, currentStation, currentDetails, settings)) {
            is RecordingLauncher.Outcome.Started ->
                toast(getString(R.string.recording_started, outcome.stationName))

            RecordingLauncher.Outcome.Stopped -> Unit

            is RecordingLauncher.Outcome.Blocked -> toast(getString(outcome.messageRes))

            RecordingLauncher.Outcome.NeedsStoragePermission ->
                requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        renderRecordingState(RecordingService.state.value)
    }

    /** Called once the storage permission has been granted on API 28 and below. */
    private fun startRecording() = onRecordClicked()

    // endregion

    // region Cast

    private fun setUpCast() {
        castContext = runCatching { CastContext.getSharedInstance(this) }
            .onFailure { Log.w(TAG, "Cast unavailable on this device", it) }
            .getOrNull()
        castSession = castContext?.sessionManager?.currentCastSession
    }

    private fun StationDetails.toMediaItem(station: RadioStation): MediaItem = MediaItem.Builder()
        .setUri(streamUrl)
        .setMediaId(streamUrl)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(signal)
                .setArtworkUri(logoUrl.takeIf { it.isNotEmpty() }?.let(Uri::parse))
                // Carried on the session so the activity can work out which
                // station is playing after being recreated.
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_STATION_ID, station.id)
                        putString(EXTRA_STATION_LOGO, station.logoUrl)
                    }
                )
                .build()
        )
        .build()

    private fun StationDetails.toCastMediaInfo(): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, signal)
            if (logoUrl.isNotEmpty()) addImage(WebImage(Uri.parse(logoUrl)))
        }
        return MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("audio/aac")
            .setMetadata(metadata)
            .build()
    }

    private inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {

        private fun transferToRemotePlayer(session: CastSession) {
            if (mediaController?.isPlaying != true) return
            val currentItem = mediaController?.currentMediaItem ?: return

            mediaController?.volume = 0f

            val metadata = currentItem.mediaMetadata
            val castMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, metadata.title?.toString().orEmpty())
                putString(MediaMetadata.KEY_SUBTITLE, metadata.artist?.toString().orEmpty())
                metadata.artworkUri?.let { addImage(WebImage(it)) }
            }

            val mediaInfo = MediaInfo.Builder(currentItem.mediaId)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType("audio/aac")
                .setMetadata(castMetadata)
                .build()

            session.remoteMediaClient?.load(mediaInfo, false)?.setResultCallback { result ->
                if (result.status.isSuccess) {
                    session.remoteMediaClient?.play()
                }
            }
        }

        private fun transferToLocalMediaPlayer(session: CastSession) {
            mediaController?.volume = 1f
            val remoteMediaClient = session.remoteMediaClient ?: return
            val playingRemotely = remoteMediaClient.isPlaying || remoteMediaClient.isBuffering
            if (!playingRemotely) return

            val mediaInfo = remoteMediaClient.mediaInfo ?: return
            val castMetadata = mediaInfo.metadata
            val localMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(castMetadata?.getString(MediaMetadata.KEY_TITLE))
                .setArtist(castMetadata?.getString(MediaMetadata.KEY_SUBTITLE))
                .setArtworkUri(castMetadata?.images?.firstOrNull()?.url)
                .build()

            val controller = mediaController ?: return
            controller.setMediaItem(
                MediaItem.Builder()
                    .setUri(mediaInfo.contentId)
                    .setMediaId(mediaInfo.contentId)
                    .setMediaMetadata(localMetadata)
                    .build()
            )
            controller.prepare()
            controller.play()
        }

        private fun showCastDeviceName(session: CastSession) {
            val deviceName = session.castDevice?.friendlyName
            val label = binding.playerControlsContainer.castDeviceName
            if (deviceName.isNullOrEmpty()) {
                label.visibility = View.GONE
            } else {
                label.text = getString(R.string.player_casting_to, deviceName)
                label.visibility = View.VISIBLE
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            transferToRemotePlayer(session)
            castSession = session
            showCastDeviceName(session)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            transferToRemotePlayer(session)
            castSession = session
            showCastDeviceName(session)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            transferToLocalMediaPlayer(session)
            if (session == castSession) castSession = null
            binding.playerControlsContainer.castDeviceName.visibility = View.GONE
        }

        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    }

    // endregion

    // region Menu, permissions and helpers

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            fetchStations()
            true
        }

        R.id.action_recordings -> {
            startActivity(Intent(this, RecordingsActivity::class.java))
            true
        }

        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        R.id.action_about -> {
            startActivity(Intent(this, AboutActivity::class.java))
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val manager = getSystemService<ConnectivityManager>() ?: return
        runCatching { manager.registerDefaultNetworkCallback(networkCallback) }
            .onSuccess { networkCallbackRegistered = true }
            .onFailure { Log.w(TAG, "Could not watch for connectivity changes", it) }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        val manager = getSystemService<ConnectivityManager>() ?: return
        runCatching { manager.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // endregion

    private companion object {
        const val TAG = "RadioApp"
        const val APP_PREFS = "app_prefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val STATIONS_URL = "https://radio.or.ke/"
        const val STREAM_API_PREFIX = "https://api.instant.audio/data/streams/81/"
        const val NETWORK_TIMEOUT_MS = 15_000
        const val RECORDING_TICK_MS = 1_000L

        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_STATION_LOGO = "station_logo"

        const val STATE_STATIONS = "state_stations"
        const val STATE_STATION = "state_station"
        const val STATE_DETAILS = "state_details"
        const val STATE_RESUMED = "state_resumed"
    }
}
