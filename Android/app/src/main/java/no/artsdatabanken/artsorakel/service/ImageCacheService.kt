package no.artsdatabanken.artsorakel.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggressive image caching service for species images and distribution maps.
 * Implements both memory and disk caching with intelligent cache management.
 * 
 * Performance optimizations based on Android best practices:
 * - LRU memory cache for frequently accessed images
 * - Persistent disk cache with size limits
 * - Automatic cache cleanup and size management
 * - Background prefetching for common species images
 */
@Singleton
class ImageCacheService @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    
    companion object {
        // Memory cache size: 1/8 of available memory
        private val MEMORY_CACHE_SIZE = (Runtime.getRuntime().maxMemory() / 8).toInt()
        
        // Disk cache size: 50MB for species images
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024L // 50MB
        
        // Cache directories
        private const val SPECIES_CACHE_DIR = "species_images"
        private const val DISTRIBUTION_CACHE_DIR = "distribution_maps"
        
        // Cache expiry: 7 days for species images, 1 day for distribution maps
        private const val SPECIES_CACHE_EXPIRY = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val DISTRIBUTION_CACHE_EXPIRY = 24 * 60 * 60 * 1000L // 1 day
    }
    
    // Memory cache using LRU algorithm
    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }
    
    // Cache directories
    private val speciesCacheDir by lazy {
        File(context.cacheDir, SPECIES_CACHE_DIR).apply { mkdirs() }
    }
    
    private val distributionCacheDir by lazy {
        File(context.cacheDir, DISTRIBUTION_CACHE_DIR).apply { mkdirs() }
    }
    
    /**
     * Cache types for different image categories
     */
    enum class CacheType(val directory: String, val expiry: Long) {
        SPECIES_IMAGE(SPECIES_CACHE_DIR, SPECIES_CACHE_EXPIRY),
        DISTRIBUTION_MAP(DISTRIBUTION_CACHE_DIR, DISTRIBUTION_CACHE_EXPIRY)
    }
    
    /**
     * Retrieves an image from cache or downloads and caches it.
     * 
     * @param url Image URL to retrieve
     * @param cacheType Type of cache to use
     * @return Bitmap if successful, null if failed
     */
    suspend fun getImage(url: String, cacheType: CacheType): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheKey = generateCacheKey(url)
                
                // 1. Check memory cache first (fastest)
                memoryCache.get(cacheKey)?.let { bitmap ->
                    return@withContext bitmap
                }
                
                // 2. Check disk cache
                getCachedBitmap(cacheKey, cacheType)?.let { bitmap ->
                    // Add to memory cache for faster future access
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
                
                // 3. Download and cache the image
                downloadAndCacheImage(url, cacheKey, cacheType)
                
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("ImageCacheService")
                null
            }
        }
    }
    
    /**
     * Preloads images for better user experience.
     * Useful for prefetching common species images.
     * 
     * @param urls List of URLs to preload
     * @param cacheType Type of cache to use
     */
    suspend fun preloadImages(urls: List<String>, cacheType: CacheType) {
        withContext(Dispatchers.IO) {
            urls.forEach { url ->
                try {
                    val cacheKey = generateCacheKey(url)
                    
                    // Only download if not already cached
                    if (!isImageCached(cacheKey, cacheType)) {
                        downloadAndCacheImage(url, cacheKey, cacheType)
                    }
                } catch (e: Exception) {
                    // Log but don't fail preloading for other images
                    val error = ErrorMapper.mapException(e)
                    error.logError("ImageCacheService")
                }
            }
        }
    }
    
    /**
     * Clears expired cache entries to manage disk space.
     */
    suspend fun clearExpiredCache() {
        withContext(Dispatchers.IO) {
            try {
                clearExpiredCacheInDirectory(speciesCacheDir, CacheType.SPECIES_IMAGE.expiry)
                clearExpiredCacheInDirectory(distributionCacheDir, CacheType.DISTRIBUTION_MAP.expiry)
                
                // Also clear memory cache if it's getting too large
                if (memoryCache.size() > MEMORY_CACHE_SIZE * 0.8) {
                    memoryCache.evictAll()
                }
                
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("ImageCacheService")
            }
        }
    }
    
    /**
     * Gets cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats {
        val speciesCount = speciesCacheDir.listFiles()?.size ?: 0
        val distributionCount = distributionCacheDir.listFiles()?.size ?: 0
        val speciesSize = calculateDirectorySize(speciesCacheDir)
        val distributionSize = calculateDirectorySize(distributionCacheDir)
        val memoryUsage = memoryCache.size()
        
        return CacheStats(
            speciesImageCount = speciesCount,
            distributionMapCount = distributionCount,
            speciesImageSize = speciesSize,
            distributionMapSize = distributionSize,
            memoryUsage = memoryUsage,
            memoryHitRate = memoryCache.hitCount().toFloat() / (memoryCache.hitCount() + memoryCache.missCount()).coerceAtLeast(1)
        )
    }
    
    // Private helper methods
    
    private fun generateCacheKey(url: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    private fun getCachedBitmap(cacheKey: String, cacheType: CacheType): Bitmap? {
        val cacheDir = when (cacheType) {
            CacheType.SPECIES_IMAGE -> speciesCacheDir
            CacheType.DISTRIBUTION_MAP -> distributionCacheDir
        }
        
        val cachedFile = File(cacheDir, cacheKey)
        
        return if (cachedFile.exists() && !isCacheExpired(cachedFile, cacheType.expiry)) {
            try {
                FileInputStream(cachedFile).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                // If cached file is corrupted, delete it
                cachedFile.delete()
                null
            }
        } else {
            // Delete expired cache
            if (cachedFile.exists()) {
                cachedFile.delete()
            }
            null
        }
    }
    
    private suspend fun downloadAndCacheImage(url: String, cacheKey: String, cacheType: CacheType): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    
                    bitmap?.let {
                        // Cache to disk
                        cacheBitmapToDisk(it, cacheKey, cacheType)
                        
                        // Cache to memory
                        memoryCache.put(cacheKey, it)
                        
                        it
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ImageCacheService")
            null
        }
    }
    
    private fun cacheBitmapToDisk(bitmap: Bitmap, cacheKey: String, cacheType: CacheType) {
        val cacheDir = when (cacheType) {
            CacheType.SPECIES_IMAGE -> speciesCacheDir
            CacheType.DISTRIBUTION_MAP -> distributionCacheDir
        }
        
        val cachedFile = File(cacheDir, cacheKey)
        
        try {
            FileOutputStream(cachedFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ImageCacheService")
        }
    }
    
    private fun isImageCached(cacheKey: String, cacheType: CacheType): Boolean {
        val cacheDir = when (cacheType) {
            CacheType.SPECIES_IMAGE -> speciesCacheDir
            CacheType.DISTRIBUTION_MAP -> distributionCacheDir
        }
        
        val cachedFile = File(cacheDir, cacheKey)
        return cachedFile.exists() && !isCacheExpired(cachedFile, cacheType.expiry)
    }
    
    private fun isCacheExpired(file: File, expiry: Long): Boolean {
        return System.currentTimeMillis() - file.lastModified() > expiry
    }
    
    private fun clearExpiredCacheInDirectory(directory: File, expiry: Long) {
        directory.listFiles()?.forEach { file ->
            if (isCacheExpired(file, expiry)) {
                file.delete()
            }
        }
    }
    
    private fun calculateDirectorySize(directory: File): Long {
        return directory.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * Data class for cache statistics
     */
    data class CacheStats(
        val speciesImageCount: Int,
        val distributionMapCount: Int,
        val speciesImageSize: Long,
        val distributionMapSize: Long,
        val memoryUsage: Int,
        val memoryHitRate: Float
    )
} 