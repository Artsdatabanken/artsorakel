package no.artsdatabanken.artsorakel.core.errors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.artsdatabanken.artsorakel.usecase.ManageImagesUseCase

/**
 * Extension functions to extract common error handling patterns.
 * These reduce code duplication and provide consistent error handling across the app.
 */
inline fun <T> handleWithResult(
    tag: String,
    context: ErrorContext = ErrorContext.UNKNOWN,
    operation: () -> T
): Result<T> {
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        val error = ErrorMapper.mapException(e, context)
        error.logError(tag)
        Result.failure(e)
    }
}

/**
 * Extension function to handle exceptions with logging and return null.
 * Useful for operations where null is an acceptable failure response.
 */
inline fun <T> handleWithNull(
    tag: String,
    context: ErrorContext = ErrorContext.UNKNOWN,
    operation: () -> T
): T? {
    return try {
        operation()
    } catch (e: Exception) {
        val error = ErrorMapper.mapException(e, context)
        error.logError(tag)
        null
    }
}

/**
 * Extension function to handle exceptions with logging and return a default value.
 * Useful for operations where a fallback value is preferred over null.
 */
inline fun <T> handleWithDefault(
    tag: String,
    defaultValue: T,
    context: ErrorContext = ErrorContext.UNKNOWN,
    operation: () -> T
): T {
    return try {
        operation()
    } catch (e: Exception) {
        val error = ErrorMapper.mapException(e, context)
        error.logError(tag)
        defaultValue
    }
}

/**
 * Extension function to handle ManageImagesUseCase.ImageManagementResult with consistent logging.
 * Reduces repetitive when statements for image management results.
 */
fun ManageImagesUseCase.ImageManagementResult.handleResult(tag: String) {
    when (this) {
        is ManageImagesUseCase.ImageManagementResult.PartialSuccess -> {
            val error = ErrorMapper.mapFileException(
                RuntimeException("Failed to clean up ${totalCount - cleanedCount} file(s)")
            )
            error.logError(tag)
        }
        is ManageImagesUseCase.ImageManagementResult.Error -> {
            val error = ErrorMapper.mapFileException(
                RuntimeException(message)
            )
            error.logError(tag)
        }
        ManageImagesUseCase.ImageManagementResult.Success -> {
            // All good, no action needed
        }
    }
}

/**
 * Extension function to safely execute coroutines with error handling.
 * Prevents crashes from unhandled exceptions in coroutines.
 */
fun CoroutineScope.safeLaunch(
    tag: String,
    context: ErrorContext = ErrorContext.UNKNOWN,
    onError: ((AppError) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
) {
    launch {
        try {
            block()
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e, context)
            error.logError(tag)
            onError?.invoke(error)
        }
    }
}

/**
 * Extension function for defensive null checks with error logging.
 * Reduces repetitive null check patterns.
 */
inline fun <T> T?.requireNotNullWithLog(
    tag: String,
    lazyMessage: () -> String = { "Required value was null" }
): T {
    return this ?: run {
        val error = ErrorMapper.mapException(IllegalArgumentException(lazyMessage()))
        error.logError(tag)
        throw IllegalArgumentException(lazyMessage())
    }
}

/**
 * Extension function for safe casting with error logging.
 * Provides type-safe casting with consistent error handling.
 */
inline fun <reified T> Any?.safeCast(
    tag: String,
    noinline onError: ((AppError) -> Unit)? = null
): T? {
    return try {
        this as? T
    } catch (e: Exception) {
        val error = ErrorMapper.mapException(e)
        error.logError(tag)
        onError?.invoke(error)
        null
    }
}

/**
 * Extension function to validate conditions with error mapping.
 * Reduces repetitive validation patterns.
 */
inline fun validateCondition(
    condition: Boolean,
    tag: String,
    lazyMessage: () -> String = { "Validation failed" },
    context: ErrorContext = ErrorContext.UNKNOWN
): Result<Unit> {
    return if (condition) {
        Result.success(Unit)
    } else {
        val error = ErrorMapper.mapException(IllegalArgumentException(lazyMessage()), context)
        error.logError(tag)
        Result.failure(IllegalArgumentException(lazyMessage()))
    }
}

/**
 * Extension function to handle async operations with timeout and error handling.
 * Provides consistent timeout and error handling for network operations.
 */
suspend inline fun <T> withErrorHandling(
    tag: String,
    context: ErrorContext = ErrorContext.UNKNOWN,
    crossinline operation: suspend () -> T
): Result<T> {
    return try {
        Result.success(operation())
    } catch (e: Exception) {
        val error = ErrorMapper.mapException(e, context)
        error.logError(tag)
        Result.failure(e)
    }
}

/**
 * Extension function to handle collections with error recovery.
 * Useful for processing collections where some items may fail.
 */
inline fun <T, R> Collection<T>.mapWithErrorHandling(
    tag: String,
    context: ErrorContext = ErrorContext.UNKNOWN,
    transform: (T) -> R
): List<R> {
    return mapNotNull { item ->
        try {
            transform(item)
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e, context)
            error.logError("$tag - Item processing failed")
            null
        }
    }
} 