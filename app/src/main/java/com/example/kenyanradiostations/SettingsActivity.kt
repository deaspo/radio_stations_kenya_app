package com.example.kenyanradiostations

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import com.example.kenyanradiostations.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        applyWindowInsets()

        bindTheme()
        bindLanguage()
        bindSwitches()
        bindRecordingLimit()
        bindActions()
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = bars.top)
            binding.settingsScroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun bindTheme() {
        val checkedId = when (settings.theme) {
            Settings.Theme.SYSTEM -> R.id.theme_system
            Settings.Theme.LIGHT -> R.id.theme_light
            Settings.Theme.DARK -> R.id.theme_dark
        }
        binding.themeGroup.check(checkedId)

        binding.themeGroup.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val theme = when (buttonId) {
                R.id.theme_light -> Settings.Theme.LIGHT
                R.id.theme_dark -> Settings.Theme.DARK
                else -> Settings.Theme.SYSTEM
            }
            if (theme == settings.theme) return@addOnButtonCheckedListener
            settings.theme = theme
            // Recreates every activity in the task, this one included.
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        }
    }

    private fun bindLanguage() {
        renderLanguage()
        binding.rowLanguage.setOnClickListener {
            val labels = AppLanguage.OPTIONS.map { getString(it.labelRes) }.toTypedArray()
            val current = AppLanguage.indexOf(AppLanguage.currentTag())

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    dialog.dismiss()
                    val chosen = AppLanguage.OPTIONS[which].tag
                    if (chosen != AppLanguage.currentTag()) {
                        // Recreates this activity, so nothing after this runs.
                        AppLanguage.apply(chosen)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun renderLanguage() {
        val option = AppLanguage.OPTIONS[AppLanguage.indexOf(AppLanguage.currentTag())]
        binding.languageValue.setText(option.labelRes)
    }

    private fun bindSwitches() {
        binding.switchResume.isChecked = settings.resumeLastStation
        binding.switchResume.setOnCheckedChangeListener { _, checked ->
            settings.resumeLastStation = checked
        }

        binding.switchKeepScreenOn.isChecked = settings.keepScreenOn
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, checked ->
            settings.keepScreenOn = checked
        }

        binding.switchWifiOnly.isChecked = settings.wifiOnly
        binding.switchWifiOnly.setOnCheckedChangeListener { _, checked ->
            settings.wifiOnly = checked
        }
    }

    private fun bindRecordingLimit() {
        renderRecordingLimit()
        binding.rowRecordingLimit.setOnClickListener {
            val choices = Settings.RECORDING_LIMIT_CHOICES
            val labels = choices.map { limitLabel(it) }.toTypedArray()
            val current = choices.indexOf(settings.recordingLimitMinutes).coerceAtLeast(0)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_recording_limit)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    settings.recordingLimitMinutes = choices[which]
                    renderRecordingLimit()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun renderRecordingLimit() {
        binding.recordingLimitValue.text = limitLabel(settings.recordingLimitMinutes)
    }

    private fun limitLabel(minutes: Int): String =
        if (minutes <= 0) {
            getString(R.string.settings_recording_limit_none)
        } else {
            getString(R.string.settings_recording_limit_minutes, minutes)
        }

    private fun bindActions() {
        binding.rowClearCache.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val loader = applicationContext.imageLoader
                    runCatching { loader.diskCache?.clear() }
                    runCatching { loader.memoryCache?.clear() }
                }
                Toast.makeText(
                    this@SettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.rowReplayOnboarding.setOnClickListener {
            settings.resetOnboarding()
            Toast.makeText(this, R.string.settings_onboarding_reset, Toast.LENGTH_SHORT).show()
        }
    }
}
