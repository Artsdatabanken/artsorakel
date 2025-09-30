package no.artsdatabanken.artsorakel.core.errors

import no.artsdatabanken.artsorakel.core.AppConfig

/**
 * Base sealed class for all application errors.
 * Provides structured error handling with categorization and user-friendly messages.
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
    open val isRecoverable: Boolean = true
) {
    
    /**
     * Network-related errors
     */
    sealed class NetworkError(
        message: String,
        cause: Throwable? = null,
        isRecoverable: Boolean = true
    ) : AppError(message, cause, isRecoverable) {
        
        data class ConnectionError(
            override val cause: Throwable? = null
        ) : NetworkError(
            message = "Unable to connect to the server. Please check your internet connection.",
            cause = cause,
            isRecoverable = true
        )
        
        data class TimeoutError(
            override val cause: Throwable? = null
        ) : NetworkError(
            message = "The request timed out. Please try again.",
            cause = cause,
            isRecoverable = true
        )
        
        data class ServerError(
            val statusCode: Int? = null,
            override val cause: Throwable? = null
        ) : NetworkError(
            message = "Server error${statusCode?.let { " ($it)" } ?: ""}. Please try again later.",
            cause = cause,
            isRecoverable = true
        )
        
        data class ApiError(
            val apiMessage: String? = null,
            override val cause: Throwable? = null
        ) : NetworkError(
            message = apiMessage ?: "An error occurred while processing your request.",
            cause = cause,
            isRecoverable = true
        )
    }
    
    /**
     * Image processing errors
     */
    sealed class ImageError(
        message: String,
        cause: Throwable? = null,
        isRecoverable: Boolean = true
    ) : AppError(message, cause, isRecoverable) {
        
        data class InvalidImageUri(
            val uri: String? = null,
            override val cause: Throwable? = null
        ) : ImageError(
            message = "The selected image could not be accessed. Please try selecting a different image.",
            cause = cause,
            isRecoverable = true
        )
        
        data class ImageProcessingError(
            override val cause: Throwable? = null
        ) : ImageError(
            message = "Failed to process the image. Please try with a different image.",
            cause = cause,
            isRecoverable = true
        )
        
        data class ImageCompressionError(
            override val cause: Throwable? = null
        ) : ImageError(
            message = "Failed to compress the image. Please try with a smaller image.",
            cause = cause,
            isRecoverable = true
        )
        
        data class NoImagesSelected(
            override val cause: Throwable? = null
        ) : ImageError(
            message = "No images selected. Please add at least one image to identify.",
            cause = cause,
            isRecoverable = true
        )
        
        data class AllImagesFailedProcessing(
            val failedCount: Int,
            override val cause: Throwable? = null
        ) : ImageError(
            message = "Failed to process $failedCount image${if (failedCount != 1) "s" else ""}. Please try with different images.",
            cause = cause,
            isRecoverable = true
        )
    }
    
    /**
     * File system errors
     */
    sealed class FileError(
        message: String,
        cause: Throwable? = null,
        isRecoverable: Boolean = true
    ) : AppError(message, cause, isRecoverable) {
        
        data class FileNotFound(
            val filePath: String? = null,
            override val cause: Throwable? = null
        ) : FileError(
            message = "File not found${filePath?.let { ": $it" } ?: ""}.",
            cause = cause,
            isRecoverable = false
        )
        
        data class FileAccessDenied(
            val filePath: String? = null,
            override val cause: Throwable? = null
        ) : FileError(
            message = "Access denied to file${filePath?.let { ": $it" } ?: ""}. Please check permissions.",
            cause = cause,
            isRecoverable = true
        )
        
        data class InsufficientStorage(
            override val cause: Throwable? = null
        ) : FileError(
            message = "Insufficient storage space. Please free up some space and try again.",
            cause = cause,
            isRecoverable = true
        )
        
        data class FileCreationError(
            val fileName: String? = null,
            override val cause: Throwable? = null
        ) : FileError(
            message = "Failed to create file${fileName?.let { ": $fileName" } ?: ""}.",
            cause = cause,
            isRecoverable = true
        )
    }
    
    /**
     * API response parsing errors
     */
    sealed class DataError(
        message: String,
        cause: Throwable? = null,
        isRecoverable: Boolean = true
    ) : AppError(message, cause, isRecoverable) {
        
        data class NoResultsFound(
            override val cause: Throwable? = null
        ) : DataError(
            message = "No identifiable species found in the results. Try with a clearer image or different angle.",
            cause = cause,
            isRecoverable = true
        )
        
        data class InvalidApiResponse(
            override val cause: Throwable? = null
        ) : DataError(
            message = "Received invalid response from server. Please try again.",
            cause = cause,
            isRecoverable = true
        )
        
        data class DataParsingError(
            override val cause: Throwable? = null
        ) : DataError(
            message = "Failed to parse server response. Please try again.",
            cause = cause,
            isRecoverable = true
        )
    }
    
    /**
     * Permission-related errors
     */
    sealed class PermissionError(
        message: String,
        cause: Throwable? = null,
        isRecoverable: Boolean = true
    ) : AppError(message, cause, isRecoverable) {
        
        data class CameraPermissionDenied(
            override val cause: Throwable? = null
        ) : PermissionError(
            message = "Camera permission is required to take photos. Please grant permission in settings.",
            cause = cause,
            isRecoverable = true
        )
        
        data class StoragePermissionDenied(
            override val cause: Throwable? = null
        ) : PermissionError(
            message = "Storage permission is required to access images. Please grant permission in settings.",
            cause = cause,
            isRecoverable = true
        )
    }
    
    /**
     * Unknown or unexpected errors
     */
    data class UnknownError(
        override val cause: Throwable? = null
    ) : AppError(
        message = "An unexpected error occurred. Please try again.",
        cause = cause,
        isRecoverable = true
    )
}

/**
 * Extension function to get user-friendly error message based on app configuration
 */
fun AppError.getUserMessage(): String {
    return if (AppConfig.Debug.IS_DEBUG_BUILD && cause != null) {
        "$message\n\nDebug info: ${cause?.message}"
    } else {
        message
    }
}

/**
 * Extension function to log errors consistently
 */
fun AppError.logError(tag: String = "AppError") {
    if (AppConfig.Debug.ENABLE_LOGGING) {
        val errorInfo = buildString {
            append("[$tag] ")
            append(this@logError::class.simpleName)
            append(": ")
            append(message)
            cause?.let { 
                append("\nCause: ")
                append(it.message)
                append("\nStack trace: ")
                append(it.stackTraceToString())
            }
        }
        println(errorInfo)
        
        // Also print stack trace for debugging
        cause?.printStackTrace()
    }
} 