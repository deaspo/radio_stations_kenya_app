package com.example.kenyanradiostations

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Whether the device can reach the network at all, and on what.
 *
 * Used to tell "you are offline" apart from "the site is down", which are the
 * same stack trace but very different messages to show someone.
 */
object Connectivity {

    fun isOnline(context: Context): Boolean = capabilities(context)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** Wi-Fi or Ethernet; anything else counts as mobile data. */
    fun isUnmetered(context: Context): Boolean {
        val capabilities = capabilities(context) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun capabilities(context: Context): NetworkCapabilities? {
        val manager = context.getSystemService<ConnectivityManager>() ?: return null
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)
    }
}
