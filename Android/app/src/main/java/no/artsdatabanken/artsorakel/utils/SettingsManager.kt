package no.artsdatabanken.artsorakel.utils

import android.content.Context
import android.content.SharedPreferences
import no.artsdatabanken.artsorakel.manager.ThemeManager
import no.artsdatabanken.artsorakel.manager.LanguageManager
import javax.inject.Inject
import androidx.core.content.edit

/**
 * Provides a simplified interface for accessing and modifying app settings.
 */
class SettingsManager @Inject constructor(
    private val context: Context,
    private val themeManager: ThemeManager,
    private val languageManager: LanguageManager
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_SAVE_HISTORY = "save_history"
        private const val KEY_USE_LOCATION_FOR_ID = "use_location_for_id"
        private const val KEY_DISCLAIMER_VERSION = "disclaimer_version"
        private const val CURRENT_DISCLAIMER_VERSION = 1
    }

    fun applySavedSettings() {
        themeManager.applySavedTheme()
        languageManager.applySavedLanguage()
    }

    /**
     * Get whether history saving is enabled
     */
    fun isSaveHistoryEnabled(): Boolean {
        return prefs.getBoolean(KEY_SAVE_HISTORY, true) // Default to enabled
    }

    /**
     * Set whether history saving is enabled
     */
    fun setSaveHistoryEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SAVE_HISTORY, enabled) }
    }

    /**
     * Check if the user needs to see the disclaimer
     * @return true if the disclaimer should be shown (first launch or version updated)
     */
    fun shouldShowDisclaimer(): Boolean {
        val savedVersion = prefs.getInt(KEY_DISCLAIMER_VERSION, 0)
        return savedVersion < CURRENT_DISCLAIMER_VERSION
    }

    /**
     * Mark the current disclaimer version as seen by the user
     */
    fun markDisclaimerSeen() {
        prefs.edit { putInt(KEY_DISCLAIMER_VERSION, CURRENT_DISCLAIMER_VERSION) }
    }

    /**
     * Get whether location should be used for identification
     */
    fun isUseLocationForIdEnabled(): Boolean {
        return prefs.getBoolean(KEY_USE_LOCATION_FOR_ID, true) // Default to enabled
    }

    /**
     * Set whether location should be used for identification
     */
    fun setUseLocationForIdEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_LOCATION_FOR_ID, enabled) }
    }
} 