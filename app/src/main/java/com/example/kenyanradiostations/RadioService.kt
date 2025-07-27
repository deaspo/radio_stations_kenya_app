package com.example.kenyanradiostations

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult

class RadioService : MediaLibraryService() {

    //private var mediaSession: MediaSessionCompat? = null
    //private var player: ExoPlayer? = null
    //private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer

    private val mediaLibrarySessionCallback = object : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ConnectionResult {
            // Get the default session commands from the super class
            val sessionCommands =
                ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    // Add any custom commands here if needed
                    .build()

            // Return a ConnectionResult that accepts the connection and specifies the commands
            return ConnectionResult.accept(
                sessionCommands,
                ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }
    }


//    companion object {
//        const val NOTIFICATION_ID = 1
//        const val NOTIFICATION_CHANNEL_ID = "radio_channel"
//    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        //player = ExoPlayer.Builder(this).build().also { it.addListener(this) }
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaLibrarySession =
            MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
                .build()

//        mediaSession = MediaSessionCompat(this, "RadioService").apply {
//            setCallback(mediaSessionCallback)
//            setSessionToken(sessionToken)
//        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (mediaLibrarySession?.player?.playWhenReady == false) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }


//    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
//        override fun onPrepareFromMediaId(mediaId: String, extras: Bundle?) {
//            val station: StationDetails? =
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                    extras?.getParcelable("STATION_DETAILS", StationDetails::class.java)
//                } else {
//                    @Suppress("DEPRECATION")
//                    extras?.getParcelable("STATION_DETAILS")
//                }
//
//            station?.let {
//                val mediaItem = MediaItem.fromUri(it.streamUrl)
//                player?.setMediaItem(mediaItem)
//                player?.prepare()
//
//                // Update metadata for the notification
//                serviceScope.launch { updateMetadata(it) }
//
//                // Set the session state to Paused, ready for the 'play' command
//                mediaSession?.setPlaybackState(
//                    PlaybackStateCompat.Builder()
//                        .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
//                        .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP)
//                        .build()
//                )
//            }
//        }
//
//        override fun onPlayFromUri(uri: android.net.Uri?, extras: Bundle?) {
//            var currentStationDetails =
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                    extras?.getParcelable("STATION_DETAILS", StationDetails::class.java)
//                } else {
//                    @Suppress("DEPRECATION") // Suppress warning for older APIs
//                    extras?.getParcelable("STATION_DETAILS")
//                }
//
//            currentStationDetails?.let { station ->
//                val mediaItem = MediaItem.fromUri(station.streamUrl)
//                player?.setMediaItem(mediaItem)
//                player?.prepare()
//                player?.play()
//
//                // Update metadata for the notification
//                serviceScope.launch { updateMetadata(station) }
//            }
//        }
//
//        override fun onPlay() {
//            player?.play()
//        }
//
//        override fun onPause() {
//            player?.pause()
//        }
//
//        override fun onStop() {
//            player?.stop()
//            stopSelf() // Stop the service
//        }
//    }

//    override fun onPlaybackStateChanged(playbackState: Int) {
//        val state = when (playbackState) {
//            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
//            Player.STATE_READY -> if (player?.playWhenReady == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
//            else -> PlaybackStateCompat.STATE_STOPPED
//        }
//        mediaSession?.setPlaybackState(
//            PlaybackStateCompat.Builder()
//                .setState(state, 0, 1.0f)
//                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP)
//                .build()
//        )
//
//        // Update notification
//        startForeground(NOTIFICATION_ID, buildNotification())
//    }
//
//    private suspend fun updateMetadata(station: StationDetails) {
//        val request = ImageRequest.Builder(this)
//            .data(station.logoUrl)
//            .allowHardware(false) // Required for notification bitmaps
//            .build()
//        val imageResult = imageLoader.execute(request)
//        val bitmap = (imageResult.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
//
//        val metadata = MediaMetadataCompat.Builder()
//            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.title)
//            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.signal)
//            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
//            .build()
//        mediaSession?.setMetadata(metadata)
//    }
//
//    private fun buildNotification(): Notification {
//        val controller = mediaSession?.controller
//        val metadata = controller?.metadata
//        val description = metadata?.description
//
//        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
//            setContentTitle(description?.title)
//            setContentText(description?.subtitle)
//            setSubText(description?.description)
//            setLargeIcon(description?.iconBitmap)
//
//            // Open the app when the notification is tapped
//            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
//            setContentIntent(
//                PendingIntent.getActivity(
//                    this@RadioService,
//                    0,
//                    launchIntent,
//                    PendingIntent.FLAG_IMMUTABLE
//                )
//            )
//
//            // Stop the service when the notification is swiped away
//            setDeleteIntent(
//                androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
//                    this@RadioService, PlaybackStateCompat.ACTION_STOP
//                )
//            )
//
//            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//            setSmallIcon(R.drawable.ic_radio_icon) // Add a small icon for the status bar
//            setColor(ContextCompat.getColor(this@RadioService, R.color.purple_500)) // Set a color
//
//            // Add media control buttons
//            addAction(
//                NotificationCompat.Action(
//                    if (player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play,
//                    "Pause",
//                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
//                        this@RadioService,
//                        PlaybackStateCompat.ACTION_PLAY_PAUSE
//                    )
//                )
//            )
//            addAction(
//                NotificationCompat.Action(
//                    R.drawable.ic_close,
//                    "Stop",
//                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
//                        this@RadioService,
//                        PlaybackStateCompat.ACTION_STOP
//                    )
//                )
//            )
//
//            // Take advantage of MediaStyle features
//            setStyle(
//                MediaStyle()
//                    .setMediaSession(mediaSession?.sessionToken)
//                    .setShowActionsInCompactView(0, 1) // Show play/pause and stop in compact view
//                    .setShowCancelButton(true)
//                    .setCancelButtonIntent(
//                        androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
//                            this@RadioService,
//                            PlaybackStateCompat.ACTION_STOP
//                        )
//                    )
//            )
//        }
//        return builder.build()
//    }

    // Boilerplate service methods
//    override fun onGetRoot(
//        clientPackageName: String,
//        clientUid: Int,
//        rootHints: Bundle?
//    ): BrowserRoot? {
//        return BrowserRoot("root", null) // Allow connections
//    }
//
//    override fun onLoadChildren(
//        parentId: String,
//        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
//    ) {
//        result.sendResult(null) // Not used for this app
//    }

//    override fun onDestroy() {
//        super.onDestroy()
//        serviceScope.cancel()
//        player?.release()
//        mediaSession?.release()
//    }
}