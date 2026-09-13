package com.example.kenyanradiostations

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A resolved station: the URL playback should use, plus every alternative the
 * stream API offered.
 *
 * Playback and recording want different things. Playback prefers HLS, which
 * adapts to a flaky connection. Recording prefers a progressive MP3 or AAC
 * stream, because those can be written to a file byte for byte with no
 * transcoding at all - HLS arrives as separate segments that are not a valid
 * file when simply concatenated.
 */
@Parcelize
data class StationDetails(
    val title: String,
    val signal: String,
    val logoUrl: String,
    val streamUrl: String,
    val mp3Url: String? = null,
    val aacUrl: String? = null,
    val hlsUrl: String? = null
) : Parcelable {

    /** Null when the station only offers HLS, in which case recording is off. */
    val recordableUrl: String?
        get() = mp3Url ?: aacUrl

    /** The real container of [recordableUrl] - never a lie about the contents. */
    val recordingExtension: String
        get() = if (mp3Url != null) "mp3" else "aac"

    val recordingMimeType: String
        get() = if (mp3Url != null) "audio/mpeg" else "audio/aac"
}
