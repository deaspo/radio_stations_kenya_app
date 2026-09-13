package com.example.kenyanradiostations

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Applied before any activity is created so the first frame is already
        // in the chosen theme.
        AppCompatDelegate.setDefaultNightMode(Settings(this).theme.nightMode)

        createRecordingChannel()
    }

    /**
     * Media3 creates and owns the channel for the playback notification; this
     * one is only for recording progress.
     */
    private fun createRecordingChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(RECORDING_CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                RECORDING_CHANNEL_ID,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.recording_channel_description)
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val RECORDING_CHANNEL_ID = "recording"
    }
}
