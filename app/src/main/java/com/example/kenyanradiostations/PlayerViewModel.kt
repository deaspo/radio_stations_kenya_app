package com.example.kenyanradiostations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Use AndroidViewModel to get the application context
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // The ViewModel now owns the player instance
    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    // This StateFlow will hold the metadata of the currently playing station.
    // UI components can observe it for changes.
    private val _nowPlaying = MutableStateFlow<MediaMetadata?>(null)
    val nowPlaying: StateFlow<MediaMetadata?> = _nowPlaying

    fun updateNowPlaying(metadata: MediaMetadata) {
        _nowPlaying.value = metadata
    }

    fun clearNowPlaying() {
        _nowPlaying.value = null
    }

    // This method is called when the ViewModel is no longer used and will be destroyed.
    // This happens when the app is fully closed, not during screen rotation.
    override fun onCleared() {
        super.onCleared()
        // Release the player when the ViewModel is cleared
        player.release()
    }
}