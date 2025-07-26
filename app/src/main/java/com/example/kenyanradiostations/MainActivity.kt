package com.example.kenyanradiostations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.example.kenyanradiostations.databinding.ActivityMainBinding
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var audioManager: AudioManager
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private val sessionManagerListener = SessionManagerListenerImpl()
    private var lastPlayedStation: RadioStation? = null

    private var localPlayer: ExoPlayer? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("RadioApp", "Notification permission granted.")
            } else {
                Toast.makeText(
                    this,
                    "Notification permission is required for background playback.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val remotePlayerCallback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updatePlayerUiState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        askNotificationPermission()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupLocalPlayer()
        setupCast()

        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        fetchStations()

        setupPlayerControls()
    }

    private fun setupLocalPlayer() {
        localPlayer = ExoPlayer.Builder(this).build()
        localPlayer?.addListener(playerListener)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    private fun updatePlayerUiState() {
        val isPlaying: Boolean
        if (castSession?.isConnected == true) {
            val remoteState = castSession?.remoteMediaClient?.playerState
            isPlaying = (remoteState == MediaStatus.PLAYER_STATE_PLAYING || remoteState == MediaStatus.PLAYER_STATE_BUFFERING)
        } else {
            isPlaying = localPlayer?.isPlaying ?: false
        }

        if (isPlaying) {
            binding.playerControlsContainer.playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            binding.playerControlsContainer.playPauseButton.setImageResource(R.drawable.ic_play)
        }

        // Ensure the controls are visible if we have an active session
        val localIsActive = localPlayer?.playbackState != Player.STATE_IDLE
        val remoteIsActive = castSession?.remoteMediaClient?.hasMediaSession() == true

        if (localIsActive || remoteIsActive) {
            binding.playerControlsContainer.root.visibility = View.VISIBLE
        } else {
            binding.playerControlsContainer.root.visibility = View.GONE
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
//            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
//                binding.playerControlsContainer.root.visibility = View.GONE
//            } else {
//                binding.playerControlsContainer.root.visibility = View.VISIBLE
//            }
            updatePlayerUiState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            // Update station info in the control view
            binding.playerControlsContainer.stationNamePlayer.text = mediaMetadata.title
            binding.playerControlsContainer.stationSignalPlayer.text = mediaMetadata.artist
            binding.playerControlsContainer.stationLogoPlayer.load(mediaMetadata.artworkUri) {
                placeholder(R.drawable.ic_radio_icon)
            }
        }
    }

    private fun setupCast() {
        castContext = CastContext.getSharedInstance(this)
        castSession = castContext?.sessionManager?.currentCastSession
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayerControls() {
        // Link the PlayerControlView directly to our local player instance
//        binding.playerControlsContainer.playerView.player = localPlayer
//
//        binding.playerControlsContainer.closeButton.setOnClickListener {
//            localPlayer?.stop()
//            binding.playerControlsContainer.root.visibility = View.GONE
//        }
        binding.playerControlsContainer.playPauseButton.setOnClickListener {
            if (castSession?.isConnected == true) {
                // Control the remote player
                val remoteClient = castSession?.remoteMediaClient
                if (remoteClient?.isPlaying == true) {
                    remoteClient.pause()
                } else {
                    remoteClient?.play()
                }
            } else {
                // Control the local player
                if (localPlayer?.isPlaying == true) {
                    localPlayer?.pause()
                } else {
                    lastPlayedStation?.let {
                        playStation(it)
                    }
                }
            }
        }

        binding.playerControlsContainer.closeButton.setOnClickListener {
            localPlayer?.stop()
            castSession?.remoteMediaClient?.stop()
            binding.playerControlsContainer.root.visibility = View.GONE
        }

        // Volume SeekBar setup
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.playerControlsContainer.volumeSeekbar.max = maxVolume
        binding.playerControlsContainer.volumeSeekbar.progress = currentVolume
        binding.playerControlsContainer.volumeSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showPlayerControls(details: StationDetails) {
        binding.playerControlsContainer.apply {
            stationNamePlayer.text = details.title
            stationSignalPlayer.text = details.signal
            stationLogoPlayer.load(details.logoUrl)
            root.visibility = View.VISIBLE
        }
    }

    private fun parseStationDetails(
        jsonString: String,
        originalStation: RadioStation
    ): StationDetails? {
        val jsonObject = JSONObject(jsonString)
        if (jsonObject.getBoolean("success")) {
            val result = jsonObject.getJSONObject("result")
            val stationInfo = result.getJSONObject("station")
            val streams = result.getJSONArray("streams")

            var hlsUrl: String? = null
            var aacUrl: String? = null
            var mp3Url: String? = null

            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                val url = stream.getString("url")
                if (url.isNotEmpty()) {
                    when (stream.getString("mediaType")) {
                        "HLS" -> hlsUrl = url
                        "AAC" -> aacUrl = url
                        "MP3" -> mp3Url = url
                    }
                }
            }
            val finalStreamUrl = hlsUrl ?: aacUrl ?: mp3Url

            return if (finalStreamUrl != null) {
                StationDetails(
                    title = stationInfo.getString("title"),
                    signal = stationInfo.getString("signal"),
                    logoUrl = originalStation.logoUrl, // Use logo from initial scrape
                    streamUrl = finalStreamUrl
                )
            } else {
                null
            }
        }
        return null
    }

    private fun fetchStations() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stations = mutableListOf<RadioStation>()
                Log.d("RadioApp", "Connecting to https://radio.or.ke/")
                val doc = Jsoup.connect("https://radio.or.ke/").get()

                val stationElements = doc.select("li[class^='item-']")
                Log.d(
                    "RadioApp",
                    "Found ${stationElements.size} station elements with new selector."
                )

                for (element in stationElements) {
                    val link = element.selectFirst("a")
                    val image = element.selectFirst("img")

                    if (link != null && image != null) {
                        // 2. GET THE NEW ID:
                        // The ID is now the part after the '#' in the href link.
                        // e.g., "https://radio.or.ke/#kameme" -> "kameme"
                        val id = link.attr("href").substringAfterLast('#', "")

                        // 3. GET THE NAME AND LOGO:
                        // The name is in the link's "title" attribute.
                        // The logo is in the image's "src" attribute.
                        val name = link.attr("title")
                        val logoUrl = image.attr("src")

                        if (id.isNotEmpty() && name.isNotEmpty()) {
                            stations.add(RadioStation(id, name, logoUrl))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.recyclerView.adapter = StationAdapter(stations) { station ->
                        // We will handle playback here
                        playStation(station)
                    }
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., no network)
                Log.e("RadioApp", "Failed to fetch stations", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    // Show an error message
                }
            }
        }
    }

    // Add this inner class to manage cast session
    private inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            // --- LOCAL TO CAST TRANSFER LOGIC ---
            val wasPlayingLocally = localPlayer?.isPlaying == true
            if (wasPlayingLocally) {
                val currentItem = localPlayer?.currentMediaItem ?: return
                Log.d("RadioApp", "Transferring playback from local player to cast device.")
                // Build MediaInfo for the cast device from the local player's current item
                val metadata = currentItem.mediaMetadata
                val castMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
                castMetadata.putString(MediaMetadata.KEY_TITLE, metadata.title?.toString()!!)
                castMetadata.putString(
                    MediaMetadata.KEY_SUBTITLE,
                    metadata.artist?.toString()!!
                ) // We use artist for the signal
                metadata.artworkUri?.let { castMetadata.addImage(WebImage(it)) }

                val mediaInfo = MediaInfo.Builder(currentItem.mediaId ?: "")
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("audio/aac")
                    .setMetadata(castMetadata)
                    .build()

                // Register the remote callback and load media
                session.remoteMediaClient?.registerCallback(remotePlayerCallback)
                session.remoteMediaClient?.load(mediaInfo, true)?.setResultCallback {
                    if (it.status.isSuccess) {
                        localPlayer?.stop() // Stop local audio but keep UI managed
                    }
                }

//                // Load the media on the cast device. On success, stop the local player.
//                session.remoteMediaClient?.load(mediaInfo, true)?.setResultCallback {
//                    if (it.status.isSuccess) {
//                        localPlayer?.stop()
//                    }
//                }
            }

            castSession = session
            session.remoteMediaClient?.registerCallback(remotePlayerCallback) // Also register here for new sessions
            invalidateOptionsMenu()
            updatePlayerUiState() // Update UI immediately
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            invalidateOptionsMenu()
        }

        // THIS IS THE NEWLY ADDED, REQUIRED METHOD
        override fun onSessionEnding(session: CastSession) {
            // Called just before the session ends.
            // You can add cleanup logic here if needed.
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            // --- CAST TO LOCAL TRANSFER LOGIC ---
            val remoteMediaClient = session.remoteMediaClient
            // Check if the remote client was playing just before disconnection
            val wasPlayingRemotely =
                remoteMediaClient?.isPlaying == true || remoteMediaClient?.isBuffering == true
            if (wasPlayingRemotely) {
                Log.d("RadioApp", "Transferring playback from cast device to local player.")

                val mediaInfo = remoteMediaClient?.mediaInfo ?: return
                // For live radio, starting from the beginning is usually desired
                // val position = remoteMediaClient.approximateStreamPosition

                // Build a MediaItem for the local player from the cast device's info
                val castMetadata = mediaInfo.metadata
                val localMetadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(castMetadata?.getString(MediaMetadata.KEY_TITLE))
                    .setArtist(castMetadata?.getString(MediaMetadata.KEY_SUBTITLE)) // We use artist for the signal
                    .setArtworkUri(castMetadata?.images?.firstOrNull()?.url)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(mediaInfo.contentId) // The contentId holds the stream URL
                    .setMediaId(mediaInfo.contentId)
                    .setMediaMetadata(localMetadata)
                    .build()

                localPlayer?.setMediaItem(mediaItem)
                localPlayer?.prepare()
                localPlayer?.play()
            }

            remoteMediaClient?.unregisterCallback(remotePlayerCallback) // Unregister the callback
            if (session == castSession) {
                castSession = null
            }
            invalidateOptionsMenu()
            updatePlayerUiState() // Update UI immediately
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            // Empty implementation is fine if you don't need to handle this
        }

        override fun onSessionStarting(session: CastSession) {
            // Empty implementation is fine
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            // Empty implementation is fine
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            // Empty implementation is fine
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            // Empty implementation is fine
        }
    }

    // Setup Cast button in the toolbar
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(
            applicationContext,
            menu,
            R.id.media_route_menu_item
        )
        return true
    }

    private fun playStation(station: RadioStation) {
        // Stop any current playback immediately to ensure a clean state.
        if (castSession?.isConnected == true) {
            // If casting, stop the remote player.
            castSession?.remoteMediaClient?.stop()
        } else {
            // If playing locally, stop the local player.
            localPlayer?.stop()
        }

        this.lastPlayedStation = station
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.instant.audio/data/streams/81/${station.id}"
                val jsonString = URL(apiUrl).openStream().bufferedReader().readText()
                val stationDetails = parseStationDetails(jsonString, station)

                withContext(Dispatchers.Main) {
                    if (stationDetails == null) {
                        Toast.makeText(this@MainActivity, "Could not find stream.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    showPlayerControls(stationDetails)

                    if (castSession != null && castSession!!.isConnected) {
                        localPlayer?.stop() // Stop local playback before casting
                        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
                        mediaMetadata.putString(MediaMetadata.KEY_TITLE, stationDetails.title)
                        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, stationDetails.signal)
                        mediaMetadata.addImage(WebImage(Uri.parse(stationDetails.logoUrl)))
                        val mediaInfo = MediaInfo.Builder(stationDetails.streamUrl)
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setContentType("audio/aac")
                            .setMetadata(mediaMetadata)
                            .build()
                        castSession?.remoteMediaClient?.load(mediaInfo, true)
                    } else {
                        val mediaItem = MediaItem.Builder()
                            .setUri(stationDetails.streamUrl)
                            .setMediaId(stationDetails.streamUrl)
                            // Attach the metadata for the local player UI and notification
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(stationDetails.title)
                                    .setArtist(stationDetails.signal) // Use artist field for the signal
                                    .setArtworkUri(Uri.parse(stationDetails.logoUrl))
                                    .build()
                            )
                            .build()
                        localPlayer?.setMediaItem(mediaItem)
                        localPlayer?.prepare()
                        localPlayer?.play()
                    }
                }
            } catch (e: Exception) {
                Log.e("RadioApp", "Error fetching station details", e)
            }
        }
    }

    // Lifecycle methods for Cast session management
    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.addSessionManagerListener(
            sessionManagerListener,
            CastSession::class.java
        )
    }

    override fun onPause() {
        super.onPause()
        castContext?.sessionManager?.removeSessionManagerListener(
            sessionManagerListener,
            CastSession::class.java
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        localPlayer?.release() // Important!
    }
}