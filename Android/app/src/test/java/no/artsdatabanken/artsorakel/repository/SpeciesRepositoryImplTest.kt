package no.artsdatabanken.artsorakel.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.artsdatabanken.artsorakel.network.ApiResponse
import no.artsdatabanken.artsorakel.network.ApiService
import no.artsdatabanken.artsorakel.network.PredictionDto
import no.artsdatabanken.artsorakel.network.TaxaInfoDto
import no.artsdatabanken.artsorakel.network.TaxonItemDto
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeciesRepositoryImplTest {

    private lateinit var mockApiService: ApiService
    private lateinit var repository: SpeciesRepositoryImpl

    private val testImageData = "test_image".toByteArray()
    private val testImageFilenames = listOf("test_image.jpg")

    @Before
    fun setup() {
        mockApiService = mockk(relaxed = true)
        repository = SpeciesRepositoryImpl(mockApiService)
    }

    @Test
    fun `identifySpecies - success with valid response`() = runTest {
        // Arrange
        val taxonItem = TaxonItemDto(
            probability = 0.85,
            scientificName = "Testus scientificus",
            scientificNameId = "123",
            vernacularNames = mapOf("en" to "Test Species", "nb" to "Test Art"),
            groupNames = mapOf("en" to "Test Group", "nb" to "Test Gruppe"),
            name = "Testus scientificus",
            infoUrl = "https://test.com/info",
            picture = "https://test.com/image.jpg",
            redListCategory = null,
            invasiveCategory = null
        )
        
        val taxaInfoDto = TaxaInfoDto(items = listOf(taxonItem), type = "species")
        val predictionDto = PredictionDto(regionGroupId = "1", taxa = taxaInfoDto)
        val apiResponse = ApiResponse(predictions = listOf(predictionDto), modelInfo = null, warnings = null, uploadId = "test-upload-id", uploadSecret = "test-upload-secret")

        coEvery { mockApiService.classifyImages(any(), any(), any(), any()) } returns apiResponse

        // Act
        val result = repository.identifySpecies(listOf(testImageData), testImageFilenames)

        // Assert
        assertTrue(result.isSuccess)
        val identificationResult = result.getOrNull()!!
        assertEquals(1, identificationResult.predictions.size)
        assertEquals("123", identificationResult.predictions[0].id)
        assertEquals(mapOf("en" to "Test Species", "nb" to "Test Art"), identificationResult.predictions[0].vernacularNames)
        assertEquals("Testus scientificus", identificationResult.predictions[0].scientificName)
        assertEquals(0.85, identificationResult.predictions[0].probability)
    }

    @Test
    fun `identifySpecies - success with empty response`() = runTest {
        // Arrange
        val apiResponse = ApiResponse(predictions = emptyList(), modelInfo = null, warnings = null, uploadId = null, uploadSecret = null)
        coEvery { mockApiService.classifyImages(any(), any(), any(), any()) } returns apiResponse

        // Act
        val result = repository.identifySpecies(listOf(testImageData), testImageFilenames)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `identifySpecies - handles network timeout exception`() = runTest {
        // Arrange
        coEvery { mockApiService.classifyImages(any(), any()) } throws SocketTimeoutException("Timeout")

        // Act
        val result = repository.identifySpecies(listOf(testImageData), testImageFilenames)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `identifySpecies - handles connection exception`() = runTest {
        // Arrange
        coEvery { mockApiService.classifyImages(any(), any()) } throws ConnectException("Connection failed")

        // Act
        val result = repository.identifySpecies(listOf(testImageData), testImageFilenames)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `identifySpecies - handles general IO exception`() = runTest {
        // Arrange
        coEvery { mockApiService.classifyImages(any(), any()) } throws IOException("IO Error")

        // Act
        val result = repository.identifySpecies(listOf(testImageData), testImageFilenames)

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `isIdentificationInProgress - returns false when no job active`() {
        // Act & Assert
        val result = repository.isIdentificationInProgress()
        assertEquals(false, result)
    }

    @Test
    fun `cancelIdentification - completes without error`() = runTest {
        // Act (should not throw)
        repository.cancelIdentification()
        
        // Assert - no exception thrown
        assertTrue(true)
    }
} 