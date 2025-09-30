package no.artsdatabanken.artsorakel.core.errors

import retrofit2.HttpException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import android.net.Uri

/**
 * Utility object to map common exceptions to domain-specific AppError instances.
 * This centralizes error mapping logic and ensures consistent error handling.
 */
object ErrorMapper {
    
    /**
     * Maps network-related exceptions to NetworkError
     */
    fun mapNetworkException(exception: Throwable): AppError.NetworkError {
        return when (exception) {
            is HttpException -> {
                when (exception.code()) {
                    in 400..499 -> AppError.NetworkError.ApiError(
                        apiMessage = "Client error (${exception.code()}): ${exception.message()}",
                        cause = exception
                    )
                    in 500..599 -> AppError.NetworkError.ServerError(
                        statusCode = exception.code(),
                        cause = exception
                    )
                    else -> AppError.NetworkError.ApiError(
                        apiMessage = "HTTP error (${exception.code()}): ${exception.message()}",
                        cause = exception
                    )
                }
            }
            is SocketTimeoutException -> AppError.NetworkError.TimeoutError(cause = exception)
            is ConnectException -> AppError.NetworkError.ConnectionError(cause = exception)
            is UnknownHostException -> AppError.NetworkError.ConnectionError(cause = exception)
            else -> AppError.NetworkError.ApiError(cause = exception)
        }
    }
    
    /**
     * Maps file-related exceptions to FileError
     */
    fun mapFileException(exception: Throwable, filePath: String? = null): AppError.FileError {
        return when (exception) {
            is FileNotFoundException -> AppError.FileError.FileNotFound(
                filePath = filePath,
                cause = exception
            )
            is IOException -> {
                when {
                    exception.message?.contains("No space left", ignoreCase = true) == true -> 
                        AppError.FileError.InsufficientStorage(cause = exception)
                    exception.message?.contains("Permission denied", ignoreCase = true) == true -> 
                        AppError.FileError.FileAccessDenied(filePath = filePath, cause = exception)
                    else -> AppError.FileError.FileCreationError(
                        fileName = filePath,
                        cause = exception
                    )
                }
            }
            else -> AppError.FileError.FileCreationError(
                fileName = filePath,
                cause = exception
            )
        }
    }
    
    /**
     * Maps image processing exceptions to ImageError
     */
    fun mapImageException(exception: Throwable, uri: Uri? = null): AppError.ImageError {
        return when (exception) {
            is IllegalArgumentException -> AppError.ImageError.ImageProcessingError(cause = exception)
            is OutOfMemoryError -> AppError.ImageError.ImageCompressionError(cause = exception)
            is IOException -> AppError.ImageError.InvalidImageUri(
                uri = uri?.toString(),
                cause = exception
            )
            else -> {
                // Check if it's a security-related exception by message
                if (exception.message?.contains("permission", ignoreCase = true) == true ||
                    exception.message?.contains("access", ignoreCase = true) == true) {
                    AppError.ImageError.InvalidImageUri(
                        uri = uri?.toString(),
                        cause = exception
                    )
                } else {
                    AppError.ImageError.ImageProcessingError(cause = exception)
                }
            }
        }
    }
    
    /**
     * Maps general exceptions to appropriate AppError types based on context
     */
    fun mapException(
        exception: Throwable,
        context: ErrorContext = ErrorContext.UNKNOWN
    ): AppError {
        return when (context) {
            ErrorContext.NETWORK -> mapNetworkException(exception)
            ErrorContext.FILE_OPERATION -> mapFileException(exception)
            ErrorContext.IMAGE_PROCESSING -> mapImageException(exception)
            ErrorContext.DATA_PARSING -> AppError.DataError.DataParsingError(cause = exception)
            ErrorContext.UNKNOWN -> {
                // Try to intelligently map based on exception type
                when (exception) {
                    is HttpException, is SocketTimeoutException, is ConnectException, is UnknownHostException -> 
                        mapNetworkException(exception)
                    is FileNotFoundException, is IOException -> 
                        mapFileException(exception)
                    is IllegalArgumentException, is OutOfMemoryError -> 
                        mapImageException(exception)
                    else -> AppError.UnknownError(cause = exception)
                }
            }
        }
    }
    
    /**
     * Creates an error for multiple failed image processing attempts
     */
    fun createAllImagesFailedError(failedCount: Int): AppError.ImageError.AllImagesFailedProcessing {
        return AppError.ImageError.AllImagesFailedProcessing(failedCount = failedCount)
    }
    
    /**
     * Creates an error for no images selected
     */
    fun createNoImagesSelectedError(): AppError.ImageError.NoImagesSelected {
        return AppError.ImageError.NoImagesSelected()
    }
    
    /**
     * Creates an error for no results found
     */
    fun createNoResultsFoundError(): AppError.DataError.NoResultsFound {
        return AppError.DataError.NoResultsFound()
    }
}

/**
 * Context enum to help ErrorMapper make better decisions about error types
 */
enum class ErrorContext {
    NETWORK,
    FILE_OPERATION,
    IMAGE_PROCESSING,
    DATA_PARSING,
    UNKNOWN
} 