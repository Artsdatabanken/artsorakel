package no.artsdatabanken.artsorakel.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import no.artsdatabanken.artsorakel.core.Constants
import javax.inject.Inject

/**
 * Manages app language/locale with lifecycle awareness.
 * Handles language persistence and application.
 */
class LanguageManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(Constants.Preferences.APP_SETTINGS, Context.MODE_PRIVATE)

    enum class SupportedLanguage(val languageTag: String, val displayName: String) {
        SYSTEM("", "System Default"),
        NORWEGIAN_BOKMAAL("nb", "Norsk (Bokmål)"),
        NORWEGIAN_NYNORSK("nn", "Norsk (Nynorsk)"),
        ENGLISH("en", "English"),
        SPANISH("es", "Español"),
        SWEDISH("sv", "Svenska"),

        DUTCH("nl", "Nederlands");

        companion object {
            fun fromLanguageTag(tag: String): SupportedLanguage {
                return entries.find { it.languageTag == tag } ?: SYSTEM
            }
        }
    }

    /**
     * Gets the currently active language
     */
    fun getCurrentLanguage(): SupportedLanguage {
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        if (currentAppLocales.isEmpty) {
            return SupportedLanguage.SYSTEM
        }
        
        val currentTag = currentAppLocales[0]?.toLanguageTag() ?: ""
        return SupportedLanguage.fromLanguageTag(currentTag)
    }

    /**
     * Gets the saved language from preferences
     */
    fun getSavedLanguage(): SupportedLanguage {
        val savedTag = sharedPreferences.getString(Constants.Preferences.LANGUAGE_KEY, "") ?: ""
        return SupportedLanguage.fromLanguageTag(savedTag)
    }

    /**
     * Sets and applies a new language
     */
    fun setLanguage(language: SupportedLanguage) {
        if (getCurrentLanguage() != language) {
            saveLanguage(language)
            applyLanguage(language)
        }
    }

    /**
     * Applies the saved language from preferences
     */
    fun applySavedLanguage() {
        val savedLanguage = getSavedLanguage()
        applyLanguage(savedLanguage)
    }

    /**
     * Gets all supported languages
     */
    fun getSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.entries
    }

    /**
     * Checks if a specific language is currently active
     */
    fun isLanguageActive(language: SupportedLanguage): Boolean {
        return getCurrentLanguage() == language
    }

    /**
     * Gets the current language tag (empty string for system default)
     */
    fun getCurrentLanguageTag(): String {
        return getCurrentLanguage().languageTag
    }

    /**
     * Gets the effective language tag being used (resolves system default to actual locale)
     */
    fun getEffectiveLanguageTag(): String {
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        if (currentAppLocales.isEmpty) {
            // System default - use the device's locale
            return context.resources.configuration.locales[0]?.language ?: "en"
        }
        return currentAppLocales[0]?.language ?: "en"
    }

    private fun saveLanguage(language: SupportedLanguage) {
        sharedPreferences.edit {
            putString(Constants.Preferences.LANGUAGE_KEY, language.languageTag)
        }
    }

    private fun applyLanguage(language: SupportedLanguage) {
        val localeList = if (language.languageTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    companion object {
        /**
         * Creates a language manager instance for the given context
         */
        fun create(context: Context): LanguageManager {
            return LanguageManager(context)
        }

        /**
         * Gets the language tag for a specific supported language
         */
        fun getLanguageTag(language: SupportedLanguage): String {
            return language.languageTag
        }
    }
} 