package com.example.kenyanradiostations

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var analytics: FirebaseAnalytics

    private lateinit var binding: ActivityMainBinding

    private lateinit var audioManager: AudioManager
    private var mediaController: MediaController? = null

    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private val sessionManagerListener = SessionManagerListenerImpl()

    private var isDataReady = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Notification permission is required for background playback.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                binding.playerControlsContainer.root.visibility = View.GONE
            } else {
                binding.playerControlsContainer.root.visibility = View.VISIBLE
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen. This MUST be called before super.onCreate() or setContentView().
        installSplashScreen().apply {
            // Keep the splash screen on screen until isDataReady is true.
            setKeepOnScreenCondition { !isDataReady }
        }

        super.onCreate(savedInstanceState)

        // Check if onboarding is complete BEFORE any other setup
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val onboardingComplete = sharedPrefs.getBoolean("onboarding_complete", false)

        if (!onboardingComplete) {
            // User needs to see the onboarding flow.
            // Launch OnboardingActivity and finish this one.
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return // Important: return here to stop further execution of this onCreate
        }
        // --- Onboarding is complete, proceed with normal app startup ---

        // Obtain the FirebaseAnalytics instance.
        analytics = Firebase.analytics

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        askNotificationPermission()
        createNotificationChannel()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupCast()
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        fetchStations()
        setupPlayerControls()
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayerControls() {
        // Set up the new MediaRouteButton from the player controls layout
        CastButtonFactory.setUpMediaRouteButton(applicationContext, binding.playerControlsContainer.castButton)

        binding.playerControlsContainer.closeButton.setOnClickListener {
            mediaController?.stop()
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

    private fun playStation(station: RadioStation) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.instant.audio/data/streams/81/${station.id}"
                val jsonString = URL(apiUrl).openStream().bufferedReader().readText()
                val stationDetails = parseStationDetails(jsonString, station)

                withContext(Dispatchers.Main) {
                    if (stationDetails == null) {
                        Toast.makeText(
                            this@MainActivity, "Could not find stream.", Toast.LENGTH_SHORT
                        ).show()
                        return@withContext
                    }

                    if (castSession != null && castSession!!.isConnected) {
                        mediaController?.stop()
                        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
                        mediaMetadata.putString(MediaMetadata.KEY_TITLE, stationDetails.title)
                        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, stationDetails.signal)
                        mediaMetadata.addImage(WebImage(Uri.parse(stationDetails.logoUrl)))
                        val mediaInfo = MediaInfo.Builder(stationDetails.streamUrl)
                            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                            .setContentType("audio/aac").setMetadata(mediaMetadata).build()
                        castSession?.remoteMediaClient?.load(mediaInfo, true)
                    } else {
                        val mediaItem = MediaItem.Builder()
                            .setUri(stationDetails.streamUrl)
                            .setMediaId(stationDetails.streamUrl)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(stationDetails.title)
                                    .setArtist(stationDetails.signal)
                                    .setArtworkUri(Uri.parse(stationDetails.logoUrl))
                                    .build()
                            ).build()

                        mediaController?.setMediaItem(mediaItem)
                        mediaController?.prepare()
                        mediaController?.play()
                    }
                }
            } catch (e: Exception) {
                Log.e("RadioApp", "Error fetching station details", e)
            }
        }
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
                    "RadioApp", "Found ${stationElements.size} station elements with new selector."
                )

                for (element in stationElements) {
                    val link = element.selectFirst("a")
                    val image = element.selectFirst("img")

                    if (link != null && image != null) {
                        val id = link.attr("href").substringAfterLast('#', "")
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
                    // Data is loaded and the adapter is set. It's time to dismiss the splash screen.
                    isDataReady = true
                }
            } catch (e: Exception) {
                // Handle exceptions (e.g., no network)
                Log.e("RadioApp", "Failed to fetch stations", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    // Show an error message
                }
                // Also dismiss the splash screen on error to not get stuck.
                isDataReady = true
            }
        }
    }

    private fun parseStationDetails(
        jsonString: String, originalStation: RadioStation
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

    private fun setupCast() {
        castContext = CastContext.getSharedInstance(this)
        castSession = castContext?.sessionManager?.currentCastSession
    }

    private inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {
        private fun transferToRemotePlayer(session: CastSession) {
            // Check if the service player is active via the controller
            val playingLocally = mediaController?.isPlaying == true
            if (playingLocally) {
                val currentItem = mediaController?.currentMediaItem ?: return

                val mediaMetadata = currentItem.mediaMetadata

                val castMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
                castMetadata.putString(MediaMetadata.KEY_TITLE, mediaMetadata.title.toString())
                castMetadata.putString(MediaMetadata.KEY_SUBTITLE, mediaMetadata.artist.toString())

                mediaMetadata.artworkUri?.let { castMetadata.addImage(WebImage(it)) }

                val mediaInfo = MediaInfo.Builder(currentItem.mediaId ?: "")
                    .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                    .setContentType("audio/aac")
                    .setMetadata(castMetadata)
                    .build()

                // Load the media, and on success, explicitly play and then stop the service player.
                session.remoteMediaClient?.load(mediaInfo, false)?.setResultCallback { result ->
                    if (result.status.isSuccess) {
                        session.remoteMediaClient?.play()
                        // Tell the service to stop playback
                        mediaController?.stop()
                    }
                }
            }
        }

        private fun transferToLocalMediaPlayer(session: CastSession) {
            val remoteMediaClient = session.remoteMediaClient
            val playingRemotely =
                remoteMediaClient?.isPlaying == true || remoteMediaClient?.isBuffering == true

            if (playingRemotely) {
                val mediaInfo = remoteMediaClient?.mediaInfo ?: return
                val castMetadata = mediaInfo.metadata
                val localMetadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(castMetadata?.getString(MediaMetadata.KEY_TITLE))
                    .setArtist(castMetadata?.getString(MediaMetadata.KEY_SUBTITLE))
                    .setArtworkUri(castMetadata?.images?.firstOrNull()?.url)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(mediaInfo.contentId)
                    .setMediaId(mediaInfo.contentId)
                    .setMediaMetadata(localMetadata)
                    .build()

                // Use the controller to send commands to the service
                mediaController?.setMediaItem(mediaItem)
                mediaController?.prepare()
                mediaController?.play()
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            transferToRemotePlayer(session)
            castSession = session
            invalidateOptionsMenu()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            transferToRemotePlayer(session)
            castSession = session
            invalidateOptionsMenu()
        }

        // THIS IS THE NEWLY ADDED, REQUIRED METHOD
        override fun onSessionEnding(session: CastSession) {
            // Called just before the session ends.
            // You can add cleanup logic here if needed.
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            transferToLocalMediaPlayer(session)
            if (session == castSession) {
                castSession = null
            }
            invalidateOptionsMenu()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle menu item clicks
        return when (item.itemId) {
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
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Radio Playback"
            val descriptionText = "Shows the currently playing radio station"
            val importance =
                NotificationManager.IMPORTANCE_LOW // Low importance to be less intrusive
            val channel = NotificationChannel("radio_playback_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeController() {
        val sessionToken = SessionToken(this, ComponentName(this, RadioService::class.java))
        val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            binding.playerControlsContainer.playerView.player = mediaController
            mediaController?.addListener(playerListener)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStart() {
        super.onStart()
        initializeController()
    }

    @OptIn(UnstableApi::class)
    override fun onStop() {
        super.onStop()
        mediaController?.release()
        mediaController = null
        binding.playerControlsContainer.playerView.player = null
    }

    // Lifecycle methods for Cast session management
    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.addSessionManagerListener(
            sessionManagerListener, CastSession::class.java
        )
    }

    override fun onPause() {
        super.onPause()
        castContext?.sessionManager?.removeSessionManagerListener(
            sessionManagerListener, CastSession::class.java
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}