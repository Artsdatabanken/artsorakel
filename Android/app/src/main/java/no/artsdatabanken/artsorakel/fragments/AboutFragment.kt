package no.artsdatabanken.artsorakel.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.databinding.FragmentAboutBinding
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.utils.HtmlLoader
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var languageManager: LanguageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackButton()
        setupMenuButton()
        setupWebView()
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.navigationManager?.navigateBackFromSettings()
        }
    }

    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            (activity as? MainActivity)?.drawerManager?.toggleDrawer()
        }
    }

    private fun setupWebView() {
        val webView = binding.aboutWebView

        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            blockNetworkImage = false
            loadsImagesAutomatically = true
        }

        val themeColors = getThemeColors()
        webView.setBackgroundColor(Color.TRANSPARENT)

        val htmlContent = HtmlLoader.loadHtml(requireContext(), "about", languageManager)
        val processedContent = processContent(htmlContent)
        val styledHtmlContent = injectAppStyling(processedContent, themeColors)

        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            styledHtmlContent,
            "text/html; charset=utf-8",
            "UTF-8",
            null
        )
    }

    private fun processContent(htmlContent: String): String {
        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionName = packageInfo.versionName ?: "Unknown"
        return htmlContent.replace("[Version number]", versionName)
    }

    data class ThemeColors(
        val backgroundColor: Int,
        val textColor: Int,
        val linkColor: Int
    )

    private fun getThemeColors(): ThemeColors {
        val typedValue = TypedValue()
        val theme = requireContext().theme

        val backgroundColor = if (theme.resolveAttribute(R.attr.background_subtle, typedValue, true)) {
            typedValue.data
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.background_light)
        }

        val textColor = if (theme.resolveAttribute(R.attr.text_primary, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLACK
        }

        val linkColor = if (theme.resolveAttribute(R.attr.text_accent, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLUE
        }

        return ThemeColors(backgroundColor, textColor, linkColor)
    }

    private fun injectAppStyling(htmlContent: String, themeColors: ThemeColors): String {
        val css = getOptimizedCSS(themeColors)

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

    private fun getOptimizedCSS(themeColors: ThemeColors): String {
        val backgroundColorHex = String.format("#%06X", (0xFFFFFF and themeColors.backgroundColor))
        val textColorHex = String.format("#%06X", (0xFFFFFF and themeColors.textColor))
        val linkColorHex = String.format("#%06X", (0xFFFFFF and themeColors.linkColor))

        return """
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
                    padding: 16px;
                    background-color: $backgroundColorHex;
                    opacity: 1;
                }

                h3 {
                    color: $textColorHex;
                    opacity: 0.9;
                    margin-top: 0;
                    margin-bottom: 16px;
                    font-family: 'Chivo', Arial, sans-serif;
                    font-weight: bold;
                }

                p {
                    margin-bottom: 12px;
                    text-align: left;
                    color: $textColorHex;
                }

                strong {
                    font-weight: bold;
                    font-family: 'Chivo', Arial, sans-serif;
                    color: $textColorHex;
                }

                a {
                    color: $linkColorHex;
                    text-decoration: underline;
                }

                a:visited {
                    color: $linkColorHex;
                }

                a:hover, a:active {
                    color: $linkColorHex;
                    text-decoration: underline;
                }

                .logo {
                    display: block;
                    margin: 20px auto 0 auto;
                    width: 100%;
                    height: auto;
                    max-width: 200px;
                }
            </style>
        """.trimIndent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}