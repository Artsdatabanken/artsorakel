package no.artsdatabanken.artsorakel.viewmodel

import android.content.Context
import android.net.Uri
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.repository.HistoryRepository
import no.artsdatabanken.artsorakel.service.ImageCacheService
import no.artsdatabanken.artsorakel.service.RssFeedService
import no.artsdatabanken.artsorakel.service.ThumbnailService
import no.artsdatabanken.artsorakel.usecase.ClassifySpeciesUseCase
import no.artsdatabanken.artsorakel.usecase.ManageImagesUseCase
import no.artsdatabanken.artsorakel.utils.SettingsManager
import no.artsdatabanken.artsorakel.manager.LanguageManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelClassificationTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var classifySpeciesUseCase: ClassifySpeciesUseCase
    private lateinit var manageImagesUseCase: ManageImagesUseCase
    private lateinit var imageCacheService: ImageCacheService
    private lateinit var historyRepository: HistoryRepository
    private lateinit var thumbnailService: ThumbnailService
    private lateinit var settingsManager: SettingsManager
    private lateinit var rssFeedService: RssFeedService
    private lateinit var languageManager: LanguageManager
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        classifySpeciesUseCase = mockk()
        manageImagesUseCase = mockk()
        imageCacheService = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        thumbnailService = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        rssFeedService = mockk(relaxed = true)
        languageManager = mockk(relaxed = true)

        // Default language manager settings
        every { languageManager.getCurrentLanguageTag() } returns "en"

        every { historyRepository.getAllHistory() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { historyRepository.getRecentHistory(any()) } returns emptyList()

        viewModel = MainViewModel(
            classifySpeciesUseCase,
            manageImagesUseCase,
            imageCacheService,
            historyRepository,
            thumbnailService,
            settingsManager,
            rssFeedService,
            languageManager
        )

        // Default readiness for classification
        every { manageImagesUseCase.isReadyForClassification(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `classify success updates uiState with predictions`() = runTest(testDispatcher) {
        // Seed with one image
        val cropped = mockk<Uri>(); val original = mockk<Uri>()
        every { cropped.toString() } returns "file://c.jpg"
        every { original.toString() } returns "file://o.jpg"
        coEvery { manageImagesUseCase.addImagePair(any(), cropped, original) } returns (listOf(ImagePair(cropped, original)) to ManageImagesUseCase.ImageManagementResult.Success)
        viewModel.addImagePair(cropped, original)
        testDispatcher.scheduler.advanceUntilIdle()

        // Stubs
        val ctx = mockk<Context>(relaxed = true)
        val predictions = listOf(PredictionResult(id = "1", vernacularNames = mapOf("en" to "A", "nb" to "A"), scientificName = "B", probability = 0.9, pictureUrl = null, groupNames = mapOf("en" to "G", "nb" to "G"), infoUrl = null, modelInfo = null, redListCategory = null, invasiveCategory = null))
        coEvery { classifySpeciesUseCase.classifySpecies(any(), any(), any()) } returns ClassifySpeciesUseCase.ClassificationResult.Success(predictions)
        coEvery { thumbnailService.createAndSaveThumbnails(any(), any()) } returns emptyList()
        every { settingsManager.isSaveHistoryEnabled() } returns false

        // Activate flow
        val job = launch { viewModel.uiState.collect { } }

        viewModel.classifyImagesFromUris(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.Success(predictions), viewModel.uiState.value)
        job.cancel()
    }

    @Test
    fun `classify failure sets error state`() = runTest(testDispatcher) {
        // Seed with one image
        val cropped = mockk<Uri>(); val original = mockk<Uri>()
        every { cropped.toString() } returns "file://c.jpg"
        every { original.toString() } returns "file://o.jpg"
        coEvery { manageImagesUseCase.addImagePair(any(), cropped, original) } returns (listOf(ImagePair(cropped, original)) to ManageImagesUseCase.ImageManagementResult.Success)
        viewModel.addImagePair(cropped, original)
        testDispatcher.scheduler.advanceUntilIdle()

        val ctx = mockk<Context>(relaxed = true)
        coEvery { classifySpeciesUseCase.classifySpecies(any(), any(), any()) } returns ClassifySpeciesUseCase.ClassificationResult.Failure(
            no.artsdatabanken.artsorakel.core.errors.ErrorMapper.createNoImagesSelectedError()
        )
        coEvery { thumbnailService.createAndSaveThumbnails(any(), any()) } returns emptyList()
        every { settingsManager.isSaveHistoryEnabled() } returns false

        val job = launch { viewModel.uiState.collect { } }
        viewModel.classifyImagesFromUris(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        assert(viewModel.uiState.value is UiState.Error)
        job.cancel()
    }

    @Test
    fun `classify partial failure still shows success results`() = runTest(testDispatcher) {
        // Seed with one image
        val cropped = mockk<Uri>(); val original = mockk<Uri>()
        every { cropped.toString() } returns "file://c.jpg"
        every { original.toString() } returns "file://o.jpg"
        coEvery { manageImagesUseCase.addImagePair(any(), cropped, original) } returns (listOf(ImagePair(cropped, original)) to ManageImagesUseCase.ImageManagementResult.Success)
        viewModel.addImagePair(cropped, original)
        testDispatcher.scheduler.advanceUntilIdle()

        val ctx = mockk<Context>(relaxed = true)
        val predictions = listOf(PredictionResult(id = "1", vernacularNames = mapOf("en" to "A", "nb" to "A"), scientificName = "B", probability = 0.9, pictureUrl = null, groupNames = mapOf("en" to "G", "nb" to "G"), infoUrl = null, modelInfo = null, redListCategory = null, invasiveCategory = null))
        coEvery { classifySpeciesUseCase.classifySpecies(any(), any(), any()) } returns ClassifySpeciesUseCase.ClassificationResult.PartialFailure(predictions, failedImageCount = 1)
        coEvery { thumbnailService.createAndSaveThumbnails(any(), any()) } returns emptyList()
        every { settingsManager.isSaveHistoryEnabled() } returns false

        val job = launch { viewModel.uiState.collect { } }
        viewModel.classifyImagesFromUris(ctx)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.Success(predictions), viewModel.uiState.value)
        job.cancel()
    }
}

