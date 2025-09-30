package no.artsdatabanken.artsorakel.extensions

import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.MaterialColors

/**
 * Sets the status bar and navigation bar colors and icon appearance based on the current theme.
 * Handles both older Android versions and Android 15+ edge-to-edge.
 */
fun AppCompatActivity.setupStatusBar() {
    val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    
    // Get status bar color from theme attribute
    val statusBarColor = MaterialColors.getColor(this, no.artsdatabanken.artsorakel.R.attr.background_default, 0)
    
    // For Android 15+ (API 35+), we want edge-to-edge, so make status bar transparent
    // For older versions, set the themed color to match app background
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT >= 35) {
        // Edge-to-edge on Android 15+
        window.statusBarColor = android.graphics.Color.TRANSPARENT
    } else {
        // Themed background color for older Android versions
        window.statusBarColor = statusBarColor
    }
    
    // Set icon appearance (light or dark icons)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.isAppearanceLightStatusBars = !isNightMode
    controller.isAppearanceLightNavigationBars = !isNightMode
}