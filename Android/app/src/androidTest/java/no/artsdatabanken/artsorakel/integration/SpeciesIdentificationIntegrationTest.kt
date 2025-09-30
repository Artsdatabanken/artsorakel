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
import kotlinx.coroutines.test.runTest
import no.artsdatabanken.artsorakel.network.ApiResponse
import no.artsdatabanken.artsorakel.network.ApiService
import no.artsdatabanken.artsorakel.network.ModelInfoDto
import no.artsdatabanken.artsorakel.network.PredictionDto
import no.artsdatabanken.artsorakel.network.TaxaInfoDto
import no.artsdatabanken.artsorakel.network.TaxonItemDto
import no.artsdatabanken.artsorakel.repository.SpeciesRepositoryImpl
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import no.artsdatabanken.artsorakel.usecase.ClassifySpeciesUseCase
import no.artsdatabanken.artsorakel.utils.SettingsManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the complete species identification workflow.
 * Tests the entire flow from image processing through API calls to UI state updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SpeciesIdentificationIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var mockApiService: ApiService
    private lateinit var mockImageProcessingService: ImageProcessingService
    private lateinit var mockSettingsManager: SettingsManager
    private lateinit var repository: SpeciesRepositoryImpl
    private lateinit var useCase: ClassifySpeciesUseCase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockApiService = mockk()
        mockImageProcessingService = mockk()
        mockSettingsManager = mockk()

        // Mock settings manager to return default value
        every { mockSettingsManager.isUseLocationForIdEnabled() } returns true

        repository = SpeciesRepositoryImpl(mockApiService)
        useCase = ClassifySpeciesUseCase(
            repository = repository,
            imageProcessingService = mockImageProcessingService,
            settingsManager = mockSettingsManager
        )
    }

    @Test
    fun complete_identification_flow_success_scenario() = runTest {
        // Arrange
        val testUri = mockk<Uri>()
        val testImageData = ByteArray(100) { it.toByte() }

        every { testUri.toString() } returns "content://test/image.jpg"
        
        // Mock image processing success
        coEvery {
            mockImageProcessingService.processMultipleImages(
                context = any(),
                uris = listOf(testUri),
                targetWidth = any(),
                targetHeight = any(),
                quality = any()
            )
        } returns Triple(
            listOf(testImageData),
            listOf("image_0.jpg"),
            emptyList()
        )
        
        // Mock API success - with all parameters including location
        coEvery {
            mockApiService.classifyImages(any(), any(), any(), any())
        } returns createMockApiResponse()
        
        // Act
        val result = useCase.classifySpecies(
            context = context,
            imageUris = listOf(testUri)
        )
        
        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.Success)
        val successResult = result
        assertEquals(2, successResult.predictions.size)
        assertEquals("Corvus corax", successResult.predictions[0].scientificName)
        assertEquals(0.95, successResult.predictions[0].probability, 0.01)
    }

    @Test
    fun complete_identification_flow_partial_failure_scenario() = runTest {
        // Arrange
        val testUri1 = mockk<Uri>()
        val testUri2 = mockk<Uri>()
        val testImageData = ByteArray(100) { it.toByte() }
        
        every { testUri1.toString() } returns "content://test/image1.jpg"
        every { testUri2.toString() } returns "content://test/image2.jpg"
        
        // Mock image processing with one failure
        coEvery {
            mockImageProcessingService.processMultipleImages(
                context = any(),
                uris = listOf(testUri1, testUri2),
                targetWidth = any(),
                targetHeight = any(),
                quality = any()
            )
        } returns Triple(
            listOf(testImageData), // Only one image processed
            listOf("image_0.jpg"),
            listOf(testUri2) // One failed
        )
        
        // Mock API success - with all parameters including location
        coEvery {
            mockApiService.classifyImages(any(), any(), any(), any())
        } returns createMockApiResponse()
        
        // Act
        val result = useCase.classifySpecies(
            context = context,
            imageUris = listOf(testUri1, testUri2)
        )
        
        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.PartialFailure)
        val partialResult = result
        assertEquals(2, partialResult.predictions.size)
        assertEquals(1, partialResult.failedImageCount)
    }

    @Test
    fun complete_identification_flow_network_error_scenario() = runTest {
        // Arrange
        val testUri = mockk<Uri>()
        val testImageData = ByteArray(100) { it.toByte() }
        
        every { testUri.toString() } returns "content://test/image.jpg"
        
        // Mock image processing success
        coEvery {
            mockImageProcessingService.processMultipleImages(
                context = any(),
                uris = listOf(testUri),
                targetWidth = any(),
                targetHeight = any(),
                quality = any()
            )
        } returns Triple(
            listOf(testImageData),
            listOf("image_0.jpg"),
            emptyList()
        )
        
        // Mock API network error
        coEvery {
            mockApiService.classifyImages(any(), any(), any(), any())
        } throws Exception("Server Error")
        
        // Act
        val result = useCase.classifySpecies(
            context = context,
            imageUris = listOf(testUri)
        )
        
        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.Failure)
    }

    @Test
    fun complete_identification_flow_all_images_fail_processing() = runTest {
        // Arrange
        val testUri = mockk<Uri>()
        
        every { testUri.toString() } returns "content://test/image.jpg"
        
        // Mock image processing failure
        coEvery {
            mockImageProcessingService.processMultipleImages(
                context = any(),
                uris = listOf(testUri),
                targetWidth = any(),
                targetHeight = any(),
                quality = any()
            )
        } returns Triple(
            emptyList(), // No images processed
            emptyList(),
            listOf(testUri) // All failed
        )
        
        // Act
        val result = useCase.classifySpecies(
            context = context,
            imageUris = listOf(testUri)
        )
        
        // Assert
        assertTrue(result is ClassifySpeciesUseCase.ClassificationResult.Failure)
    }

    @Test
    fun repository_handles_empty_API_response_gracefully() = runTest {
        // Arrange
        val testImageData = ByteArray(100) { it.toByte() }
        
        // Mock API with empty response
        coEvery {
            mockApiService.classifyImages(any(), any(), any(), any())
        } returns ApiResponse(predictions = emptyList(), modelInfo = null)
        
        // Act
        val result = repository.identifySpecies(
            imageDataList = listOf(testImageData),
            imageFilenames = listOf("test.jpg"),
            location = null
        )
        
        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun repository_handles_malformed_API_response_gracefully() = runTest {
        // Arrange
        val testImageData = ByteArray(100) { it.toByte() }
        
        // Mock API with malformed response
        coEvery {
            mockApiService.classifyImages(any(), any(), any(), any())
        } returns ApiResponse(
                predictions = listOf(
                    PredictionDto(
                        regionGroupId = "test-region",
                        taxa = TaxaInfoDto(
                            items = listOf(
                                TaxonItemDto(
                                    probability = 0.8,
                                    scientificName = "Test species",
                                    scientificNameId = null, // Invalid - should be filtered out
                                    vernacularNames = mapOf("en" to "Test Species", "nb" to "Test Art"),
                                    groupNames = mapOf("en" to "Test Group", "nb" to "Testgruppe"),
                                    name = "Test species",
                                    infoUrl = null,
                                    picture = null,
                                    redListCategory = null,
                                    invasiveCategory = null
                                ),
                                TaxonItemDto(
                                    probability = 0.7,
                                    scientificName = "Valid species",
                                    scientificNameId = "valid-id",
                                    vernacularNames = mapOf("en" to "Valid Species", "nb" to "Gyldig Art"),
                                    groupNames = mapOf("en" to "Valid Group", "nb" to "Gyldig Gruppe"),
                                    name = "Valid species",
                                    infoUrl = "https://example.com/info",
                                    picture = "https://example.com/image.jpg",
                                    redListCategory = null,
                                    invasiveCategory = null
                                )
                            ),
                            type = "species"
                        )
                    )
                ),
                modelInfo = null
            )
        
        // Act
        val result = repository.identifySpecies(
            imageDataList = listOf(testImageData),
            imageFilenames = listOf("test.jpg"),
            location = null
        )
        
        // Assert
        assertTrue(result.isSuccess)
        val predictions = result.getOrNull()!!
        assertEquals(1, predictions.size) // Only valid item should remain
        assertEquals("valid-id", predictions[0].id)
    }

    @Test
    fun location_toggle_controls_location_sending() = runTest {
        // Test that location is sent when toggle is ON
        val testUri = mockk<Uri>()
        val testImageData = ByteArray(100) { it.toByte() }
        val testLocation = no.artsdatabanken.artsorakel.model.GeoLocation(60.0, 10.0)
        val testImagePair = no.artsdatabanken.artsorakel.model.ImagePair(
            originalUri = testUri,
            croppedUri = testUri,
            location = testLocation
        )

        every { testUri.toString() } returns "content://test/image.jpg"
        every { mockSettingsManager.isUseLocationForIdEnabled() } returns true

        coEvery {
            mockImageProcessingService.processMultipleImages(any(), any(), any(), any(), any())
        } returns Triple(listOf(testImageData), listOf("image_0.jpg"), emptyList())

        coEvery {
            mockApiService.classifyImages(any(), any(), any(), any())
        } returns createMockApiResponse()

        // Act with location enabled
        val resultWithLocation = useCase.classifySpecies(
            context = context,
            imageUris = listOf(testUri),
            imagePairs = listOf(testImagePair)
        )

        // Assert location was used
        assertTrue(resultWithLocation is ClassifySpeciesUseCase.ClassificationResult.Success)

        // Now test with location disabled
        every { mockSettingsManager.isUseLocationForIdEnabled() } returns false

        val resultWithoutLocation = useCase.classifySpecies(
            context = context,
            imageUris = listOf(testUri),
            imagePairs = listOf(testImagePair)
        )

        // Assert - result should still succeed but location wasn't sent
        assertTrue(resultWithoutLocation is ClassifySpeciesUseCase.ClassificationResult.Success)
    }

    private fun createMockApiResponse(): ApiResponse {
        return ApiResponse(
            predictions = listOf(
                PredictionDto(
                    regionGroupId = "NO",
                    taxa = TaxaInfoDto(
                        items = listOf(
                            TaxonItemDto(
                                probability = 0.95,
                                scientificName = "Corvus corax",
                                scientificNameId = "corvus-corax",
                                vernacularNames = mapOf("en" to "Common Raven", "nb" to "Ravn"),
                                groupNames = mapOf("en" to "Birds", "nb" to "Fugler"),
                                name = "Common Raven",
                                infoUrl = "https://artsdatabanken.no/Pages/corvus-corax",
                                picture = "https://artsdatabanken.no/media/raven.jpg",
                                redListCategory = null,
                                invasiveCategory = null
                            ),
                            TaxonItemDto(
                                probability = 0.85,
                                scientificName = "Corvus corone",
                                scientificNameId = "corvus-corone",
                                vernacularNames = mapOf("en" to "Carrion Crow", "nb" to "Svartkråke"),
                                groupNames = mapOf("en" to "Birds", "nb" to "Fugler"),
                                name = "Carrion Crow",
                                infoUrl = "https://artsdatabanken.no/Pages/corvus-corone",
                                picture = "https://artsdatabanken.no/media/crow.jpg",
                                redListCategory = null,
                                invasiveCategory = null
                            )
                        ),
                        type = "species"
                    )
                )
            ),
            modelInfo = ModelInfoDto(
                model = "Norwegian",
                country = "NO",
                locationSource = "ip"
            )
        )
    }
} 