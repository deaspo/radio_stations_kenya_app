package com.example.kenyanradiostations

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Parcelable so the scraped catalogue can be kept in the activity's saved
 * state: without it a rotation threw the list away and re-scraped the site.
 */
@Parcelize
data class RadioStation(
    val id: String,
    val name: String,
    val logoUrl: String
) : Parcelable
