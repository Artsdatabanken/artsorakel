package no.artsdatabanken.artsorakel.core

import no.artsdatabanken.artsorakel.BuildConfig

/**
 * Centralized application configuration.
 * Uses BuildConfig to provide environment-specific values for testing vs production.
 */
object AppConfig {
    
    /**
     * Network configuration
     */
    object Network {
        const val BASE_URL: String = BuildConfig.API_BASE_URL
        const val TIMEOUT_SECONDS: Long = BuildConfig.NETWORK_TIMEOUT_SECONDS
        const val ENABLE_LOGGING: Boolean = BuildConfig.ENABLE_LOGGING

        /**
         * Bearer token selected based on the API endpoint.
         * Uses test token if BASE_URL contains ".test.", otherwise uses production token.
         */
        val BEARER_TOKEN: String = if (BASE_URL.contains(".test.")) {
            BuildConfig.API_BEARER_TOKEN_TEST
        } else {
            BuildConfig.API_BEARER_TOKEN
        }
    }
    
    /**
     * API configuration
     */
    object Api {
        /**
         * Application type sent to the API.
         * - "test" for non-release builds (debug, etc.)
         * - "Artsorakel x.x.x (android)" for release builds
         */
        val APPLICATION_TYPE: String = if (BuildConfig.IS_RELEASE_BUILD) {
            "Artsorakel ${BuildConfig.VERSION_NAME} (android)"
        } else {
            "test"
        }
    }
    
    /**
     * Development and debugging configuration
     */
    object Debug {
        const val ENABLE_LOGGING: Boolean = BuildConfig.ENABLE_LOGGING
        val IS_DEBUG_BUILD: Boolean = BuildConfig.DEBUG
    }
    
    /**
     * Timeout configuration (unchanged from Constants)
     */
    object Timeouts {
        const val TIMEOUT_SHOW_MESSAGE_MS = 4000L
        const val FRAGMENT_VISIBILITY_DELAY_MS = 500L
    }

    /**
     * RSS feed configuration
     */
    object RssFeed {
        const val RSS_FEED_URL: String = BuildConfig.RSS_FEED_URL
    }
} 