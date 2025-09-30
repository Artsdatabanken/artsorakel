package no.artsdatabanken.artsorakel.core.logging

import android.util.Log
import no.artsdatabanken.artsorakel.core.AppConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized logging framework for the application.
 * Provides structured logging with different levels and conditional output based on build configuration.
 */
object AppLogger {
    
    /**
     * Log levels for different types of messages
     */
    enum class LogLevel(val priority: Int, val tag: String) {
        VERBOSE(Log.VERBOSE, "V"),
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E")
    }
    
    /**
     * Categories for different types of events
     */
    enum class Category(val categoryName: String) {
        NETWORK("Network"),
        UI("UI"),
        IMAGE_PROCESSING("ImageProcessing"),
        SPECIES_IDENTIFICATION("SpeciesIdentification"),
        ERROR_HANDLING("ErrorHandling"),
        USER_ACTION("UserAction"),
        PERFORMANCE("Performance"),
        LIFECYCLE("Lifecycle")
    }
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * Log a verbose message
     */
    fun v(category: Category, message: String, tag: String? = null, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, category, message, tag, throwable)
    }
    
    /**
     * Log a debug message
     */
    fun d(category: Category, message: String, tag: String? = null, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, category, message, tag, throwable)
    }
    
    /**
     * Log an info message
     */
    fun i(category: Category, message: String, tag: String? = null, throwable: Throwable? = null) {
        log(LogLevel.INFO, category, message, tag, throwable)
    }
    
    /**
     * Log a warning message
     */
    fun w(category: Category, message: String, tag: String? = null, throwable: Throwable? = null) {
        log(LogLevel.WARN, category, message, tag, throwable)
    }
    
    /**
     * Log an error message
     */
    fun e(category: Category, message: String, tag: String? = null, throwable: Throwable? = null) {
        log(LogLevel.ERROR, category, message, tag, throwable)
    }
    
    /**
     * Core logging method that handles all log levels
     */
    private fun log(level: LogLevel, category: Category, message: String, tag: String?, throwable: Throwable?) {
        if (!shouldLog(level)) return
        
        val logTag = buildLogTag(category, tag)
        val formattedMessage = formatMessage(message, throwable)
        
        // Use Android's Log system
        when (level) {
            LogLevel.VERBOSE -> Log.v(logTag, formattedMessage, throwable)
            LogLevel.DEBUG -> Log.d(logTag, formattedMessage, throwable)
            LogLevel.INFO -> Log.i(logTag, formattedMessage, throwable)
            LogLevel.WARN -> Log.w(logTag, formattedMessage, throwable)
            LogLevel.ERROR -> Log.e(logTag, formattedMessage, throwable)
        }
        
        // Also print to console for unit tests and debugging
        if (AppConfig.Debug.IS_DEBUG_BUILD) {
            val timestamp = dateFormatter.format(Date())
            println("[$timestamp] ${level.tag}/$logTag: $formattedMessage")
            throwable?.printStackTrace()
        }
    }
    
    /**
     * Determine if we should log based on configuration and level
     */
    private fun shouldLog(level: LogLevel): Boolean {
        return when {
            // Always log errors and warnings
            level == LogLevel.ERROR || level == LogLevel.WARN -> true
            // Log other levels only if logging is enabled
            AppConfig.Debug.ENABLE_LOGGING -> true
            // In release builds, only log errors and warnings
            else -> false
        }
    }
    
    /**
     * Build a consistent log tag
     */
    private fun buildLogTag(category: Category, customTag: String?): String {
        return if (customTag != null) {
            "Artsorakel_${category.categoryName}_$customTag"
        } else {
            "Artsorakel_${category.categoryName}"
        }
    }
    
    /**
     * Format the log message with additional context
     */
    private fun formatMessage(message: String, throwable: Throwable?): String {
        return buildString {
            append(message)
            
            // Add throwable information if present
            throwable?.let { 
                append(" | Exception: ")
                append(it.javaClass.simpleName)
                append(" - ")
                append(it.message ?: "No message")
            }
        }
    }
    
    /**
     * Convenience methods for common scenarios
     */
    object Network {
        fun request(url: String, method: String = "GET") {
            d(Category.NETWORK, "Request: $method $url")
        }
        
        fun response(url: String, statusCode: Int, responseTime: Long) {
            i(Category.NETWORK, "Response: $url -> $statusCode (${responseTime}ms)")
        }
        
        fun error(url: String, error: Throwable) {
            e(Category.NETWORK, "Network error for $url", throwable = error)
        }
    }
    
    object UserAction {
        fun click(element: String, screen: String? = null) {
            i(Category.USER_ACTION, "User clicked: $element${screen?.let { " on $it" } ?: ""}")
        }
        
        fun navigation(from: String, to: String) {
            i(Category.USER_ACTION, "Navigation: $from -> $to")
        }
        
        fun imageSelected(count: Int, source: String) {
            i(Category.USER_ACTION, "Images selected: $count from $source")
        }
        
        fun speciesIdentificationStarted(imageCount: Int) {
            i(Category.USER_ACTION, "Species identification started with $imageCount images")
        }
    }
    
    object Performance {
        fun operationStart(operation: String): Long {
            val startTime = System.currentTimeMillis()
            d(Category.PERFORMANCE, "Started: $operation")
            return startTime
        }
        
        fun operationEnd(operation: String, startTime: Long) {
            val duration = System.currentTimeMillis() - startTime
            i(Category.PERFORMANCE, "Completed: $operation (${duration}ms)")
        }
        
        fun memoryUsage(context: String) {
            if (AppConfig.Debug.ENABLE_LOGGING) {
                val runtime = Runtime.getRuntime()
                val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                val maxMemory = runtime.maxMemory() / 1024 / 1024
                d(Category.PERFORMANCE, "Memory usage in $context: ${usedMemory}MB / ${maxMemory}MB")
            }
        }
    }
    
    object Lifecycle {
        fun activityCreated(activityName: String) {
            d(Category.LIFECYCLE, "Activity created: $activityName")
        }
        
        fun fragmentCreated(fragmentName: String) {
            d(Category.LIFECYCLE, "Fragment created: $fragmentName")
        }
        
        fun viewModelCreated(viewModelName: String) {
            d(Category.LIFECYCLE, "ViewModel created: $viewModelName")
        }
    }
} 