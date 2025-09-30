package no.artsdatabanken.artsorakel.utils

import no.artsdatabanken.artsorakel.core.logging.AppLogger

object Logger {
    fun d(tag: String, message: String) = AppLogger.d(AppLogger.Category.UI, message, tag)
    fun i(tag: String, message: String) = AppLogger.i(AppLogger.Category.UI, message, tag)
    fun w(tag: String, message: String, throwable: Throwable? = null) = AppLogger.w(AppLogger.Category.UI, message, tag, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = AppLogger.e(AppLogger.Category.UI, message, tag, throwable)
}

