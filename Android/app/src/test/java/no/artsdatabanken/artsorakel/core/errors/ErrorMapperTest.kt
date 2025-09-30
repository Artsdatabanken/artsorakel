package no.artsdatabanken.artsorakel.core.errors

import org.junit.Test
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ErrorMapperTest {

    @Test
    fun `mapNetworkException - maps timeout exception correctly`() {
        // Arrange
        val timeoutException = SocketTimeoutException("Connection timed out")

        // Act
        val result = ErrorMapper.mapNetworkException(timeoutException)

        // Assert
        assertTrue(result is AppError.NetworkError.TimeoutError)
    }

    @Test
    fun `mapNetworkException - maps connection exception correctly`() {
        // Arrange
        val connectionException = ConnectException("Connection refused")

        // Act
        val result = ErrorMapper.mapNetworkException(connectionException)

        // Assert
        assertTrue(result is AppError.NetworkError.ConnectionError)
    }

    @Test
    fun `mapFileException - maps file not found exception correctly`() {
        // Arrange
        val fileNotFoundException = FileNotFoundException("File not found")

        // Act
        val result = ErrorMapper.mapFileException(fileNotFoundException)

        // Assert
        assertTrue(result is AppError.FileError.FileNotFound)
    }

    @Test
    fun `mapImageException - maps illegal argument exception correctly`() {
        // Arrange
        val illegalArgumentException = IllegalArgumentException("Invalid image format")

        // Act
        val result = ErrorMapper.mapImageException(illegalArgumentException)

        // Assert
        assertTrue(result is AppError.ImageError.ImageProcessingError)
    }

    @Test
    fun `createNoImagesSelectedError - returns correct error type`() {
        // Act
        val result = ErrorMapper.createNoImagesSelectedError()

        // Assert
        assertEquals(AppError.ImageError.NoImagesSelected::class, result::class)
    }

    @Test
    fun `createAllImagesFailedError - returns correct error type`() {
        // Act
        val result = ErrorMapper.createAllImagesFailedError(5)

        // Assert
        assertEquals(AppError.ImageError.AllImagesFailedProcessing::class, result::class)
    }

    @Test
    fun `createNoResultsFoundError - returns correct error type`() {
        // Act
        val result = ErrorMapper.createNoResultsFoundError()

        // Assert
        assertEquals(AppError.DataError.NoResultsFound::class, result::class)
    }
} 