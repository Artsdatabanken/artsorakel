package no.artsdatabanken.artsorakel.usecase

import android.net.Uri
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManageImagesUseCaseTest {

    private lateinit var mockImageProcessingService: ImageProcessingService
    private lateinit var manageImagesUseCase: ManageImagesUseCase
    
    private val mockUri1 = mockk<Uri>()
    private val mockUri2 = mockk<Uri>()
    private val mockCroppedUri1 = mockk<Uri>()
    private val mockCroppedUri2 = mockk<Uri>()

    @Before
    fun setup() {
        mockImageProcessingService = mockk(relaxed = true)
        manageImagesUseCase = ManageImagesUseCase(mockImageProcessingService)
    }

    @Test
    fun `addImagePair - successfully adds new image pair`() = runTest {
        // Arrange
        val currentPairs = emptyList<ImagePair>()
        
        // Act
        val result = manageImagesUseCase.addImagePair(currentPairs, mockCroppedUri1, mockUri1)

        // Assert
        assertEquals(1, result.first.size)
        assertEquals(mockUri1, result.first.first().originalUri)
        assertEquals(mockCroppedUri1, result.first.first().croppedUri)
        assertTrue(result.second is ManageImagesUseCase.ImageManagementResult.Success)
    }

    @Test
    fun `addImagePair - replaces existing image pair with same original URI`() = runTest {
        // Arrange
        val existingPair = ImagePair(mockCroppedUri1, mockUri1)
        val currentPairs = listOf(existingPair)
        coEvery { mockImageProcessingService.deleteFile(mockCroppedUri1) } returns true

        // Act
        val result = manageImagesUseCase.addImagePair(currentPairs, mockCroppedUri2, mockUri1)

        // Assert
        assertEquals(1, result.first.size)
        assertEquals(mockUri1, result.first.first().originalUri)
        assertEquals(mockCroppedUri2, result.first.first().croppedUri) // New cropped URI
        assertTrue(result.second is ManageImagesUseCase.ImageManagementResult.Success)
    }

    @Test
    fun `removeImagePair - successfully removes image pair`() = runTest {
        // Arrange
        val imagePair = ImagePair(mockCroppedUri1, mockUri1)
        val currentPairs = listOf(imagePair)
        coEvery { mockImageProcessingService.deleteFiles(any()) } returns 2

        // Act
        val result = manageImagesUseCase.removeImagePair(currentPairs, imagePair)

        // Assert
        assertTrue(result.first.isEmpty())
        assertTrue(result.second is ManageImagesUseCase.ImageManagementResult.Success)
    }

    @Test
    fun `clearAllImages - successfully clears all images`() = runTest {
        // Arrange
        val imagePair1 = ImagePair(mockCroppedUri1, mockUri1)
        val imagePair2 = ImagePair(mockCroppedUri2, mockUri2)
        val currentPairs = listOf(imagePair1, imagePair2)
        coEvery { mockImageProcessingService.deleteFiles(any()) } returns 4

        // Act
        val result = manageImagesUseCase.clearAllImages(currentPairs)

        // Assert
        assertTrue(result is ManageImagesUseCase.ImageManagementResult.Success)
    }

    @Test
    fun `clearAllImages - handles empty list`() = runTest {
        // Act
        val result = manageImagesUseCase.clearAllImages(emptyList())

        // Assert
        assertTrue(result is ManageImagesUseCase.ImageManagementResult.Success)
    }

    @Test
    fun `isReadyForClassification - returns true when has images`() {
        // Arrange
        val imagePair = ImagePair(mockCroppedUri1, mockUri1)
        val currentPairs = listOf(imagePair)

        // Act & Assert
        assertTrue(manageImagesUseCase.isReadyForClassification(currentPairs))
    }

    @Test
    fun `isReadyForClassification - returns false when no images`() {
        // Act & Assert
        assertFalse(manageImagesUseCase.isReadyForClassification(emptyList()))
    }

    @Test
    fun `getCroppedUris - returns cropped URIs`() {
        // Arrange
        val imagePair1 = ImagePair(mockCroppedUri1, mockUri1)
        val imagePair2 = ImagePair(mockCroppedUri2, mockUri2)
        val currentPairs = listOf(imagePair1, imagePair2)

        // Act
        val croppedUris = manageImagesUseCase.getCroppedUris(currentPairs)

        // Assert
        assertEquals(2, croppedUris.size)
        assertTrue(croppedUris.contains(mockCroppedUri1))
        assertTrue(croppedUris.contains(mockCroppedUri2))
    }
} 