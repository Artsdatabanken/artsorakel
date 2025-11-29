package no.artsdatabanken.artsorakel.viewmodel // Replace with your package name

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.model.GeoLocation
import no.artsdatabanken.artsorakel.model.IdentificationHistory
import no.artsdatabanken.artsorakel.model.RssFeedItem
import no.artsdatabanken.artsorakel.model.Warnings
import no.artsdatabanken.artsorakel.service.ImageCacheService
import no.artsdatabanken.artsorakel.service.ThumbnailService
import no.artsdatabanken.artsorakel.service.RssFeedService
import no.artsdatabanken.artsorakel.repository.HistoryRepository
import no.artsdatabanken.artsorakel.usecase.ClassifySpeciesUseCase
import no.artsdatabanken.artsorakel.usecase.ManageImagesUseCase
import no.artsdatabanken.artsorakel.utils.SettingsManager
import no.artsdatabanken.artsorakel.manager.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import no.artsdatabanken.artsorakel.core.errors.AppError
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.getUserMessage
import no.artsdatabanken.artsorakel.core.errors.handleResult
import no.artsdatabanken.artsorakel.core.errors.logError
import no.artsdatabanken.artsorakel.core.errors.safeLaunch
import no.artsdatabanken.artsorakel.core.FragmentEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.core.net.toUri

// Sealed class to represent UI State
sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    // Success now holds a List of PredictionResult objects
    data class Success(
        val results: List<PredictionResult>,
        val warnings: Warnings? = null,
        val isHistorical: Boolean = false,
        val historicalDate: java.util.Date? = null,
        val locationUsed: Boolean = false
    ) : UiState()
    data class Error(val error: AppError) : UiState() {
        // Convenience property for UI
        val message: String get() = error.getUserMessage()
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val classifySpeciesUseCase: ClassifySpeciesUseCase,
    private val manageImagesUseCase: ManageImagesUseCase,
    private val imageCacheService: ImageCacheService,
    private val historyRepository: HistoryRepository,
    private val thumbnailService: ThumbnailService,
    private val settingsManager: SettingsManager,
    private val rssFeedService: RssFeedService,
    private val languageManager: LanguageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Returns whether location was used for the current identification results.
     */
    fun wasLocationUsed(): Boolean {
        return (_uiState.value as? UiState.Success)?.locationUsed ?: false
    }
    
    // Event system for fragment communication
    private val _fragmentEvents = Channel<FragmentEvent>(Channel.BUFFERED)
    val fragmentEvents = _fragmentEvents.receiveAsFlow()
    
    // Job to track the classification process for cancellation
    private var classificationJob: Job? = null
    private var isAborted = false

    // --- StateFlow for user's actual selected images ---
    private val _selectedImagePairs = MutableStateFlow<List<ImagePair>>(emptyList())
    val selectedImagePairs: StateFlow<List<ImagePair>> = _selectedImagePairs.asStateFlow()

    // Display image pairs (user images or historical images)
    private val _displayImagePairs = MutableStateFlow<List<ImagePair>>(emptyList())
    val displayImagePairs: StateFlow<List<ImagePair>> = _displayImagePairs.asStateFlow()

    val selectedImageUris: StateFlow<List<Uri>> = _displayImagePairs.map { pairs ->
        pairs.map { it.croppedUri }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Recent history for the main screen stack (max 3 items)
    private val _recentHistory = MutableStateFlow<List<IdentificationHistory>>(emptyList())
    val recentHistory: StateFlow<List<IdentificationHistory>> = _recentHistory.asStateFlow()

    // All history for the expanded view
    private val _allHistory = MutableStateFlow<List<IdentificationHistory>>(emptyList())
    val allHistory: StateFlow<List<IdentificationHistory>> = _allHistory.asStateFlow()

    // RSS feed item
    private val _rssFeedItem = MutableStateFlow<RssFeedItem?>(null)
    val rssFeedItem: StateFlow<RssFeedItem?> = _rssFeedItem.asStateFlow()

    // Track if we're currently viewing historical results
    private var isViewingHistoricalResults = false

    // Track current language to detect changes
    private var currentLanguageTag: String = languageManager.getCurrentLanguageTag()

    init {
        // Load recent history on initialization
        loadRecentHistory()

        // Reset RSS session on app start and load RSS feed
        rssFeedService.resetSessionOnAppStart()
        loadRssFeed()

        // Keep display in sync with user's actual images initially
        _displayImagePairs.value = _selectedImagePairs.value
    }

    fun checkAndHandleLanguageChange() {
        val newLanguageTag = languageManager.getCurrentLanguageTag()
        if (newLanguageTag != currentLanguageTag) {
            currentLanguageTag = newLanguageTag
            onLanguageChanged()
        }
    }

    private fun loadRecentHistory() {
        viewModelScope.launch {
            try {
                val recent = historyRepository.getRecentHistory(3)
                _recentHistory.value = recent
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-loadRecentHistory")
                _recentHistory.value = emptyList()
            }
        }
    }

    fun loadRssFeed() {
        viewModelScope.launch {
            try {
                val feedItem = rssFeedService.fetchFilteredRssItem()
                _rssFeedItem.value = feedItem
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-loadRssFeed")
                _rssFeedItem.value = null
            }
        }
    }

    fun dismissRssFeed() {
        val feedItem = _rssFeedItem.value
        if (feedItem != null) {
            rssFeedService.dismissItem(feedItem)
            // Try to get the next available item
            viewModelScope.launch {
                try {
                    val nextItem = rssFeedService.refilterCachedItems()
                    _rssFeedItem.value = nextItem
                } catch (e: Exception) {
                    val error = ErrorMapper.mapException(e)
                    error.logError("MainViewModel-dismissRssFeed")
                    _rssFeedItem.value = null
                }
            }
        }
    }

    fun onLanguageChanged() {
        viewModelScope.launch {
            try {
                val feedItem = rssFeedService.refilterCachedItems()
                _rssFeedItem.value = feedItem
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-onLanguageChanged")
                _rssFeedItem.value = null
            }
        }
    }



    fun addImagePair(croppedUri: Uri, originalUri: Uri, location: GeoLocation? = null, isRecrop: Boolean = false, oldCroppedUri: Uri? = null) {
        // Clear results when adding new images
        _uiState.value = UiState.Idle
        
        // Clear any historical viewing state
        isViewingHistoricalResults = false
        
        viewModelScope.safeLaunch("MainViewModel") {
            val (updatedList, managementResult) = manageImagesUseCase.addImagePair(
                currentPairs = _selectedImagePairs.value,
                croppedUri = croppedUri,
                originalUri = originalUri,
                location = location,
                isRecrop = isRecrop,
                oldCroppedUri = oldCroppedUri
            )
            
            // Update both the actual user images and display with defensive copies
            _selectedImagePairs.value = updatedList.toList()
            _displayImagePairs.value = updatedList.toList()
            
            // Log any cleanup issues (but don't fail the operation)
            managementResult.handleResult("MainViewModel")
        }
    }

    fun removeImagePair(pair: ImagePair) {
        viewModelScope.safeLaunch("MainViewModel") {
            val (updatedList, managementResult) = manageImagesUseCase.removeImagePair(
                currentPairs = _selectedImagePairs.value,
                pairToRemove = pair
            )

            // Update both the actual user images and display with defensive copies
            _selectedImagePairs.value = updatedList.toList()
            _displayImagePairs.value = updatedList.toList()

            // Clear any existing results as they are no longer valid
            _uiState.value = UiState.Idle

            // Log any cleanup issues (but don't fail the operation)
            managementResult.handleResult("MainViewModel")
        }
    }

    fun clearImages() {
        viewModelScope.safeLaunch("MainViewModel") {
            val managementResult = manageImagesUseCase.clearAllImages(
                currentPairs = _selectedImagePairs.value
            )
            
            // Clear both the actual user images and display (defensive empty lists)
            _selectedImagePairs.value = emptyList()
            _displayImagePairs.value = emptyList()
            _uiState.value = UiState.Idle
            
            // Log any cleanup issues (but don't fail the operation)
            managementResult.handleResult("MainViewModel")
        }
    }
    
    /**
     * Clears historical state and returns to idle
     */
    fun clearHistoricalState() {
        _uiState.value = UiState.Idle
        // Clear display images but keep user images intact
        _displayImagePairs.value = emptyList()
    }
    
    /**
     * Resets to normal mode - behavior depends on current context
     */
    private fun resetToUserImages() {
        if (isViewingHistoricalResults) {
            _displayImagePairs.value = _selectedImagePairs.value.toList()
            isViewingHistoricalResults = false
        } else {
            // Normal reset after user's own identification - clear all user images
            clearImages()
        }
        _uiState.value = UiState.Idle
    }





    fun abortClassification() {
        isAborted = true
        classificationJob?.cancel()
        classificationJob = null
        
        // Cancel classification operation
        viewModelScope.launch {
            classifySpeciesUseCase.cancelClassification()
        }
        
        _uiState.value = UiState.Idle
    }

    fun classifyImagesFromUris(context: Context?) {
        // Defensive null check for context
        if (context == null) {
            val error = ErrorMapper.mapException(IllegalArgumentException("Context cannot be null"))
            error.logError("MainViewModel")
            _uiState.value = UiState.Error(error)
            return
        }

        val urisToProcess = selectedImageUris.value // Get current URIs from StateFlow
        val currentPairs = _selectedImagePairs.value

        // Defensive null checks and validation
        if (currentPairs.isEmpty()) {
            val error = ErrorMapper.createNoImagesSelectedError()
            error.logError("MainViewModel")
            _uiState.value = UiState.Error(error)
            return
        }
        
        // Validate using the use case
        if (!manageImagesUseCase.isReadyForClassification(currentPairs)) {
            val error = ErrorMapper.createNoImagesSelectedError()
            error.logError("MainViewModel")
            _uiState.value = UiState.Error(error)
            return
        }

        // --- Reset abort flag ---
        isAborted = false

        // --- Set Loading State ---
        _uiState.value = UiState.Loading

        // --- Launch Coroutine for Background Work ---
        classificationJob = viewModelScope.launch {
            try {
                // Check if aborted before starting
                if (isAborted) {
                    return@launch
                }

                // --- Use ClassifySpeciesUseCase for the entire workflow ---
                val classificationResult = classifySpeciesUseCase.classifySpecies(
                    context = context,
                    imageUris = urisToProcess,
                    imagePairs = currentPairs
                )

                // Check if aborted before setting results
                if (isAborted) {
                    return@launch
                }
                
                // --- Handle Classification Result ---
                when (classificationResult) {
                    is ClassifySpeciesUseCase.ClassificationResult.Success -> {
                        // Mark that we're viewing fresh results (not historical)
                        isViewingHistoricalResults = false
                        _uiState.value = UiState.Success(
                            results = classificationResult.predictions,
                            warnings = classificationResult.warnings,
                            locationUsed = classificationResult.locationUsed
                        )
                        // Save to history
                        saveToHistory(context, classificationResult.predictions, classificationResult.warnings, urisToProcess)
                        // Preload images for better user experience
                        preloadSpeciesImages(classificationResult.predictions)
                        // Signal that results are ready to display
                        sendEvent(FragmentEvent.ResultsReady)
                    }
                    is ClassifySpeciesUseCase.ClassificationResult.PartialFailure -> {
                        // Log the partial failure but still show results
                        val error = ErrorMapper.createAllImagesFailedError(classificationResult.failedImageCount)
                        error.logError("MainViewModel")
                        // Mark that we're viewing fresh results (not historical)
                        isViewingHistoricalResults = false
                        _uiState.value = UiState.Success(
                            results = classificationResult.predictions,
                            warnings = classificationResult.warnings,
                            locationUsed = classificationResult.locationUsed
                        )
                        // Save to history
                        saveToHistory(context, classificationResult.predictions, classificationResult.warnings, urisToProcess)
                        // Preload images for better user experience
                        preloadSpeciesImages(classificationResult.predictions)
                        // Signal that results are ready to display
                        sendEvent(FragmentEvent.ResultsReady)
                    }
                    is ClassifySpeciesUseCase.ClassificationResult.Failure -> {
                        classificationResult.error.logError("MainViewModel")
                        _uiState.value = UiState.Error(classificationResult.error)
                    }
                }

            } catch (e: Exception) {
                // --- Handle Unexpected Errors ---
                // Don't set error state if aborted
                if (!isAborted) {
                    val error = ErrorMapper.mapException(e)
                    error.logError("MainViewModel")
                    _uiState.value = UiState.Error(error)
                }
            } finally {
                // Clear the job reference when done
                if (classificationJob?.isCompleted == true) {
                    classificationJob = null
                }
            }
        }
    }

    // --- Fragment Event Handling ---
    
    /**
     * Sends an event to listening fragments
     */
    fun sendEvent(event: FragmentEvent) {
        viewModelScope.launch {
            _fragmentEvents.send(event)
        }
    }
    
    /**
     * Handles events from fragments - replaces individual listener methods
     */
    fun handleFragmentEvent(event: FragmentEvent) {
        when (event) {
            is FragmentEvent.IdentifySpecies -> {
                if (selectedImageUris.value.isNotEmpty()) {
                    sendEvent(FragmentEvent.IdentifySpecies)
                } else {
                    val error = ErrorMapper.createNoImagesSelectedError()
                    error.logError("MainViewModel")
                    _uiState.value = UiState.Error(error)
                }
            }
            is FragmentEvent.ResetApp -> {
                resetToUserImages()
                _uiState.value = UiState.Idle
                sendEvent(event)
            }
            is FragmentEvent.AddImage -> {
                sendEvent(event)
            }
            is FragmentEvent.ViewImage -> {
                sendEvent(event)
            }
            is FragmentEvent.SelectSpecies -> {
                // Signal that species selection occurred
                sendEvent(event)
            }
            is FragmentEvent.NavigateBack -> {
                // Signal navigation back
                sendEvent(event)
            }
            is FragmentEvent.ResultsReady -> {
                // Signal that results are ready to display
                sendEvent(event)
            }
            is FragmentEvent.ViewHistoryResults -> {
                // Load and display results from history without new identification
                loadHistoryResults(event.historyItem)
            }
            is FragmentEvent.BackToHistoryList -> {
                // Pass through to MainActivity for navigation handling
                sendEvent(event)
            }
        }
    }
    
    /**
     * Loads and displays results from history without performing new identification
     */
    private fun loadHistoryResults(historyItem: IdentificationHistory) {
        viewModelScope.safeLaunch("MainViewModel-loadHistoryResults") {
            try {
                // Parse the stored JSON results
                val predictions = historyRepository.parseResultsFromJson(historyItem.allResults)

                // Parse warnings if available
                val warnings = historyItem.warnings?.let { warningsJson ->
                    try {
                        com.google.gson.Gson().fromJson(warningsJson, Warnings::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (predictions.isNotEmpty()) {
                    // Create ImagePairs for the historical images
                    // Use fullSizeImagePaths if available (new format), fall back to thumbnailPaths (old format)
                    val imagePaths = historyItem.fullSizeImagePaths.ifEmpty { historyItem.thumbnailPaths }
                    val historicalImagePairs = imagePaths.map { path ->
                        val fileUri = Uri.fromFile(java.io.File(path))
                        ImagePair(
                            croppedUri = fileUri,
                            originalUri = fileUri
                        )
                    }

                    // Mark that we're now viewing historical results
                    isViewingHistoricalResults = true

                    // Update ONLY the display state - user's actual images remain untouched
                    _displayImagePairs.value = historicalImagePairs.toList()

                    // Set the UI state to show the historical results with date
                    _uiState.value = UiState.Success(
                        results = predictions,
                        warnings = warnings,
                        isHistorical = true,
                        historicalDate = historyItem.timestamp
                    )
                    
                    // Preload species images for better user experience
                    preloadSpeciesImages(predictions)
                    
                    sendEvent(FragmentEvent.ResultsReady)
                } else {
                    val error = ErrorMapper.createNoResultsFoundError()
                    error.logError("MainViewModel-loadHistoryResults")
                    _uiState.value = UiState.Error(error)
                }
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-loadHistoryResults")
                _uiState.value = UiState.Error(error)
            }
        }
    }
    
    /**
     * Preloads species images in the background for better user experience.
     * This improves performance when users view species details.
     */
    private fun preloadSpeciesImages(predictions: List<PredictionResult>) {
        viewModelScope.safeLaunch("MainViewModel") {
            // Extract image URLs from predictions
            val imageUrls = predictions.mapNotNull { it.pictureUrl }
            
            if (imageUrls.isNotEmpty()) {
                // Preload species images in background
                imageCacheService.preloadImages(imageUrls, ImageCacheService.CacheType.SPECIES_IMAGE)
            }
            
            // Clear expired cache entries
            imageCacheService.clearExpiredCache()
        }
    }
    
    /**
     * Saves identification results to history with thumbnails
     */
    private fun saveToHistory(
        context: Context,
        predictions: List<PredictionResult>,
        warnings: Warnings?,
        imageUris: List<Uri>
    ) {
        viewModelScope.safeLaunch("MainViewModel-saveToHistory") {
            try {
                // Save full-size images (1024x1024) for both display and reporting
                val fullSizeImagePaths = thumbnailService.createAndSaveFullSizeImages(context, imageUris)

                // Convert URIs to string paths for storage (these may expire, kept for reference)
                val imagePaths = imageUris.map { it.toString() }

                // Save the identification result to history only if enabled
                if (settingsManager.isSaveHistoryEnabled()) {
                    historyRepository.saveIdentificationToHistory(
                        predictionResults = predictions,
                        warnings = warnings,
                        imagePaths = imagePaths,
                        thumbnailPaths = fullSizeImagePaths, // Use full-size images as thumbnails too
                        fullSizeImagePaths = fullSizeImagePaths
                    )
                }

                // Refresh recent history after saving
                loadRecentHistory()
            } catch (e: Exception) {
                // Log error but don't fail the main operation
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-saveToHistory")
            }
        }
    }

    /**
     * Deletes a specific history item and its associated images
     */
    fun deleteHistoryItem(historyItem: IdentificationHistory) {
        viewModelScope.safeLaunch("MainViewModel-deleteHistoryItem") {
            try {
                // Delete associated full-size image files
                if (historyItem.fullSizeImagePaths.isNotEmpty()) {
                    thumbnailService.deleteFullSizeImages(historyItem.fullSizeImagePaths)
                } else {
                    // Fall back to deleting old thumbnail files for backward compatibility
                    thumbnailService.deleteThumbnails(historyItem.thumbnailPaths)
                }

                // Delete the history record from database
                historyRepository.deleteHistory(historyItem)

                // Refresh history lists after deletion
                loadRecentHistory()
                loadAllHistory()

            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-deleteHistoryItem")
                // Could emit an error state here if needed
            }
        }
    }

    /**
     * Loads all history items for the expanded view
     */
    fun loadAllHistory() {
        viewModelScope.safeLaunch("MainViewModel-loadAllHistory") {
            try {
                val allHistory = historyRepository.getAllHistory().first()
                _allHistory.value = allHistory
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-loadAllHistory")
                _allHistory.value = emptyList()
            }
        }
    }

    /**
     * Clears all identification history and thumbnails
     */
    fun clearAllHistory() {
        viewModelScope.safeLaunch("MainViewModel-clearAllHistory") {
            try {
                historyRepository.deleteAllHistory()
                // Refresh history data after clearing
                loadRecentHistory()
                loadAllHistory()
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("MainViewModel-clearAllHistory")
            }
        }
    }
    
    // Track last used input method (camera vs gallery)
    private var lastInputMethodIsCamera = true
    
    fun setLastInputMethod(isCamera: Boolean) {
        lastInputMethodIsCamera = isCamera
    }
    
    fun getLastInputMethod(): Boolean = lastInputMethodIsCamera
}