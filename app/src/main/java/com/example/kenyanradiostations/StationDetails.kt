package com.example.kenyanradiostations

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StationDetails(
    val title: String,
    val signal: String,
    val logoUrl: String,
    val streamUrl: String
) : Parcelable
