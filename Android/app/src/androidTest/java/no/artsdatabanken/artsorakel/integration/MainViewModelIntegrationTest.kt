package no.artsdatabanken.artsorakel.integration

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.service.ImageCacheService
import no.artsdatabanken.artsorakel.service.RssFeedService
import no.artsdatabanken.artsorakel.service.ThumbnailService
import no.artsdatabanken.artsorakel.repository.HistoryRepository
import no.artsdatabanken.artsorakel.usecase.ClassifySpeciesUseCase
import no.artsdatabanken.artsorakel.utils.SettingsManager
import no.artsdatabanken.artsorakel.usecase.ManageImagesUseCase
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import no.artsdatabanken.artsorakel.viewmodel.UiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for MainViewModel UI state management.
 * Tests the complete flow of UI state changes during species identification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MainViewModelIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private lateinit var context: Context
    private lateinit var mockClassifySpeciesUseCase: ClassifySpeciesUseCase
    private lateinit var mockManageImagesUseCase: ManageImagesUseCase
    private lateinit var mockImageCacheService: ImageCacheService
    private lateinit var mockHistoryRepository: HistoryRepository
    private lateinit var mockThumbnailService: ThumbnailService
    private lateinit var mockSettingsManager: SettingsManager
    private lateinit var mockRssFeedService: RssFeedService
    private lateinit var mockLanguageManager: LanguageManager
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockClassifySpeciesUseCase = mockk()
        mockManageImagesUseCase = mockk()
        mockImageCacheService = mockk()
        mockHistoryRepository = mockk(relaxed = true)
        mockThumbnailService = mockk(relaxed = true)
        mockSettingsManager = mockk(relaxed = true)
        mockRssFeedService = mockk(relaxed = true)
        mockLanguageManager = mockk(relaxed = true)

        // Default the save history setting to enabled for tests
        every { mockSettingsManager.isSaveHistoryEnabled() } returns true

        // Default language manager settings
        every { mockLanguageManager.getCurrentLanguageTag() } returns "en"

        viewModel = MainViewModel(
            classifySpeciesUseCase = mockClassifySpeciesUseCase,
            manageImagesUseCase = mockManageImagesUseCase,
            imageCacheService = mockImageCacheService,
            historyRepository = mockHistoryRepository,
            thumbnailService = mockThumbnailService,
            settingsManager = mockSettingsManager,
            rssFeedService = mockRssFeedService,
            languageManager = mockLanguageManager
        )
    }

    @Test
    fun complete_UI_flow_successful_identification() = testScope.runTest {
        // Arrange
        val testContext = ApplicationProvider.getApplicationContext<Context>()
        val testUri = mockk<Uri>()
        val testImagePair = ImagePair(
            croppedUri = testUri,
            originalUri = testUri
        )
        val expectedPredictions = createMockPredictions()
        
        every { testUri.toString() } returns "content://test/image.jpg"
        
        // Mock successful image management
        coEvery {
            mockManageImagesUseCase.addImagePair(any(), any(), any())
        } returns Pair(
            listOf(testImagePair),
            ManageImagesUseCase.ImageManagementResult.Success
        )
        
        coEvery {
            mockManageImagesUseCase.isReadyForClassification(any())
        } returns true
        
        // Mock successful classification
        coEvery {
            mockClassifySpeciesUseCase.classifySpecies(any(), any())
        } returns ClassifySpeciesUseCase.ClassificationResult.Success(expectedPredictions, null)
        
        // Mock image cache operations
        coEvery {
            mockImageCacheService.preloadImages(any(), any())
        } returns Unit
        
        coEvery {
            mockImageCacheService.clearExpiredCache()
        } returns Unit
        
        // Act & Assert - Step 1: Add image
        viewModel.addImagePair(testUri, testUri)
        advanceUntilIdle()
        
        assertEquals(1, viewModel.selectedImagePairs.value.size)
        assertTrue(viewModel.uiState.value is UiState.Idle)
        
        // Act & Assert - Step 2: Start classification
        viewModel.classifyImagesFromUris(testContext)
        
        // Should be in loading state immediately
        assertTrue(viewModel.uiState.value is UiState.Loading)
        
        // Wait for classification to complete
        advanceUntilIdle()
        
        // Should be in success state with predictions
        val finalState = viewModel.uiState.value
        assertTrue(finalState is UiState.Success)
        val successState = finalState
        assertEquals(2, successState.results.size)
        assertEquals("corvus-corax", successState.results[0].id)
    }

    private fun createMockPredictions(): List<PredictionResult> {
        return listOf(
            PredictionResult(
                id = "corvus-corax",
                vernacularNames = mapOf("en" to "Common Raven", "nb" to "Ravn"),
                scientificName = "Corvus corax",
                groupNames = mapOf("en" to "Birds", "nb" to "Fugler"),
                probability = 0.95,
                pictureUrl = "https://artsdatabanken.no/media/raven.jpg",
                infoUrl = "https://artsdatabanken.no/Pages/corvus-corax",
                modelInfo = null,
                redListCategory = null,
                invasiveCategory = null
            ),
            PredictionResult(
                id = "corvus-corone",
                vernacularNames = mapOf("en" to "Carrion Crow", "nb" to "Svartkråke"),
                scientificName = "Corvus corone",
                groupNames = mapOf("en" to "Birds", "nb" to "Fugler"),
                probability = 0.85,
                pictureUrl = "https://artsdatabanken.no/media/crow.jpg",
                infoUrl = "https://artsdatabanken.no/Pages/corvus-corone",
                modelInfo = null,
                redListCategory = null,
                invasiveCategory = null
            )
        )
    }
} 