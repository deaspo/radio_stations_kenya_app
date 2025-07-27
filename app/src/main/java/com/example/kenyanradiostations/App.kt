package com.example.kenyanradiostations

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Radio Playback"
            val descriptionText = "Shows the currently playing radio station"
            // Use IMPORTANCE_LOW to prevent the notification from making a sound.
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("radio_playback_channel", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}