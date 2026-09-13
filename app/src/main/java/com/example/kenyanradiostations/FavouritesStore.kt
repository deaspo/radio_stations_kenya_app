package com.example.kenyanradiostations

import android.content.Context

/**
 * Favourite stations, keyed by the station id from radio.or.ke.
 *
 * Ids are used rather than names because names are neither stable nor unique
 * across a re-scrape.
 */
class FavouritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ids(): Set<String> = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()

    /** Toggles [id] and returns the full set afterwards. */
    fun toggle(id: String): Set<String> {
        // getStringSet hands back the live instance, so copy before mutating and
        // always store a new set - SharedPreferences will not persist edits made
        // to the instance it returned.
        val updated = ids().toMutableSet()
        if (!updated.remove(id)) {
            updated.add(id)
        }
        prefs.edit().putStringSet(KEY_IDS, updated).apply()
        return updated
    }

    private companion object {
        const val PREFS_NAME = "radio_prefs"
        const val KEY_IDS = "favourite_station_ids"
    }
}
