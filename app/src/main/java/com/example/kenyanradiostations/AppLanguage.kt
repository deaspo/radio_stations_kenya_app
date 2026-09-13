package com.example.kenyanradiostations

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The in-app language picker.
 *
 * The choice is not stored in [Settings]. AppCompat owns it: on Android 13 and
 * above it hands the locale to the platform, which shows the same value in the
 * system per-app language screen, and below that it persists the tag itself
 * through the AppLocalesMetadataHolderService declared in the manifest.
 * Keeping a second copy in SharedPreferences would only give the two somewhere
 * to disagree.
 */
object AppLanguage {

    /** An empty [tag] means "follow the system". */
    data class Option(val tag: String, val labelRes: Int)

    val OPTIONS: List<Option> = listOf(
        Option("", R.string.settings_language_system),
        Option("en", R.string.settings_language_en),
        Option("sw", R.string.settings_language_sw)
    )

    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.size() > 0) locales.get(0)?.language.orEmpty() else ""
    }

    fun apply(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        // Recreates every activity in the task so the new strings are inflated.
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Falls back to "follow the system" for a locale the app does not ship. */
    fun indexOf(tag: String): Int =
        OPTIONS.indexOfFirst { it.tag == tag }.takeIf { it >= 0 } ?: 0
}
