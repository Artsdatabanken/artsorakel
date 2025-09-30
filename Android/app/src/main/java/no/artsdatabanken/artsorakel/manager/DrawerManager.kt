package no.artsdatabanken.artsorakel.manager

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.utils.Logger
import javax.inject.Inject


/**
 * Manages navigation drawer functionality including opening/closing and menu item interactions.
 */
class DrawerManager @Inject constructor(
    private val themeManager: ThemeManager,
    private val languageManager: LanguageManager
) {
    
    private lateinit var activity: AppCompatActivity
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    /**
     * Initialize the DrawerManager with activity context and drawer components.
     */
    fun initialize(
        activity: AppCompatActivity,
        drawerLayout: DrawerLayout,
        navigationView: NavigationView
    ) {
        this.activity = activity
        this.drawerLayout = drawerLayout
        this.navigationView = navigationView
        
        // Set DrawerLayout status bar background for Material Design drawer behavior
        val isNightMode = (activity.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
            
        val statusBarBackgroundColor = if (isNightMode) {
            ContextCompat.getColor(activity, R.color.ocean_90)
        } else {
            ContextCompat.getColor(activity, R.color.ocean_90)
        }
        
        drawerLayout.setStatusBarBackgroundColor(statusBarBackgroundColor)
        
        setupNavigationView()
    }
    
    /**
     * Opens the navigation drawer from the right side.
     */
    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.END)
    }
    
    /**
     * Closes the navigation drawer.
     */
    fun closeDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        }
    }
    
    /**
     * Toggles the navigation drawer (open/close).
     */
    fun toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            closeDrawer()
        } else {
            openDrawer()
        }
    }
    
    private fun setupNavigationView() {
        // Set up click listeners for custom bottom-aligned layout items
        val themeItem = navigationView.findViewById<View>(R.id.nav_theme)
        val languageItem = navigationView.findViewById<View>(R.id.nav_language)
        val settingsItem = navigationView.findViewById<View>(R.id.nav_settings)
        val aboutItem = navigationView.findViewById<View>(R.id.nav_about)
        val faqItem = navigationView.findViewById<View>(R.id.nav_faq)
        val closeButton = navigationView.findViewById<View>(R.id.nav_close)
        val bottomMenuContainer = navigationView.findViewById<LinearLayout>(R.id.bottom_menu_container)

        
        themeItem?.setOnClickListener {
            showThemeDialog()
        }
        
        languageItem?.setOnClickListener {
            showLanguageDialog()
        }
        
        settingsItem?.setOnClickListener {
            showSettingsDialog()
        }
        
        aboutItem?.setOnClickListener {
            showAboutDialog()
        }
        
        faqItem?.setOnClickListener {
            showFaqDialog()
        }

        closeButton?.setOnClickListener {
            closeDrawer()
        }

        // Handle system window insets for Material Design drawer behavior
        ViewCompat.setOnApplyWindowInsetsListener(navigationView) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Add top padding to push content below status bar
            // Add bottom padding to avoid navigation bar overlap  
            view.updatePadding(
                top = systemBarsInsets.top,
                bottom = systemBarsInsets.bottom
            )
            
            // Debug logging
            Logger.d("WindowInsets", 
                "Drawer NavigationView - Top: ${systemBarsInsets.top}, Bottom: ${systemBarsInsets.bottom}")
            
            insets
        }
        
        // Add base padding to the bottom menu container for visual spacing
        bottomMenuContainer?.updatePadding(
            bottom = try {
                activity.resources.getDimensionPixelSize(R.dimen.drawer_menu_bottom_padding)
            } catch (_: Exception) {
                (16 * activity.resources.displayMetrics.density).toInt() // 16dp fallback
            }
        )
        
        // Request insets to be applied
        ViewCompat.requestApplyInsets(navigationView)
    }
    
    private fun showThemeDialog() {
        val themeOptions = arrayOf(
            activity.getString(R.string.system_default),
            activity.getString(R.string.theme_light),
            activity.getString(R.string.theme_dark)
        )
        
        val currentTheme = when (themeManager.getCurrentThemeMode()) {
            ThemeManager.ThemeMode.SYSTEM -> 0
            ThemeManager.ThemeMode.LIGHT -> 1
            ThemeManager.ThemeMode.DARK -> 2
        }


        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.theme_theme))
            .setSingleChoiceItems(themeOptions, currentTheme) { dialog, which ->
                val selectedTheme = when (which) {
                    0 -> ThemeManager.ThemeMode.SYSTEM
                    1 -> ThemeManager.ThemeMode.LIGHT
                    2 -> ThemeManager.ThemeMode.DARK
                    else -> ThemeManager.ThemeMode.SYSTEM
                }
                themeManager.setThemeMode(selectedTheme)
                dialog.dismiss()
                closeDrawer()
            }
            .setNegativeButton(activity.getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showLanguageDialog() {
        val languageOptions = arrayOf(
            activity.getString(R.string.system_default),
            activity.getString(R.string.language_norwegian_bokmaal),
            activity.getString(R.string.language_norwegian_nynorsk),
            activity.getString(R.string.language_english),
            activity.getString(R.string.language_spanish),
            activity.getString(R.string.language_dutch),
            activity.getString(R.string.language_swedish),
        )
        
        val currentLanguage = when (languageManager.getCurrentLanguage()) {
            LanguageManager.SupportedLanguage.SYSTEM -> 0
            LanguageManager.SupportedLanguage.NORWEGIAN_BOKMAAL -> 1
            LanguageManager.SupportedLanguage.NORWEGIAN_NYNORSK -> 2
            LanguageManager.SupportedLanguage.ENGLISH -> 3
            LanguageManager.SupportedLanguage.SPANISH -> 4
            LanguageManager.SupportedLanguage.DUTCH -> 5
            LanguageManager.SupportedLanguage.SWEDISH -> 6
        }
        
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.language_language))
            .setSingleChoiceItems(languageOptions, currentLanguage) { dialog, which ->
                val selectedLanguage = when (which) {
                    0 -> LanguageManager.SupportedLanguage.SYSTEM
                    1 -> LanguageManager.SupportedLanguage.NORWEGIAN_BOKMAAL
                    2 -> LanguageManager.SupportedLanguage.NORWEGIAN_NYNORSK
                    3 -> LanguageManager.SupportedLanguage.ENGLISH
                    4 -> LanguageManager.SupportedLanguage.SPANISH
                    5 -> LanguageManager.SupportedLanguage.DUTCH
                    6 -> LanguageManager.SupportedLanguage.SWEDISH

                    else -> LanguageManager.SupportedLanguage.SYSTEM
                }
                languageManager.setLanguage(selectedLanguage)
                dialog.dismiss()
                closeDrawer()
            }
            .setNegativeButton(activity.getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showSettingsDialog() {
        // Navigate to settings fragment instead of showing a dialog
        val mainActivity = activity as? MainActivity
        mainActivity?.navigationManager?.showSettingsFragment()
        closeDrawer()
    }
    
    private fun showAboutDialog() {
        val mainActivity = activity as? MainActivity
        mainActivity?.navigationManager?.showAboutFragment()
        closeDrawer()
    }

    private fun showFaqDialog() {
        val mainActivity = activity as? MainActivity
        mainActivity?.navigationManager?.showFaqFragment()
        closeDrawer()
    }

} 