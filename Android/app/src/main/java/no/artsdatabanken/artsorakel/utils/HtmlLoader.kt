package no.artsdatabanken.artsorakel.utils

import android.content.Context
import no.artsdatabanken.artsorakel.manager.LanguageManager
import java.io.IOException

/**
 * Utility class for loading HTML content from assets based on language
 */
object HtmlLoader {
    
    /**
     * Loads HTML content from assets based on the current language setting
     * 
     * @param context Application context
     * @param baseName Base name of the HTML file (e.g., "about" for about_en.html)
     * @param languageManager Language manager to determine current language
     * @return HTML content as string, or fallback content if file not found
     */
    fun loadHtml(context: Context, baseName: String, languageManager: LanguageManager): String {
        val languageCode = when (languageManager.getCurrentLanguage()) {
            LanguageManager.SupportedLanguage.ENGLISH -> "en"
            LanguageManager.SupportedLanguage.NORWEGIAN_BOKMAAL -> "nb"
            LanguageManager.SupportedLanguage.NORWEGIAN_NYNORSK -> "nn"
            LanguageManager.SupportedLanguage.DUTCH -> "nl"
            LanguageManager.SupportedLanguage.SPANISH -> "es"
            LanguageManager.SupportedLanguage.SWEDISH -> "sv"
            LanguageManager.SupportedLanguage.SYSTEM -> {
                // Try to determine system language or default to Norwegian Bokmål
                val systemLang = java.util.Locale.getDefault().language
                when (systemLang) {
                    "en" -> "en"
                    "nn" -> "nn"
                    "nl" -> "nl"
                    "nb", "no" -> "nb"
                    "es" -> "es"
                    "sv" -> "sv"
                    else -> "nb" // Default to Norwegian Bokmål
                }
            }
        }
        
        return try {
            // Try to load the specific language file
            val fileName = "${baseName}_${languageCode}.html"
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            try {
                // Fallback to English if specific language not found
                val fallbackFileName = "${baseName}_en.html"
                context.assets.open(fallbackFileName).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                // Final fallback - return error message
                createErrorHtml("Failed to load content: ${e.message}")
            }
        }
    }
    
    /**
     * Creates a simple error HTML page
     */
    private fun createErrorHtml(message: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        padding: 16px;
                        color: #333;
                    }
                </style>
            </head>
            <body>
                <p><strong>Error:</strong> $message</p>
            </body>
            </html>
        """.trimIndent()
    }
}