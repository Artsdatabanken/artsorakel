package no.artsdatabanken.artsorakel.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import no.artsdatabanken.artsorakel.core.Constants
import javax.inject.Inject

/**
 * Manages the app's theme (light, dark, system default) with lifecycle awareness.
 */
class ThemeManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(Constants.Preferences.APP_SETTINGS, Context.MODE_PRIVATE)

    enum class ThemeMode(val value: Int) {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        companion object {
            fun fromValue(value: Int): ThemeMode {
                return entries.find { it.value == value } ?: SYSTEM
            }
        }
    }

    /**
     * Gets the currently active theme mode
     */
    fun getCurrentThemeMode(): ThemeMode {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        return ThemeMode.fromValue(currentMode)
    }

    /**
     * Gets the saved theme mode from preferences
     */
    fun getSavedThemeMode(): ThemeMode {
        val savedMode = sharedPreferences.getInt(
            Constants.Preferences.THEME_KEY, 
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        return ThemeMode.fromValue(savedMode)
    }

    /**
     * Sets and applies a new theme mode
     */
    fun setThemeMode(themeMode: ThemeMode) {
        if (getCurrentThemeMode() != themeMode) {
            saveThemeMode(themeMode)
            applyThemeMode(themeMode)
        }
    }

    /**
     * Applies the saved theme mode from preferences
     */
    fun applySavedTheme() {
        val savedThemeMode = getSavedThemeMode()
        applyThemeMode(savedThemeMode)
    }

    /**
     * Checks if the current theme is dark mode
     */
    fun isDarkModeActive(): Boolean {
        return when (getCurrentThemeMode()) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                // For system mode, we'd need to check system configuration
                // This is a simplified check
                val currentNightMode = context.resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun saveThemeMode(themeMode: ThemeMode) {
        sharedPreferences.edit {
            putInt(Constants.Preferences.THEME_KEY, themeMode.value)
        }
    }

    private fun applyThemeMode(themeMode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(themeMode.value)
    }

    companion object {
        /**
         * Creates a theme manager instance for the given context
         */
        fun create(context: Context): ThemeManager {
            return ThemeManager(context)
        }
    }
} 