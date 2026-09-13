package com.example.kenyanradiostations

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class RecordingState {
    data object Idle : RecordingState()
    data class Active(val stationName: String, val startedAtElapsedRealtime: Long) : RecordingState()
}

/**
 * Captures a live stream to a file.
 *
 * There is no transcoding here, and deliberately so: the stream API hands us a
 * progressive MP3 or AAC URL, and those bytes are already a valid file. Writing
 * them straight to disk gives a bit-perfect recording at zero CPU cost, and the
 * extension always matches what was actually written.
 *
 * The recorder opens its own connection rather than tapping the player's, so a
 * recording is unaffected by pausing, switching station, or a Cast hand-off.
 */
class RecordingService : Service() {

    private var worker: Thread? = null

    @Volatile
    private var running = false

    /** Held so stopping can break out of a blocking socket read. */
    @Volatile
    private var connection: HttpURLConnection? = null

    /** Set when the platform ended the recording, not the user. */
    @Volatile
    private var timedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15 gives a dataSync foreground service six hours in any 24, then
     * calls this and expects the service gone within seconds - otherwise the
     * platform raises RemoteServiceException and the part-written recording is
     * lost. Stopping here finalises the file instead, so the user keeps the six
     * hours that were captured.
     *
     * The quota resets when the app is next brought to the foreground.
     */
    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service time limit reached; finalising the recording")
        timedOut = true
        stopRecording()
        // Belt and braces: if the capture thread is wedged on a socket, stop
        // anyway rather than let the platform kill the process.
        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, STOP_GRACE_MS)
    }

    override fun onDestroy() {
        running = false
        runCatching { connection?.disconnect() }
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        if (running) return

        val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            stopSelf()
            return
        }
        val extension = intent.getStringExtra(EXTRA_EXTENSION) ?: "mp3"
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "audio/mpeg"
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)

        val startedAt = SystemClock.elapsedRealtime()
        running = true
        _state.value = RecordingState.Active(stationName, startedAt)

        // Must be inside five seconds of startForegroundService, so before any
        // network work happens.
        startInForeground(buildOngoingNotification(stationName, 0L))

        worker = Thread {
            capture(stationName, url, extension, mimeType, limitMinutes, startedAt)
        }.apply {
            name = "radio-recorder"
            start()
        }
    }

    private fun stopRecording() {
        running = false
        // Closing the connection is what actually unblocks InputStream.read;
        // interrupting the thread does not.
        runCatching { connection?.disconnect() }
    }

    // POST_NOTIFICATIONS is declared and requested in MainActivity; a refused
    // permission simply means the progress notification is not shown.
    @SuppressLint("MissingPermission")
    private fun capture(
        stationName: String,
        url: String,
        extension: String,
        mimeType: String,
        limitMinutes: Int,
        startedAt: Long
    ) {
        val target = RecordingStore.create(this, stationName, extension, mimeType)
        if (target == null) {
            finishWith(getString(R.string.recording_failed_storage), null)
            return
        }

        var bytesWritten = 0L
        var input: InputStream? = null

        try {
            input = openStream(url)
            val buffer = ByteArray(BUFFER_SIZE)
            val limitMs = if (limitMinutes > 0) limitMinutes * 60_000L else Long.MAX_VALUE
            var lastNotifiedAt = 0L

            while (running) {
                val read = input.read(buffer)
                if (read < 0) break
                target.output.write(buffer, 0, read)
                bytesWritten += read

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= limitMs) break
                if (elapsed - lastNotifiedAt >= NOTIFICATION_INTERVAL_MS) {
                    lastNotifiedAt = elapsed
                    NotificationManagerCompat.from(this).notify(
                        ONGOING_NOTIFICATION_ID,
                        buildOngoingNotification(stationName, elapsed)
                    )
                }
            }
        } catch (error: Exception) {
            // A dropped connection is normal for live radio. Whatever was
            // written up to that point is still a perfectly playable file, so
            // it is kept rather than thrown away.
            Log.w(TAG, "Recording of $stationName ended early", error)
        } finally {
            runCatching { input?.close() }
            runCatching { connection?.disconnect() }
            connection = null
        }

        if (bytesWritten >= MINIMUM_USEFUL_BYTES) {
            RecordingStore.finish(this, target)
            val saved = getString(R.string.recording_saved, target.displayName)
            finishWith(
                if (timedOut) getString(R.string.recording_timed_out, saved) else saved,
                target.displayName
            )
        } else {
            RecordingStore.discard(this, target)
            finishWith(getString(R.string.recording_failed_empty), null)
        }
    }

    /**
     * Opens the stream, following redirects by hand because HttpURLConnection
     * will not follow one that changes protocol, which http -> https does.
     *
     * Note the absence of an "Icy-MetaData: 1" header: asking for ICY metadata
     * makes Shoutcast servers interleave title blocks into the audio, which
     * would corrupt the file.
     */
    private fun openStream(url: String): InputStream {
        var current = url
        repeat(MAX_REDIRECTS) {
            val candidate = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = candidate.responseCode
            if (code in 300..399) {
                val location = candidate.getHeaderField("Location")
                candidate.disconnect()
                if (location.isNullOrEmpty()) throw java.io.IOException("Redirect without Location")
                current = URL(URL(current), location).toString()
            } else {
                connection = candidate
                return candidate.inputStream
            }
        }
        throw java.io.IOException("Too many redirects")
    }

    @SuppressLint("MissingPermission")
    private fun finishWith(message: String, savedName: String?) {
        running = false
        _state.value = RecordingState.Idle

        val builder = NotificationCompat.Builder(this, App.RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio_icon)
            .setContentTitle(getString(R.string.recording_notification_done_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
        if (savedName != null) {
            builder.setContentIntent(recordingsIntent())
        }

        NotificationManagerCompat.from(this).notify(DONE_NOTIFICATION_ID, builder.build())

        stopForegroundCompat()
        stopSelf()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun buildOngoingNotification(stationName: String, elapsedMs: Long): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags()
        )

        return NotificationCompat.Builder(this, App.RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(getString(R.string.recording_notification_title, stationName))
            .setContentText(Elapsed.format(elapsedMs))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(recordingsIntent())
            .addAction(
                R.drawable.ic_close,
                getString(R.string.recording_stop),
                stopIntent
            )
            .build()
    }

    private fun recordingsIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, RecordingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        pendingIntentFlags()
    )

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

    companion object {

        private const val TAG = "RadioRecorder"

        const val ACTION_START = "com.example.kenyanradiostations.action.START_RECORDING"
        const val ACTION_STOP = "com.example.kenyanradiostations.action.STOP_RECORDING"

        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_URL = "url"
        const val EXTRA_EXTENSION = "extension"
        const val EXTRA_MIME_TYPE = "mime_type"
        const val EXTRA_LIMIT_MINUTES = "limit_minutes"

        private const val ONGOING_NOTIFICATION_ID = 2001
        private const val DONE_NOTIFICATION_ID = 2002
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN = 2

        private const val BUFFER_SIZE = 16 * 1024
        private const val MINIMUM_USEFUL_BYTES = 32L * 1024
        private const val NOTIFICATION_INTERVAL_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 4
        private const val STOP_GRACE_MS = 2_000L
        private const val USER_AGENT = "RadioDiaspora/1.0 (Android)"

        private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val state: StateFlow<RecordingState> = _state.asStateFlow()

        fun start(context: Context, stationName: String, details: StationDetails, limitMinutes: Int) {
            val url = details.recordableUrl ?: return
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATION_NAME, stationName)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_EXTENSION, details.recordingExtension)
                putExtra(EXTRA_MIME_TYPE, details.recordingMimeType)
                putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

    }
}
