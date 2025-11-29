package no.artsdatabanken.artsorakel.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale

/**
 * Service for creating and managing thumbnails for identification history
 */
@Singleton
class ThumbnailService @Inject constructor() {
    
    companion object {
        private const val THUMBNAIL_WIDTH = 200
        private const val THUMBNAIL_HEIGHT = 200
        private const val THUMBNAIL_QUALITY = 80
        private const val THUMBNAILS_FOLDER = "thumbnails"

        private const val FULL_SIZE_WIDTH = 1024
        private const val FULL_SIZE_HEIGHT = 1024
        private const val FULL_SIZE_QUALITY = 85
        private const val FULL_SIZE_FOLDER = "images"
    }
    
    /**
     * Creates thumbnails from a list of image URIs and saves them to internal storage
     * @param context Application context
     * @param imageUris List of image URIs to create thumbnails from
     * @return List of file paths to saved thumbnails, empty list on failure
     */
    suspend fun createAndSaveThumbnails(
        context: Context,
        imageUris: List<Uri>
    ): List<String> = withContext(Dispatchers.IO) {
        val savedThumbnails = mutableListOf<String>()
        
        try {
            // Create thumbnails directory if it doesn't exist
            val thumbnailsDir = File(context.filesDir, THUMBNAILS_FOLDER)
            if (!thumbnailsDir.exists()) {
                thumbnailsDir.mkdirs()
            }
            
            imageUris.forEachIndexed { index, uri ->
                try {
                    // Load and resize the image to create thumbnail
                    val bitmap = loadAndResizeImage(context, uri)
                    
                    if (bitmap != null) {
                        // Generate unique filename for thumbnail
                        val filename = generateThumbnailFilename(index)
                        val thumbnailFile = File(thumbnailsDir, filename)
                        
                        // Save thumbnail to internal storage
                        FileOutputStream(thumbnailFile).use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
                        }
                        
                        savedThumbnails.add(thumbnailFile.absolutePath)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    val error = ErrorMapper.mapException(e)
                    error.logError("ThumbnailService")
                    // Continue with other images even if one fails
                }
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ThumbnailService")
        }
        
        savedThumbnails
    }

    /**
     * Creates full-size images (1024x1024) from a list of image URIs and saves them to internal storage.
     * These are used for reporting historical observations to artsobservasjoner.no
     * @param context Application context
     * @param imageUris List of image URIs to save
     * @return List of file paths to saved images, empty list on failure
     */
    suspend fun createAndSaveFullSizeImages(
        context: Context,
        imageUris: List<Uri>
    ): List<String> = withContext(Dispatchers.IO) {
        val savedImages = mutableListOf<String>()

        try {
            // Create images directory if it doesn't exist
            val imagesDir = File(context.filesDir, FULL_SIZE_FOLDER)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            imageUris.forEachIndexed { index, uri ->
                try {
                    // Load and resize the image to full size
                    val bitmap = loadAndResizeImageToSize(context, uri, FULL_SIZE_WIDTH, FULL_SIZE_HEIGHT)

                    if (bitmap != null) {
                        // Generate unique filename
                        val filename = generateFullSizeFilename(index)
                        val imageFile = File(imagesDir, filename)

                        // Save image to internal storage
                        FileOutputStream(imageFile).use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, FULL_SIZE_QUALITY, outputStream)
                        }

                        savedImages.add(imageFile.absolutePath)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    val error = ErrorMapper.mapException(e)
                    error.logError("ThumbnailService")
                    // Continue with other images even if one fails
                }
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ThumbnailService")
        }

        savedImages
    }

    /**
     * Deletes full-size image files from internal storage
     * @param imagePaths List of image file paths to delete
     */
    suspend fun deleteFullSizeImages(imagePaths: List<String>) = withContext(Dispatchers.IO) {
        imagePaths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("ThumbnailService")
            }
        }
    }

    /**
     * Loads an image from URI and resizes it to thumbnail size
     */
    private fun loadAndResizeImage(context: Context, uri: Uri): Bitmap? {
        return try {
            // Load the image as bitmap using modern approach
            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use ImageDecoder for API 28+
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            // Calculate scaled dimensions maintaining aspect ratio
            val originalWidth = originalBitmap.width
            val originalHeight = originalBitmap.height
            
            val scale = minOf(
                THUMBNAIL_WIDTH.toFloat() / originalWidth,
                THUMBNAIL_HEIGHT.toFloat() / originalHeight
            )
            
            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()
            
            // Create scaled bitmap
            val scaledBitmap = originalBitmap.scale(scaledWidth, scaledHeight)
            
            // Clean up original bitmap if it's different from scaled
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            
            scaledBitmap
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ThumbnailService")
            null
        }
    }
    
    /**
     * Loads an image from URI and resizes it to specified size
     */
    private fun loadAndResizeImageToSize(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            // Load the image as bitmap using modern approach
            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use ImageDecoder for API 28+
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            // Calculate scaled dimensions maintaining aspect ratio
            val originalWidth = originalBitmap.width
            val originalHeight = originalBitmap.height

            val scale = minOf(
                targetWidth.toFloat() / originalWidth,
                targetHeight.toFloat() / originalHeight
            )

            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()

            // Create scaled bitmap
            val scaledBitmap = originalBitmap.scale(scaledWidth, scaledHeight)

            // Clean up original bitmap if it's different from scaled
            if (scaledBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            scaledBitmap
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ThumbnailService")
            null
        }
    }

    /**
     * Generates a unique filename for a thumbnail
     */
    private fun generateThumbnailFilename(index: Int): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "thumb_${timeStamp}_${index}.jpg"
    }

    /**
     * Generates a unique filename for a full-size image
     */
    private fun generateFullSizeFilename(index: Int): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "img_${timeStamp}_${index}.jpg"
    }

    /**
     * Deletes thumbnail files from internal storage
     * @param thumbnailPaths List of thumbnail file paths to delete
     */
    suspend fun deleteThumbnails(thumbnailPaths: List<String>) = withContext(Dispatchers.IO) {
        thumbnailPaths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                val error = ErrorMapper.mapException(e)
                error.logError("ThumbnailService")
            }
        }
    }
    
    /**
     * Clears all thumbnail files from internal storage
     * @param context Application context
     */
    suspend fun clearAllThumbnails(context: Context) = withContext(Dispatchers.IO) {
        try {
            val thumbnailsDir = File(context.filesDir, THUMBNAILS_FOLDER)
            if (thumbnailsDir.exists()) {
                thumbnailsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("thumb_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ThumbnailService")
        }
    }

    /**
     * Clears all full-size image files from internal storage
     * @param context Application context
     */
    suspend fun clearAllFullSizeImages(context: Context) = withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(context.filesDir, FULL_SIZE_FOLDER)
            if (imagesDir.exists()) {
                imagesDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("img_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ThumbnailService")
        }
    }

}