package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.databinding.FragmentSettingsBinding
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.manager.PhotoPermissionStatus
import no.artsdatabanken.artsorakel.manager.ThemeManager
import no.artsdatabanken.artsorakel.utils.SettingsManager
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import javax.inject.Inject
import androidx.core.content.edit
import android.content.SharedPreferences
import no.artsdatabanken.artsorakel.core.Constants

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var themeManager: ThemeManager
    
    @Inject
    lateinit var languageManager: LanguageManager
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    private val mainViewModel: MainViewModel by activityViewModels()

    private var isInitializing = true
    private var isUpdatingFromExternal = false
    private lateinit var sharedPreferencesListener: SharedPreferences.OnSharedPreferenceChangeListener

    companion object {
        fun newInstance() = SettingsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isInitializing = true
        setupWindowInsets()
        setupBackButton()
        setupMenuButton()
        setupThemeSettings()
        setupLanguageSettings()
        setupPermissionSettings()
        setupAdvancedSettings()
        setupPreferenceListener()

        // Allow listeners to fire after initial setup
        view.post {
            isInitializing = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from settings
        updatePermissionStatus()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            view.updatePadding(top = systemBarsInsets.top)
            
            insets
        }
        
        ViewCompat.requestApplyInsets(binding.root)
    }
    
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.navigationManager?.navigateBackFromSettings()
        }
    }
    
    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            (activity as? MainActivity)?.drawerManager?.openDrawer()
        }
    }

    private fun setupThemeSettings() {
        val themeRadioGroup = binding.themeRadioGroup

        val currentThemeId = when (themeManager.getSavedThemeMode()) {
            ThemeManager.ThemeMode.SYSTEM -> R.id.radioThemeSystemDefault
            ThemeManager.ThemeMode.LIGHT -> R.id.radioThemeLight
            ThemeManager.ThemeMode.DARK -> R.id.radioThemeDark
        }
        themeRadioGroup.check(currentThemeId)

        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing || isUpdatingFromExternal) return@setOnCheckedChangeListener
            val selectedTheme = when (checkedId) {
                R.id.radioThemeSystemDefault -> ThemeManager.ThemeMode.SYSTEM
                R.id.radioThemeLight -> ThemeManager.ThemeMode.LIGHT
                R.id.radioThemeDark -> ThemeManager.ThemeMode.DARK
                else -> ThemeManager.ThemeMode.SYSTEM
            }
            

            if (selectedTheme != themeManager.getCurrentThemeMode()) {
                requireContext().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
                    .edit {
                        putBoolean("reopen_settings_after_change", true)
                    }
                themeManager.setThemeMode(selectedTheme)
            }
        }
    }

    private fun setupLanguageSettings() {
        val languageRadioGroup = binding.languageRadioGroup

        val currentLanguageId = when (languageManager.getSavedLanguage()) {
            LanguageManager.SupportedLanguage.SYSTEM -> R.id.radioSystemDefault
            LanguageManager.SupportedLanguage.NORWEGIAN_BOKMAAL -> R.id.radioNorwegianBokmaal
            LanguageManager.SupportedLanguage.NORWEGIAN_NYNORSK -> R.id.radioNorwegianNynorsk
            LanguageManager.SupportedLanguage.ENGLISH -> R.id.radioEnglish
            LanguageManager.SupportedLanguage.DUTCH -> R.id.radioDutch
            LanguageManager.SupportedLanguage.SPANISH -> R.id.radioSpanish
            LanguageManager.SupportedLanguage.SWEDISH -> R.id.radioSwedish
        }
        languageRadioGroup.check(currentLanguageId)

        languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing || isUpdatingFromExternal) return@setOnCheckedChangeListener
            val selectedLanguage = when (checkedId) {
                R.id.radioSystemDefault -> LanguageManager.SupportedLanguage.SYSTEM
                R.id.radioNorwegianBokmaal -> LanguageManager.SupportedLanguage.NORWEGIAN_BOKMAAL
                R.id.radioNorwegianNynorsk -> LanguageManager.SupportedLanguage.NORWEGIAN_NYNORSK
                R.id.radioEnglish -> LanguageManager.SupportedLanguage.ENGLISH
                R.id.radioDutch -> LanguageManager.SupportedLanguage.DUTCH
                R.id.radioSpanish -> LanguageManager.SupportedLanguage.SPANISH
                R.id.radioSwedish -> LanguageManager.SupportedLanguage.SWEDISH

                else -> LanguageManager.SupportedLanguage.SYSTEM
            }
            
            if (selectedLanguage != languageManager.getCurrentLanguage()) {
                val prefs = requireContext().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
                val activity = activity as? MainActivity

                prefs.edit().apply {
                    putBoolean("restore_navigation_after_change", true)

                    when {
                        activity?.supportFragmentManager?.findFragmentById(R.id.speciesDetailsOverlayContainer) != null -> {
                            putString("navigation_state", "species_detail")
                            activity.supportFragmentManager
                                .findFragmentById(R.id.speciesDetailsOverlayContainer)
                                ?.childFragmentManager
                                ?.findFragmentById(R.id.detailsContainer)
                            "species_detail"
                        }
                        parentFragment != null -> {
                            putString("navigation_state", "settings_in_overlay")
                            "settings_in_overlay"
                        }
                        else -> {
                            putString("navigation_state", "settings_only")
                            "settings_only"
                        }
                    }

                    val mainFragment = activity?.supportFragmentManager?.findFragmentById(R.id.fragmentContainer)
                    if (mainFragment is ResultsFragment) {
                        putBoolean("had_results", true)
                    }
                }

                languageManager.setLanguage(selectedLanguage)
            }
        }
    }

    private fun setupPermissionSettings() {
        val mainActivity = activity as? MainActivity ?: return
        val permissionManager = mainActivity.permissionManager

        // Update initial status
        updatePermissionStatus()

        // Photo permission button
        binding.photoPermissionButton.setOnClickListener {
            val status = permissionManager.getPhotoPermissionStatus()
            if (status == PhotoPermissionStatus.LIMITED_ACCESS) {
                // Show dialog with options
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.permission_limited_access_title))
                    .setMessage(getString(R.string.permission_limited_access_message))
                    .setPositiveButton(getString(R.string.permission_select_more_photos)) { _, _ ->
                        permissionManager.requestMorePhotoAccess {
                            updatePermissionStatus()
                        }
                    }
                    .setNeutralButton(getString(R.string.permission_open_settings)) { _, _ ->
                        permissionManager.openAppSettings()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                permissionManager.openAppSettings()
            }
        }

        // Camera permission button
        binding.cameraPermissionButton.setOnClickListener {
            if (!permissionManager.isCameraPermissionGranted()) {
                permissionManager.checkCameraPermissionAndExecute {
                    updatePermissionStatus()
                }
            } else {
                permissionManager.openAppSettings()
            }
        }

        // Location permission button
        binding.locationPermissionButton.setOnClickListener {
            if (!permissionManager.isLocationPermissionGranted()) {
                permissionManager.checkLocationPermissionAndExecute {
                    updatePermissionStatus()
                }
            } else {
                permissionManager.openAppSettings()
            }
        }
    }

    private fun updatePermissionStatus() {
        val mainActivity = activity as? MainActivity ?: return
        val permissionManager = mainActivity.permissionManager

        // Update photo permission status
        val photoStatus = permissionManager.getPhotoPermissionStatus()
        binding.photoPermissionStatus.text = when (photoStatus) {
            PhotoPermissionStatus.FULL_ACCESS -> getString(R.string.permission_photos_full)
            PhotoPermissionStatus.LIMITED_ACCESS -> getString(R.string.permission_photos_limited)
            PhotoPermissionStatus.NO_ACCESS -> getString(R.string.permission_photos_none)
        }
        binding.photoPermissionButton.text = when (photoStatus) {
            PhotoPermissionStatus.NO_ACCESS -> getString(R.string.permission_manage)
            PhotoPermissionStatus.LIMITED_ACCESS -> getString(R.string.permission_change_access)
            PhotoPermissionStatus.FULL_ACCESS -> getString(R.string.permission_manage)
        }

        // Update camera permission status
        val cameraGranted = permissionManager.isCameraPermissionGranted()
        binding.cameraPermissionStatus.text = if (cameraGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_not_granted)
        }

        // Update location permission status
        val locationGranted = permissionManager.isLocationPermissionGranted()
        binding.locationPermissionStatus.text = if (locationGranted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_not_granted)
        }
    }

    private fun setupAdvancedSettings() {
        val saveHistorySwitch = binding.saveHistorySwitch
        val clearHistoryButton = binding.clearHistoryButton
        val useLocationSwitch = binding.useLocationSwitch

        saveHistorySwitch.isChecked = settingsManager.isSaveHistoryEnabled()
        useLocationSwitch.isChecked = settingsManager.isUseLocationForIdEnabled()

        saveHistorySwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSaveHistoryEnabled(isChecked)
        }

        useLocationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setUseLocationForIdEnabled(isChecked)
        }

        clearHistoryButton.setOnClickListener {
            showClearHistoryConfirmation()
        }
    }
    
    private fun showClearHistoryConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.clear_history))
            .setMessage(getString(R.string.clear_history_confirmation_message))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                mainViewModel.clearAllHistory()
                (activity as? MainActivity)?.navigationManager?.navigateBackFromSettings()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupPreferenceListener() {
        val sharedPrefs = requireContext().getSharedPreferences(Constants.Preferences.APP_SETTINGS, android.content.Context.MODE_PRIVATE)

        sharedPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Make sure binding is still valid
            if (_binding == null) return@OnSharedPreferenceChangeListener

            when (key) {
                Constants.Preferences.THEME_KEY -> {
                    isUpdatingFromExternal = true
                    val currentThemeId = when (themeManager.getSavedThemeMode()) {
                        ThemeManager.ThemeMode.SYSTEM -> R.id.radioThemeSystemDefault
                        ThemeManager.ThemeMode.LIGHT -> R.id.radioThemeLight
                        ThemeManager.ThemeMode.DARK -> R.id.radioThemeDark
                    }
                    binding.themeRadioGroup.check(currentThemeId)
                    isUpdatingFromExternal = false
                }
                Constants.Preferences.LANGUAGE_KEY -> {
                    isUpdatingFromExternal = true
                    val currentLanguageId = when (languageManager.getSavedLanguage()) {
                        LanguageManager.SupportedLanguage.SYSTEM -> R.id.radioSystemDefault
                        LanguageManager.SupportedLanguage.NORWEGIAN_BOKMAAL -> R.id.radioNorwegianBokmaal
                        LanguageManager.SupportedLanguage.NORWEGIAN_NYNORSK -> R.id.radioNorwegianNynorsk
                        LanguageManager.SupportedLanguage.ENGLISH -> R.id.radioEnglish
                        LanguageManager.SupportedLanguage.DUTCH -> R.id.radioDutch
                        LanguageManager.SupportedLanguage.SPANISH -> R.id.radioSpanish
                        LanguageManager.SupportedLanguage.SWEDISH -> R.id.radioSwedish

                    }
                    binding.languageRadioGroup.check(currentLanguageId)
                    isUpdatingFromExternal = false
                }
            }
        }

        sharedPrefs.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val sharedPrefs = requireContext().getSharedPreferences(Constants.Preferences.APP_SETTINGS, android.content.Context.MODE_PRIVATE)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener)
        _binding = null
    }
}