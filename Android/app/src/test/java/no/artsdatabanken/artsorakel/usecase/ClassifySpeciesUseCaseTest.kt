package no.artsdatabanken.artsorakel.usecase

import android.content.Context
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.artsdatabanken.artsorakel.core.errors.AppError
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.repository.SpeciesRepository
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import no.artsdatabanken.artsorakel.utils.SettingsManager
import io.mockk.every
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassifySpeciesUseCaseTest {

    private lateinit var mockRepository: SpeciesRepository
    private lateinit var mockImageProcessingService: ImageProcessingService
    private lateinit var mockSettingsManager: SettingsManager
    private lateinit var mockContext: Context
    private lateinit var classifySpeciesUseCase: ClassifySpeciesUseCase
    
    private val mockUri1 = mockk<Uri>()
    private val mockUri2 = mockk<Uri>()
    private val testImageData1 = "test_image_1".toByteArray()
    private val testImageData2 = "test_image_2".toByteArray()
    private val testFilenames = listOf("image_0.jpg", "image_1.jpg")
    
    private val mockPredictionResult = PredictionResult(
        id = "test_id_1",
        vernacularNames = mapOf("en" to "Test Species", "nb" to "Test Art"),
        scientificName = "Testus speciesus",
        groupNames = mapOf("en" to "Test Group", "nb" to "Test Guppe"),
        probability = 0.85,
        pictureUrl = "https://test.com/image.jpg",
        infoUrl = "https://test.com/info",
        modelInfo = null,
        redListCategory = null,
        invasiveCategory = null
    )

    @Before
    fun setup() {
        mockRepository = mockk(relaxed = true)
        mockImageProcessingService = mockk(relaxed = true)
        mockSettingsManager = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        // Mock settings manager to return default value
        every { mockSettingsManager.isUseLocationForIdEnabled() } returns true

        classifySpeciesUseCase = ClassifySpeciesUseCase(
            repository = mockRepository,
            imageProcessingService = mockImageProcessingService,
            settingsManager = mockSettingsManager
        )
    }

    @Test
    fun `classifySpecies - success with images processed`() = runTest {
        // Arrange
        val imageUris = listOf(mockUri1)
        val predictions = listOf(mockPredictionResult)
        
        coEvery { 
            mockImageProcessingService.processMultipleImages(
                context = mockContext,
                uris = imageUris,
                targetWidth = 500,
                targetHeight = 500,
                quality = 85
            )
        } returns Triple(listOf(testImageData1), testFilenames, emptyList())
        
        coEvery {
            mockRepository.identifySpecies(listOf(testImageData1), testFilenames, null)
        } returns Result.success(predictions)

        // Act
        val result = classifySpeciesUseCase.classifySpecies(mockContext, imageUris)

        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.Success)
        assertEquals(predictions, result.predictions)
        
        coVerify { mockImageProcessingService.processMultipleImages(any(), any(), any(), any(), any()) }
        coVerify { mockRepository.identifySpecies(listOf(testImageData1), testFilenames, null) }
    }

    @Test
    fun `classifySpecies - failure when no images selected`() = runTest {
        // Act
        val result = classifySpeciesUseCase.classifySpecies(mockContext, emptyList())

        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.Failure)
        assertTrue(result.error is AppError.ImageError.NoImagesSelected)
    }

    @Test
    fun `classifySpecies - failure when all images fail processing`() = runTest {
        // Arrange
        val imageUris = listOf(mockUri1)
        val failedUris = listOf(mockUri1)
        
        coEvery { 
            mockImageProcessingService.processMultipleImages(any(), any(), any(), any(), any())
        } returns Triple(emptyList(), emptyList(), failedUris)

        // Act
        val result = classifySpeciesUseCase.classifySpecies(mockContext, imageUris)

        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.Failure)
        assertTrue(result.error is AppError.ImageError.AllImagesFailedProcessing)
    }

    @Test
    fun `cancelClassification - delegates to repository`() = runTest {
        // Act
        classifySpeciesUseCase.cancelClassification()

        // Assert
        coVerify { mockRepository.cancelIdentification() }
    }

    @Test
    fun `isClassificationInProgress - delegates to repository`() {
        // Arrange
        coEvery { mockRepository.isIdentificationInProgress() } returns true

        // Act
        val result = classifySpeciesUseCase.isClassificationInProgress()

        // Assert
        assertTrue(result)
        coVerify { mockRepository.isIdentificationInProgress() }
    }
} 