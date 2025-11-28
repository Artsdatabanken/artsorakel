package no.artsdatabanken.artsorakel.fragments

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.utils.HtmlLoader
import no.artsdatabanken.artsorakel.utils.SettingsManager
import javax.inject.Inject

@AndroidEntryPoint
class DisclaimerDialogFragment : DialogFragment() {

    @Inject
    lateinit var languageManager: LanguageManager
    
    @Inject
    lateinit var settingsManager: SettingsManager

    private var onDisclaimerAccepted: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_disclaimer, null)
        
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val contentWebView = dialogView.findViewById<WebView>(R.id.disclaimerContent)
        val understoodButton = dialogView.findViewById<MaterialButton>(R.id.understoodButton)
        
        // Set title
        titleView.text = getString(R.string.disclaimer_title)
        
        // Configure WebView for optimal performance
        contentWebView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        
        // Get theme colors and set WebView background
        val themeColors = getThemeColors()
        contentWebView.setBackgroundColor(themeColors.backgroundColor)
        
        // Create dialog but don't show it yet
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false) // User must acknowledge the disclaimer
            .create()
        
        // Initially hide the dialog window
        dialog.window?.decorView?.visibility = View.INVISIBLE
        
        // Set up WebView client to detect when content is loaded
        contentWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Small delay to ensure rendering is complete
                Handler(Looper.getMainLooper()).postDelayed({
                    // Make dialog visible with fade-in effect
                    dialog.window?.decorView?.apply {
                        alpha = 0f
                        visibility = View.VISIBLE
                        animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                }, 50)
            }
        }
        
        // Load and display HTML content with styling
        try {
            val htmlContent = HtmlLoader.loadHtml(requireContext(), "disclaimer", languageManager)
            val processedContent = inlineSvgImages(htmlContent, themeColors)
            val styledHtmlContent = injectAppStyling(processedContent, themeColors)
            contentWebView.loadDataWithBaseURL(
                "file:///android_asset/", 
                styledHtmlContent, 
                "text/html; charset=utf-8", 
                "UTF-8", 
                null
            )
        } catch (e: Exception) {
            // Fallback to basic HTML if loading fails
            val fallbackHtml = injectAppStyling("<p></p>", themeColors)
            contentWebView.loadDataWithBaseURL(
                "file:///android_asset/", 
                fallbackHtml, 
                "text/html; charset=utf-8", 
                "UTF-8", 
                null
            )
        }
        
        // Set up understood button
        understoodButton.setOnClickListener {
            // Mark disclaimer as seen
            settingsManager.markDisclaimerSeen()
            
            // Notify that disclaimer was accepted
            onDisclaimerAccepted?.invoke()
            
            // Dismiss dialog
            dialog.dismiss()
        }
        
        return dialog
    }
    
    /**
     * Data class to hold theme colors
     */
    data class ThemeColors(
        val backgroundColor: Int,
        val textColor: Int
    )
    
    /**
     * Get current theme colors from attributes
     */
    private fun getThemeColors(): ThemeColors {
        val typedValue = TypedValue()
        val theme = requireContext().theme
        
        // Get background color using background_default
        val backgroundColor = if (theme.resolveAttribute(R.attr.background_default, typedValue, true)) {
            typedValue.data
        } else {
            // Fallback to background_subtle if background_default doesn't exist
            if (theme.resolveAttribute(R.attr.background_subtle, typedValue, true)) {
                typedValue.data
            } else {
                ContextCompat.getColor(requireContext(), android.R.color.background_light)
            }
        }
        
        // Get text color using text_primary
        val textColor = if (theme.resolveAttribute(R.attr.text_primary, typedValue, true)) {
            typedValue.data
        } else {
            // Fallback to black text if theme attribute not found
            Color.BLACK
        }
        
        return ThemeColors(backgroundColor, textColor)
    }
    
    /**
     * Inline SVG images so they can inherit CSS color via currentColor
     */
    private fun inlineSvgImages(htmlContent: String, themeColors: ThemeColors): String {
        val textColorHex = String.format("#%06X", (0xFFFFFF and themeColors.textColor))
        var result = htmlContent

        // Find all <img src="file:///android_asset/...svg"> tags and replace with inline SVG
        val imgPattern = """<img\s+src="file:///android_asset/([^"]+\.svg)"([^>]*)/>""".toRegex()

        imgPattern.findAll(htmlContent).forEach { match ->
            val svgFileName = match.groupValues[1]
            val imgAttributes = match.groupValues[2]

            try {
                val svgContent = requireContext().assets.open(svgFileName).bufferedReader().use { it.readText() }

                // Extract style from img tag if present
                val styleMatch = """style="([^"]*)"""".toRegex().find(imgAttributes)
                val style = styleMatch?.groupValues?.get(1) ?: ""

                // Remove XML declaration and add color style to SVG tag
                val cleanedSvg = svgContent
                    .replace("""<\?xml[^>]+\?>""".toRegex(), "")
                    .replace("""<svg""".toRegex(), """<svg style="color: $textColorHex; $style" """)
                    .trim()

                val inlinedSvg = """<div style="text-align: center;">$cleanedSvg</div>"""

                result = result.replace(match.value, inlinedSvg)
            } catch (e: Exception) {
                // If we can't load the SVG, keep the original img tag
            }
        }

        return result
    }

    /**
     * Inject app styling into HTML content with actual theme colors
     */
    private fun injectAppStyling(htmlContent: String, themeColors: ThemeColors): String {
        val backgroundColorHex = String.format("#%06X", (0xFFFFFF and themeColors.backgroundColor))
        val textColorHex = String.format("#%06X", (0xFFFFFF and themeColors.textColor))
        
        val css = """
            <style>
                @font-face {
                    font-family: 'Chivo';
                    src: url('file:///android_res/font/chivo_regular.ttf');
                    font-weight: normal;
                    font-style: normal;
                    font-display: swap;
                }
                
                @font-face {
                    font-family: 'Chivo';
                    src: url('file:///android_res/font/chivo_bold.ttf');
                    font-weight: bold;
                    font-style: normal;
                    font-display: swap;
                }
                
                body {
                    font-family: 'Chivo', Arial, sans-serif;
                    font-size: 14px;
                    line-height: 1.5;
                    color: $textColorHex;
                    margin: 0;
                    padding: 8px;
                    background-color: $backgroundColorHex;
                }
                
                p {
                    margin-bottom: 8px;
                    text-align: left;
                    color: $textColorHex;
                }
                
                strong {
                    font-weight: bold;
                    font-family: 'Chivo', Arial, sans-serif;
                    color: $textColorHex;
                }
            </style>
        """.trimIndent()
        
        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light dark">
                $css
            </head>
            <body>
                $htmlContent
            </body>
            </html>
        """.trimIndent()
    }
    
    override fun onStart() {
        super.onStart()
        // Set dialog size with proper constraints
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val maxHeight = (screenHeight * 0.9).toInt() // Use 90% of screen height max
        
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            minOf(ViewGroup.LayoutParams.WRAP_CONTENT, maxHeight)
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
    
    /**
     * Set callback for when disclaimer is accepted
     */
    fun setOnDisclaimerAccepted(callback: () -> Unit) {
        onDisclaimerAccepted = callback
    }
    
    companion object {
        fun newInstance(): DisclaimerDialogFragment {
            return DisclaimerDialogFragment()
        }
    }
}