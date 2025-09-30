package no.artsdatabanken.artsorakel.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Service responsible for image processing and file operations.
 * Handles image resizing, compression, and file management for the application.
 */
class ImageProcessingService @Inject constructor() {
    
    /**
     * Resizes and compresses an image from a URI.
     * 
     * @param context Android context for content resolution
     * @param uri URI of the image to process
     * @param targetWidth Target width in pixels
     * @param targetHeight Target height in pixels  
     * @param quality Compression quality (0-100)
     * @return Result containing either the processed image bytes or an error
     */
    suspend fun resizeAndCompressImage(
        context: Context?,
        uri: Uri?,
        targetWidth: Int,
        targetHeight: Int,
        quality: Int
    ): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                // Defensive null checks
                if (context == null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Context cannot be null")
                    )
                }
                
                if (uri == null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("URI cannot be null")
                    )
                }
                
                // Validate parameters
                if (targetWidth <= 0 || targetHeight <= 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Target dimensions must be positive")
                    )
                }
                
                if (quality < 0 || quality > 100) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Quality must be between 0 and 100")
                    )
                }

                val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Unable to open input stream for URI: $uri")
                    )
                
                inputStream.use { // Ensures the stream is closed
                    val originalBitmap = BitmapFactory.decodeStream(it)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Unable to decode bitmap from URI: $uri")
                        )

                    // Resize (maintaining aspect ratio might be better, but request specified exact dimensions)
                    val scaledBitmap = originalBitmap.scale(targetWidth, targetHeight, false)

                    // Compress
                    val outputStream = ByteArrayOutputStream()
                    val compressionSuccess = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    
                    // Recycle bitmaps if not needed anymore (important for memory)
                    originalBitmap.recycle()
                    scaledBitmap.recycle()

                    if (!compressionSuccess) {
                        return@withContext Result.failure(
                            RuntimeException("Failed to compress image")
                        )
                    }

                    Result.success(outputStream.toByteArray())
                }
            } catch (e: Exception) {
                val error = ErrorMapper.mapImageException(e, uri)
                error.logError("ImageProcessingService")
                Result.failure(e)
            }
        }
    }
    
    /**
     * Processes multiple images in parallel using coroutines.
     * Based on Android performance best practices for multi-threading image processing.
     * 
     * @param context Android context for content resolution
     * @param uris List of URIs to process
     * @param targetWidth Target width in pixels
     * @param targetHeight Target height in pixels
     * @param quality Compression quality (0-100)
     * @return Triple containing successful processed images, filenames, and failed URIs
     */
    suspend fun processMultipleImages(
        context: Context,
        uris: List<Uri>,
        targetWidth: Int,
        targetHeight: Int,
        quality: Int
    ): Triple<List<ByteArray>, List<String>, List<Uri>> {
        return withContext(Dispatchers.IO) {
            // Process images in parallel using async for better performance
            // This approach is inspired by multi-threading best practices for image processing
            val deferredResults = uris.mapIndexed { index, uri ->
                async {
                    val imageResult = resizeAndCompressImage(context, uri, targetWidth, targetHeight, quality)
                    Triple(index, uri, imageResult)
                }
            }
            
            // Collect results while maintaining order
            val processedImages = mutableListOf<Pair<Int, ByteArray>>()
            val imageFilenames = mutableListOf<Pair<Int, String>>()
            val failedUris = mutableListOf<Uri>()
            
            // Await all results
            deferredResults.awaitAll().forEach { (index, uri, imageResult) ->
                imageResult.fold(
                    onSuccess = { imageBytes ->
                        processedImages.add(index to imageBytes)
                        imageFilenames.add(index to "image_$index.jpg")
                    },
                    onFailure = { exception ->
                        failedUris.add(uri)
                        val error = ErrorMapper.mapImageException(exception, uri)
                        error.logError("ImageProcessingService")
                    }
                )
            }
            
            // Sort by original index to maintain order and extract values
            val sortedImages = processedImages.sortedBy { it.first }.map { it.second }
            val sortedFilenames = imageFilenames.sortedBy { it.first }.map { it.second }
            
            Triple(sortedImages, sortedFilenames, failedUris)
        }
    }
    
    /**
     * Safely deletes a file from the given URI.
     * 
     * @param uri URI of the file to delete
     * @return true if the file was successfully deleted or didn't exist, false otherwise
     */
    suspend fun deleteFile(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    } else {
                        true // File doesn't exist, consider it successful
                    }
                } ?: false
            } catch (e: Exception) {
                val error = ErrorMapper.mapFileException(e, uri.path)
                error.logError("ImageProcessingService")
                false
            }
        }
    }
    
    /**
     * Safely deletes multiple files.
     * 
     * @param uris List of URIs to delete
     * @return Number of files successfully deleted
     */
    suspend fun deleteFiles(uris: List<Uri>): Int {
        return withContext(Dispatchers.IO) {
            var deletedCount = 0
            uris.forEach { uri ->
                if (deleteFile(uri)) {
                    deletedCount++
                }
            }
            deletedCount
        }
    }
} 