package com.example.kenyanradiostations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The checks that have to pass before a recording can start, in one place.
 *
 * Both the mini player and the full-screen player offer a record button, and
 * the rules are identical: there has to be a recordable stream, a connection,
 * and on older Android a storage permission. Duplicating that in two activities
 * is how they drift apart.
 */
object RecordingLauncher {

    sealed class Outcome {
        /** A recording was started. */
        data class Started(val stationName: String) : Outcome()

        /** The recording that was running has been asked to stop. */
        data object Stopped : Outcome()

        /** Nothing happened; show [messageRes]. */
        data class Blocked(val messageRes: Int) : Outcome()

        /** The caller must request WRITE_EXTERNAL_STORAGE and try again. */
        data object NeedsStoragePermission : Outcome()
    }

    fun toggle(
        context: Context,
        station: RadioStation?,
        details: StationDetails?,
        settings: Settings
    ): Outcome {
        if (RecordingService.state.value is RecordingState.Active) {
            RecordingService.stop(context)
            return Outcome.Stopped
        }

        if (station == null || details == null || details.recordableUrl == null) {
            return Outcome.Blocked(R.string.recording_unavailable)
        }
        if (!Connectivity.isOnline(context)) {
            return Outcome.Blocked(R.string.offline_message)
        }
        if (settings.wifiOnly && !Connectivity.isUnmetered(context)) {
            return Outcome.Blocked(R.string.error_wifi_only)
        }

        // Scoped storage removes the need for this from API 29.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Outcome.NeedsStoragePermission
        }

        RecordingService.start(context, station.name, details, settings.recordingLimitMinutes)
        return Outcome.Started(station.name)
    }
}
