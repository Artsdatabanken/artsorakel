package no.artsdatabanken.artsorakel.viewmodel

import android.net.Uri
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import no.artsdatabanken.artsorakel.core.errors.AppError
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.model.IdentificationHistory
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
class MainViewModelTest {

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

        // Recent history mock (suspend)
        coEvery { historyRepository.getRecentHistory(any()) } returns emptyList()
        // Flows used by UI observers
        every { historyRepository.getAllHistory() } returns kotlinx.coroutines.flow.flowOf(emptyList())

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addImagePair updates state and clears results`() = runTest(testDispatcher) {
        // Activate selectedImageUris flow
        val job = launch { viewModel.selectedImageUris.collect { } }
        val cropped = mockk<Uri>()
        val original = mockk<Uri>()
        every { cropped.toString() } returns "file://cropped.jpg"
        every { original.toString() } returns "file://original.jpg"
        val updated = listOf(ImagePair(cropped, original))
        coEvery { manageImagesUseCase.addImagePair(any(), cropped, original) } returns (updated to ManageImagesUseCase.ImageManagementResult.Success)
        // Proceed to add and verify state updates

        viewModel.addImagePair(cropped, original)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(cropped), viewModel.selectedImageUris.value)
        assertEquals(UiState.Idle, viewModel.uiState.value)
        job.cancel()
    }

    @Test
    fun `clearImages empties lists and resets state`() = runTest(testDispatcher) {
        // Activate selectedImageUris flow
        val job = launch { viewModel.selectedImageUris.collect { } }
        coEvery { manageImagesUseCase.clearAllImages(any()) } returns ManageImagesUseCase.ImageManagementResult.Success
        // Seed state
        val cropped = mockk<Uri>(); val original = mockk<Uri>()
        every { cropped.toString() } returns "file://c.jpg"
        every { original.toString() } returns "file://o.jpg"
        coEvery { manageImagesUseCase.addImagePair(any(), cropped, original) } returns (listOf(ImagePair(cropped, original)) to ManageImagesUseCase.ImageManagementResult.Success)
        viewModel.addImagePair(cropped, original)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearImages()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<Uri>(), viewModel.selectedImageUris.value)
        assertEquals(UiState.Idle, viewModel.uiState.value)
        job.cancel()
    }
}

