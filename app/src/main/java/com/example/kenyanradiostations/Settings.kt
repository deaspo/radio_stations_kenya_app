package com.example.kenyanradiostations

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * User settings, backed by SharedPreferences.
 *
 * Every value here is read somewhere: a switch that does nothing is worse than
 * no switch at all.
 */
class Settings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class Theme(val key: String, val nightMode: Int) {
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            /*
             * Stored under an explicit key rather than Enum.name. R8 is free to
             * rename enum constants in a minified build, and a saved preference
             * must not depend on that - nor on nobody ever renaming the
             * constant in source.
             */
            fun fromKey(key: String?): Theme =
                entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }

    var theme: Theme
        get() = Theme.fromKey(prefs.getString(KEY_THEME, null))
        set(value) = prefs.edit().putString(KEY_THEME, value.key).apply()

    /** Start the last station automatically when the app is opened. */
    var resumeLastStation: Boolean
        get() = prefs.getBoolean(KEY_RESUME, false)
        set(value) = prefs.edit().putBoolean(KEY_RESUME, value).apply()

    /** Refuse to stream or record when the device is on mobile data. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    /** 0 means no limit. */
    var recordingLimitMinutes: Int
        get() = prefs.getInt(KEY_RECORDING_LIMIT, DEFAULT_RECORDING_LIMIT_MINUTES)
        set(value) = prefs.edit().putInt(KEY_RECORDING_LIMIT, value).apply()

    var lastStationId: String?
        get() = prefs.getString(KEY_LAST_ID, null)
        private set(value) = prefs.edit().putString(KEY_LAST_ID, value).apply()

    var lastStationName: String?
        get() = prefs.getString(KEY_LAST_NAME, null)
        private set(value) = prefs.edit().putString(KEY_LAST_NAME, value).apply()

    var lastStationLogo: String?
        get() = prefs.getString(KEY_LAST_LOGO, null)
        private set(value) = prefs.edit().putString(KEY_LAST_LOGO, value).apply()

    fun rememberLastStation(station: RadioStation) {
        prefs.edit()
            .putString(KEY_LAST_ID, station.id)
            .putString(KEY_LAST_NAME, station.name)
            .putString(KEY_LAST_LOGO, station.logoUrl)
            .apply()
    }

    fun lastStation(): RadioStation? {
        val id = lastStationId ?: return null
        val name = lastStationName ?: return null
        return RadioStation(id = id, name = name, logoUrl = lastStationLogo.orEmpty())
    }

    fun resetOnboarding() {
        appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val APP_PREFS = "app_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

        private const val KEY_THEME = "theme"
        private const val KEY_RESUME = "resume_last_station"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_RECORDING_LIMIT = "recording_limit_minutes"
        private const val KEY_LAST_ID = "last_station_id"
        private const val KEY_LAST_NAME = "last_station_name"
        private const val KEY_LAST_LOGO = "last_station_logo"

        const val DEFAULT_RECORDING_LIMIT_MINUTES = 60

        /** Offered in the settings screen; 0 is "no limit". */
        val RECORDING_LIMIT_CHOICES = intArrayOf(0, 15, 30, 60, 120)
    }
}
