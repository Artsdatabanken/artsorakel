package no.artsdatabanken.artsorakel.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.core.AppConfig
import no.artsdatabanken.artsorakel.core.Constants
import no.artsdatabanken.artsorakel.core.FragmentEvent
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import no.artsdatabanken.artsorakel.databinding.ActivityMainBinding
import no.artsdatabanken.artsorakel.extensions.setupStatusBar
import no.artsdatabanken.artsorakel.fragments.DisclaimerDialogFragment
import no.artsdatabanken.artsorakel.fragments.InputImagesFragment
import no.artsdatabanken.artsorakel.fragments.MainScreenFragment
import no.artsdatabanken.artsorakel.fragments.ResultsFragment
import no.artsdatabanken.artsorakel.fragments.SpeciesDetailFragment
import no.artsdatabanken.artsorakel.model.GeoLocation
import no.artsdatabanken.artsorakel.manager.DrawerManager
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.manager.NavigationManager
import no.artsdatabanken.artsorakel.manager.NavigationStackManager
import no.artsdatabanken.artsorakel.manager.PermissionManager
import no.artsdatabanken.artsorakel.manager.ThemeManager
import no.artsdatabanken.artsorakel.utils.GalleryImageSaver
import no.artsdatabanken.artsorakel.utils.ImageOperationsManager
import no.artsdatabanken.artsorakel.utils.SafeDialogPresenter
import no.artsdatabanken.artsorakel.utils.SettingsManager
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import no.artsdatabanken.artsorakel.viewmodel.UiState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Manager classes injected by Hilt
    @Inject lateinit var settingsManager: SettingsManager
    @Inject lateinit var drawerManager: DrawerManager
    @Inject lateinit var navigationManager: NavigationManager
    @Inject lateinit var navigationStackManager: NavigationStackManager
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var languageManager: LanguageManager
    private lateinit var imageOperationsManager: ImageOperationsManager
    private lateinit var galleryImageSaver: GalleryImageSaver

    // Activity result launchers
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var imageCropperLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>


    
    // Timeout functionality
    private var timeoutJob: Job? = null
    
    // Last input method now tracked in ViewModel
    
    // Prevent concurrent sharing operations
    private var isProcessingSharedImage = false
    
    // Original layout heights for window insets handling - calculated once to prevent accumulation
    private var headerOriginalHeight: Int = 0
    private var bottomBarOriginalHeight: Int = 0
    private var bottomBarOriginalPaddingBottom: Int = 0

    // --- Lifecycle Methods ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply preferences after super.onCreate (when Hilt injection is complete)
        settingsManager.applySavedSettings()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupResultLaunchers()
        
        // Restore navigation state before initializing managers
        savedInstanceState?.let {
            val navState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable("nav_state", no.artsdatabanken.artsorakel.model.NavigationState::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable("nav_state") as? no.artsdatabanken.artsorakel.model.NavigationState
            }
            navState?.let { state ->
                navigationStackManager.restoreFromState(state)
            }
        }
        
        initializeManagers()
        setupClickListeners()
        setupBackPressedCallback()
        initializeFragments()
        // Input method preference handled by ViewModel
        observeViewModel()
        
        checkAndReopenSettings()
        
        checkAndShowDisclaimer()
        
        handleSharedImage(intent)
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val navState = navigationStackManager.saveState()
        outState.putSerializable("nav_state", navState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle shared image when app is already running and receives a share intent
        handleSharedImage(intent)
    }

    // Let AppCompat handle locale/theme changes by recreating activity automatically
    
    override fun onResume() {
        super.onResume()
        // Ensure status bar is properly configured after theme changes or app resume
        setupStatusBar()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cancelTimeoutTimer()
    }
    
    private fun setupBackPressedCallback() {
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Close drawer if open
                if (::drawerManager.isInitialized && binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerManager.closeDrawer()
                    return
                }
                
                // Handle back press based on current app state and visible fragments
                handleDeviceBackPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }
    
    private fun handleDeviceBackPress() {
        // Use the stack-based navigation to pop the topmost overlay
        if (navigationManager.popTopmostOverlay()) {
            updateCameraButtonVisibility()
            return
        }
        
        // Check for dialog fragments (FAQ, About, Disclaimer)
        val dialogFragment = supportFragmentManager.fragments.find { 
            it.isVisible && it is DialogFragment 
        } as? DialogFragment
        if (dialogFragment != null) {
            dialogFragment.dismiss()
            return
        }
        
        // Get the currently visible fragment in main container
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        
        when (currentFragment) {
            is SpeciesDetailFragment -> {
                // Trigger the same action as the back button in species detail
                viewModel.handleFragmentEvent(FragmentEvent.NavigateBack)
            }
            is ResultsFragment -> {
                // Check if this is historical results or new results
                val isHistorical = try {
                    currentFragment.isHistoricalResults()
                } catch (_: Exception) {
                    false // Default to non-historical if there's an error
                }
                
                if (isHistorical) {
                    // Historical results - go back to history list (same as back button)
                    viewModel.handleFragmentEvent(FragmentEvent.BackToHistoryList)
                } else {
                    // New results - trigger "identify new species" behavior (same as reset button)
                    viewModel.handleFragmentEvent(FragmentEvent.ResetApp)
                }
            }
            is MainScreenFragment -> {
                // History is now handled via overlays, so just exit app
                finish()
            }
            else -> {
                // Default behavior for any other state - exit app
                finish()
            }
        }
    }

    // --- Setup Methods ---

    private fun setupWindowInsets() {
        // Initialize original values only once to prevent accumulation during theme changes
        if (headerOriginalHeight == 0) {
            headerOriginalHeight = (64 * resources.displayMetrics.density).toInt() // 64dp
        }
        if (bottomBarOriginalHeight == 0) {
            bottomBarOriginalHeight = (148 * resources.displayMetrics.density).toInt() // 148dp
        }
        if (bottomBarOriginalPaddingBottom == 0) {
            bottomBarOriginalPaddingBottom = (16 * resources.displayMetrics.density).toInt() // 16dp from paddingVertical
        }
        
        // Simple window insets handling - adjust heights and padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootConstraint) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Expand container heights to include system bar space using stored original heights
            val headerLayoutParams = binding.headerRow.layoutParams
            headerLayoutParams.height = headerOriginalHeight + insets.top
            binding.headerRow.layoutParams = headerLayoutParams
            
            // Update bottom bar if visible - preserve original padding and add insets
            val bottomBarLayoutParams = binding.BottomButtonBar.layoutParams
            bottomBarLayoutParams.height = bottomBarOriginalHeight + insets.bottom
            binding.BottomButtonBar.layoutParams = bottomBarLayoutParams
            binding.BottomButtonBar.updatePadding(bottom = bottomBarOriginalPaddingBottom + insets.bottom)
            
            // Apply padding to position content correctly within expanded containers
            binding.headerRow.updatePadding(top = insets.top)
            
            // Apply side padding for display cutouts if any
            view.updatePadding(
                left = insets.left,
                right = insets.right
            )
            
            windowInsets
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            // Camera button on main screen
            fabCameraButton.setOnClickListener {
                viewModel.setLastInputMethod(true)
                permissionManager.checkCameraPermissionAndExecute {
                    permissionManager.checkLocationPermissionAndExecute {
                        imageOperationsManager.launchCamera()
                    }
                }
            }
            
            // Gallery button on main screen
            fabCameraGallery.setOnClickListener {
                viewModel.setLastInputMethod(false)
                permissionManager.checkStoragePermissionAndExecute { 
                    imageOperationsManager.launchImagePicker() 
                }
            }

            buttonMenu.setOnClickListener { 
                drawerManager.toggleDrawer()
            }
            
            buttonAbort.setOnClickListener {
                handleAbortClick()
            }
            
            // Header back button
            imageButtonHeaderBack.setOnClickListener {
                handleHeaderBackClick()
            }
        }
    }

    private fun setupResultLaunchers() {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (::permissionManager.isInitialized) {
                permissionManager.handlePermissionResult(isGranted)
            }
        }

        // Multi-permission launcher for Android 14+ photo access re-request
        requestMultiplePermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (::permissionManager.isInitialized) {
                // Consider granted if any permission was granted
                val anyGranted = permissions.values.any { it }
                permissionManager.handlePermissionResult(anyGranted)
            }
        }

        // Use OpenDocument instead of GetContent to preserve EXIF data
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val location = imageOperationsManager.extractLocationFromImage(it)
                startImageCropper(it, location)
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                if (::imageOperationsManager.isInitialized) {
                    imageOperationsManager.tempImageUri?.let { tempUri ->
                        // Write location to the temp image
                        imageOperationsManager.writeLocationToTempImage()

                        // Get the location for passing to cropper
                        val location = imageOperationsManager.pendingCameraLocation

                        // Save original image to gallery first
                        saveOriginalImageToGallery(tempUri) {
                            // Then proceed to cropping interface
                            startImageCropper(tempUri, location)
                        }
                    } ?: Toast.makeText(this, "Failed to get camera image URI", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Camera capture failed or cancelled", Toast.LENGTH_SHORT).show()
                if (::imageOperationsManager.isInitialized) {
                    imageOperationsManager.clearTempUri()
                }
            }
        }

        imageCropperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    handleImageCropperResult(data)
                }
            }
            if (::imageOperationsManager.isInitialized) {
                imageOperationsManager.clearTempUri()
            }
            // Reset shared image processing flag
            isProcessingSharedImage = false
        }
    }

    private fun initializeManagers() {
        // Initialize managers that need activity context/launchers
        navigationManager.initialize(this)
        permissionManager.initialize(this, requestPermissionLauncher, requestMultiplePermissionsLauncher)
        drawerManager.initialize(this, binding.drawerLayout, binding.navView)
        imageOperationsManager = ImageOperationsManager(this, takePictureLauncher, pickImageLauncher)
        galleryImageSaver = GalleryImageSaver(this)
        
        // Set up drawer menu click listeners
        setupDrawerClickListeners()
        
        // Set up event observation
        observeFragmentEvents()
    }
    
    private fun setupDrawerClickListeners() {

    }

    private fun observeFragmentEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fragmentEvents.collect { event ->
                    handleFragmentEvent(event)
                }
            }
        }
    }

    // --- ViewModel Observation ---

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    // --- Consolidated UI Update Method ---

    private fun updateUI(uiState: UiState = viewModel.uiState.value) {

        // History expansion is now handled via overlays, not inline

        binding.apply {
            // Handle state-dependent UI
            when (uiState) {
                is UiState.Loading -> {
                    progressBarResults.visibility = View.VISIBLE
                    fragmentContainer.visibility = View.GONE
                    timeoutContainer.visibility = View.GONE
                    // Start timeout timer
                    startTimeoutTimer()
                }
                is UiState.Success -> {
                    progressBarResults.visibility = View.GONE
                    timeoutContainer.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    cancelTimeoutTimer()
                }
                is UiState.Error -> {
                    progressBarResults.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    timeoutContainer.visibility = View.GONE
                    // Cancel timeout timer
                    cancelTimeoutTimer()

                    Toast.makeText(this@MainActivity, uiState.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    progressBarResults.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    timeoutContainer.visibility = View.GONE
                    // Cancel timeout timer
                    cancelTimeoutTimer()

                    // Navigate away from results fragment when state becomes Idle
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    if (currentFragment is ResultsFragment) {
                        val hasImages = viewModel.selectedImageUris.value.isNotEmpty()
                        if (hasImages) {
                            navigationManager.showInputImagesFragment()
                        } else {
                            navigationManager.showMainScreen()
                        }
                    }
                }
            }
        }

        // Update camera button visibility based on current fragment
        updateCameraButtonVisibility()
    }
    
    /**
     * Simple rule: Show camera buttons only on MainScreenFragment or InputImagesFragment
     * AND not during loading or when showing results
     */
    private fun updateCameraButtonVisibility() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val currentState = viewModel.uiState.value
        
        // Hide during loading
        if (currentState is UiState.Loading) {
            binding.BottomButtonBar.visibility = View.GONE
            return
        }
        
        // Hide when showing non-historical results (for historical, we're in overlay so main screen shows)
        if (currentState is UiState.Success && currentState.results.isNotEmpty() && !currentState.isHistorical) {
            binding.BottomButtonBar.visibility = View.GONE
            return
        }
        
        // Otherwise, show only on main screen or image management screen
        binding.BottomButtonBar.visibility = when (currentFragment) {
            is MainScreenFragment, is InputImagesFragment -> View.VISIBLE
            else -> View.GONE
        }
    }

    // --- Fragment Navigation Methods (delegated to NavigationManager) ---

    // --- Fragment Event Handler ---

    private fun handleFragmentEvent(event: FragmentEvent) {
        when (event) {
            is FragmentEvent.IdentifySpecies -> {
                handleIdentifyClick()
            }
            is FragmentEvent.ResetApp -> {
                handleResetApp()
            }
            is FragmentEvent.ViewImage -> {
                val imagePair = viewModel.selectedImagePairs.value.find { it.croppedUri == event.uri }
                imagePair?.let { pair ->
                    startImageCropper(pair.originalUri, pair.location, true, pair.croppedUri)
                }
            }
            is FragmentEvent.AddImage -> {
                // Launch camera or gallery based on last used method
                if (viewModel.getLastInputMethod()) {
                    permissionManager.checkCameraPermissionAndExecute {
                        // Also request location permission for geotagging
                        permissionManager.checkLocationPermissionAndExecute {
                            imageOperationsManager.launchCamera()
                        }
                    }
                } else {
                    permissionManager.checkStoragePermissionAndExecute { 
                        imageOperationsManager.launchImagePicker() 
                    }
                }
            }
            is FragmentEvent.SelectSpecies -> {
                navigationManager.handleSpeciesItemClick(event.result)
            }
            is FragmentEvent.ResultsReady -> {
                // Navigate to results when they're ready
                val currentState = viewModel.uiState.value
                // Only navigate if we have actual results
                if (currentState is UiState.Success && currentState.results.isNotEmpty()) {
                    if (currentState.isHistorical) {
                        navigationManager.showHistoricalResultsFragment(binding.fragmentContainer)
                    } else {
                        navigationManager.showResultsFragment(binding.fragmentContainer)
                    }
                    binding.fragmentContainer.visibility = View.VISIBLE
                    updateCameraButtonVisibility()
                }
            }
            is FragmentEvent.NavigateBack -> {
                navigationManager.navigateBackFromSpeciesDetail()
                // Restore bottom button bar visibility based on current state
                updateUI()
            }
            is FragmentEvent.ViewHistoryResults -> {
                // Navigation will happen when ResultsReady event is received
            }
            is FragmentEvent.BackToHistoryList -> {
                // Navigate back to expanded history list from historical results
                if (supportFragmentManager.backStackEntryCount > 0) {
                    // Clear the historical UI state first
                    viewModel.clearHistoricalState()
                    // Then pop back to the main screen
                    supportFragmentManager.popBackStack()
                    updateCameraButtonVisibility()
                    // Show the expanded history overlay
                    navigationManager.showExpandedHistory()
                } else {
                    // Fallback: show expanded history overlay
                    navigationManager.showExpandedHistory()
                }
            }
        }
    }

    // --- Action Handlers ---

    private fun handleIdentifyClick() {
        // Defensive null checks and validation
        if (isFinishing || isDestroyed) {
            return
        }
        
        val imageUris = viewModel.selectedImageUris.value
        if (imageUris.isNotEmpty()) {
            viewModel.classifyImagesFromUris(this)
        } else {
            Toast.makeText(this, "Please add images first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleResetApp() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerManager.closeDrawer()
        }

        supportFragmentManager.fragments.filterIsInstance<DialogFragment>().forEach {
            it.dismiss()
        }

        navigationStackManager.clear()

        if (navigationManager.isSpeciesDetailFragmentVisible()) {
            navigationManager.navigateBackFromSpeciesDetail()
        }

        navigationManager.showMainScreen()
        updateCameraButtonVisibility()

        viewModel.clearImages()
    }
    
    private fun handleAbortClick() {
        viewModel.abortClassification()
        cancelTimeoutTimer()
        updateUI(UiState.Idle)
    }
    
    private fun startTimeoutTimer() {
        // Cancel any existing timeout timer
        cancelTimeoutTimer()
        
        timeoutJob = lifecycleScope.launch {
            delay(AppConfig.Timeouts.TIMEOUT_SHOW_MESSAGE_MS) // Delay before showing timeout UI
            
            if (viewModel.uiState.value is UiState.Loading) {
                binding.timeoutContainer.visibility = View.VISIBLE
            }
        }
    }
    
    private fun cancelTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }




    private fun handleImageCropperResult(data: Intent?) {
        // Defensive null checks
        if (data == null || isFinishing || isDestroyed) {
            return
        }
        
        try {
            // Check if this is a deletion request
            if (data.getBooleanExtra("delete_image", false)) {
                val originalUriString = data.getStringExtra(Constants.IntentExtras.EXTRA_ORIGINAL_IMAGE_URI)
                if (!originalUriString.isNullOrBlank()) {
                    val originalUri = originalUriString.toUri()
                    val imagePair = viewModel.selectedImagePairs.value.find { it.originalUri == originalUri }
                    imagePair?.let { pair ->
                        viewModel.removeImagePair(pair)
                    }
                }
                return
            }

            val croppedUri = data.data
            val originalUriString = data.getStringExtra(Constants.IntentExtras.EXTRA_ORIGINAL_IMAGE_URI)
            val isRecrop = data.getBooleanExtra("recrop", false)
            val oldCroppedUriString = data.getStringExtra("old_cropped_uri")

            // Extract location data from intent
            val locationLat = data.getDoubleExtra("location_lat", Double.NaN)
            val locationLon = data.getDoubleExtra("location_lon", Double.NaN)
            val locationAlt = data.getDoubleExtra("location_alt", Double.NaN)

            val location = if (!locationLat.isNaN() && !locationLon.isNaN()) {
                GeoLocation(
                    latitude = locationLat,
                    longitude = locationLon,
                    altitude = if (locationAlt.isNaN()) null else locationAlt
                )
            } else {
                null
            }

            if (croppedUri != null && !originalUriString.isNullOrBlank()) {
                val originalUri = originalUriString.toUri()
                val oldCroppedUri = oldCroppedUriString?.toUri()
                viewModel.addImagePair(croppedUri, originalUri, location, isRecrop, oldCroppedUri)
                // Explicitly navigate to input images fragment after adding an image
                navigationManager.showInputImagesFragment()
                updateCameraButtonVisibility()
            }
        } catch (e: Exception) {
            // Log error and show user-friendly message
            val error = ErrorMapper.mapException(e)
            error.logError("MainActivity")
            Toast.makeText(this, "Failed to process image result", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startImageCropper(sourceUri: Uri, location: GeoLocation? = null, isRecropping: Boolean = false, oldCroppedUri: Uri? = null) {
        val intent = Intent(this, ImageCropperActivity::class.java).apply {
            data = sourceUri
            putExtra("recrop", isRecropping)
            oldCroppedUri?.let { putExtra("old_cropped_uri", it.toString()) }
            location?.let {
                putExtra("location_lat", it.latitude)
                putExtra("location_lon", it.longitude)
                it.altitude?.let { alt ->
                    putExtra("location_alt", alt)
                }
            }
        }
        imageCropperLauncher.launch(intent)
    }

    private fun initializeFragments() {
        // Fragment instances are now managed by the FragmentManager using tags
        // Restore the appropriate fragment based on current ViewModel state
        val existing = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (existing == null) {
            restoreFragmentBasedOnState()
        }
    }
    
    private fun restoreFragmentBasedOnState() {
        val currentState = viewModel.uiState.value
        val hasImages = viewModel.selectedImageUris.value.isNotEmpty()
        
        // Use NavigationManager to restore fragments
        val hasResults = (currentState as? UiState.Success)?.results?.isNotEmpty() == true
        navigationManager.restoreFragmentForState(
            hasImages = hasImages,
            isSuccessState = hasResults,
            isLoadingState = currentState is UiState.Loading,
            isErrorState = currentState is UiState.Error,
            fragmentContainer = binding.fragmentContainer
        )
        
        // Handle specific UI state
        binding.apply {
            when (currentState) {
                is UiState.Success -> {
                    progressBarResults.visibility = View.GONE
                    timeoutContainer.visibility = View.GONE
                    
                    if (currentState.results.isNotEmpty() && !isFinishing && !isDestroyed) {
                        fragmentContainer.post {
                            val state = viewModel.uiState.value
                            if (state is UiState.Success && state.results.isNotEmpty() && !isFinishing && !isDestroyed) {
                                fragmentContainer.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        fragmentContainer.visibility = View.VISIBLE
                    }
                }
                is UiState.Loading -> {
                    progressBarResults.visibility = View.VISIBLE
                    fragmentContainer.visibility = View.GONE
                    timeoutContainer.visibility = View.GONE
                }
                is UiState.Error, is UiState.Idle -> {
                    progressBarResults.visibility = View.GONE
                    fragmentContainer.visibility = View.VISIBLE
                    timeoutContainer.visibility = View.GONE
                }
            }
        }
        
        // Simple rule for camera buttons
        updateCameraButtonVisibility()
    }

    /**
     * Check if disclaimer should be shown and display it if needed
     */
    private fun checkAndShowDisclaimer() {
        if (!settingsManager.shouldShowDisclaimer()) return

        SafeDialogPresenter.showSafely(
            activity = this,
            dialogProvider = {
                DisclaimerDialogFragment.newInstance().apply {
                    setOnDisclaimerAccepted { /* no-op hook */ }
                }
            },
            tag = "disclaimer_dialog"
        )
    }

    private fun handleSharedImage(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            if (isProcessingSharedImage) {
                return
            }
            
            val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            
            sharedUri?.let { uri ->
                isProcessingSharedImage = true

                val currentState = viewModel.uiState.value
                val hasExistingImages = viewModel.selectedImageUris.value.isNotEmpty()

                if (!(hasExistingImages && currentState is UiState.Idle)) {
                    // Any other state, reset UI and clear images
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        drawerManager.closeDrawer()
                    }

                    supportFragmentManager.fragments.filterIsInstance<DialogFragment>().forEach {
                        it.dismiss()
                    }

                    navigationStackManager.clear()

                    if (navigationManager.isSpeciesDetailFragmentVisible()) {
                        navigationManager.navigateBackFromSpeciesDetail()
                    }

                    navigationManager.showMainScreen()
                    updateCameraButtonVisibility()

                    viewModel.clearImages()
                }

                permissionManager.checkStoragePermissionAndExecute {
                    permissionManager.checkMediaLocationPermissionAndExecute {
                        // Check if we can actually access the file (might fail with limited access)
                        if (canAccessUri(uri)) {
                            val location = imageOperationsManager.extractLocationFromImage(uri)
                            startImageCropper(uri, location)
                        } else {
                            // File not accessible - likely due to limited photo access
                            isProcessingSharedImage = false
                            showLimitedAccessDialog()
                        }
                    }
                }

                // Clear only share-related fields so config changes won't re-trigger handling
                intent.let { i ->
                    i.action = null
                    i.removeExtra(Intent.EXTRA_STREAM)
                    i.type = null
                    i.clipData = null
                }
            }
        }
    }

    /**
     * Saves the original camera image to gallery with permission handling.
     * @param imageUri The URI of the original camera image
     * @param onComplete Callback to execute after saving (or permission handling)
     */
    private fun saveOriginalImageToGallery(imageUri: Uri, onComplete: () -> Unit) {
        if (::permissionManager.isInitialized && ::galleryImageSaver.isInitialized) {
            permissionManager.checkWriteStoragePermissionAndExecute {
                galleryImageSaver.saveImageToGallery(imageUri)
                // Continue with the flow regardless of save success
                onComplete()
            }
        } else {
            // If managers aren't initialized, just continue with the flow
            onComplete()
        }
    }

    /**
     * Checks if a URI is accessible by trying to open an input stream.
     * This helps detect when a shared image can't be accessed due to limited permissions.
     */
    private fun canAccessUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Shows a dialog when a shared image can't be accessed due to limited photo permissions.
     * Offers options to select more photos or open settings for full access.
     */
    private fun showLimitedAccessDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: Offer to select more photos or open settings
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.permission_limited_access_title))
                .setMessage(getString(R.string.permission_image_not_accessible))
                .setPositiveButton(getString(R.string.permission_select_more_photos)) { _, _ ->
                    permissionManager.requestMorePhotoAccess()
                }
                .setNeutralButton(getString(R.string.permission_open_settings)) { _, _ ->
                    permissionManager.openAppSettings()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } else {
            // Older Android: Just show error and offer to open settings
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.permission_limited_access_title))
                .setMessage(getString(R.string.permission_image_not_accessible))
                .setPositiveButton(getString(R.string.permission_open_settings)) { _, _ ->
                    permissionManager.openAppSettings()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    // --- Settings Overlay State Restoration ---
    
    /**
     * Checks if navigation should be restored after configuration changes
     */
    private fun checkAndReopenSettings() {
        val prefs = getSharedPreferences(Constants.Preferences.APP_SETTINGS, MODE_PRIVATE)
        val shouldRestore = prefs.getBoolean("restore_navigation_after_change", false)
        val shouldReopenOld = prefs.getBoolean("reopen_settings_after_change", false) // Keep for compatibility

        if (shouldRestore || shouldReopenOld) {
            // Clear the flags
            prefs.edit { 
                remove("restore_navigation_after_change")
                remove("reopen_settings_after_change")
                remove("navigation_state")
                remove("had_results")
            }
            
            // Restore navigation after a short delay to ensure everything is initialized
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    // Always show settings after language change since that's where the user was
                    navigationManager.showSettingsFragment()
                    
                    // Main screen camera buttons are managed by NavigationManager.showMainScreen()
                }
            }, 200) // Slightly longer delay to ensure smooth transition
        }
    }
    
    // --- Header Back Button Utility Methods ---
    
    /**
     * Shows the back button in the header, hiding the logo
     * Used by ResultsFragment for non-historical results
     */
    fun showHeaderBackButton() {
        binding.imageButtonHeaderBack.visibility = View.VISIBLE
        binding.appHeader.visibility = View.VISIBLE
        binding.appName.visibility = View.GONE
        binding.imageViewLogo.visibility = View.GONE
        binding.imageViewSeparator.visibility = View.GONE
    }
    
    /**
     * Hides the back button in the header, showing the logo
     * Used by ResultsFragment when leaving
     */
    fun hideHeaderBackButton() {
        binding.imageButtonHeaderBack.visibility = View.GONE
        binding.appHeader.visibility = View.GONE
        binding.appName.visibility = View.VISIBLE
        binding.imageViewLogo.visibility = View.VISIBLE
        binding.imageViewSeparator.visibility = View.VISIBLE
    }
    
    /**
     * Updates the header title text
     * @param titleRes String resource ID for the title
     */
    fun setHeaderTitle(titleRes: Int) {
        binding.appHeader.text = getString(titleRes)
    }
    
    /**
     * Handles click on the header back button - routes to appropriate back action
     */
    private fun handleHeaderBackClick() {
        // Use the stack-based navigation to pop the topmost overlay
        if (navigationManager.popTopmostOverlay()) {
            updateCameraButtonVisibility()
            return
        }
        
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        
        when (currentFragment) {
            is ResultsFragment -> {
                val isHistorical = try {
                    currentFragment.isHistoricalResults()
                } catch (_: Exception) {
                    false
                }
                
                if (isHistorical) {
                    viewModel.handleFragmentEvent(FragmentEvent.BackToHistoryList)
                } else {
                    viewModel.handleFragmentEvent(FragmentEvent.ResetApp)
                }
            }
            // Main screen doesn't show header back button
        }
    }
    
}