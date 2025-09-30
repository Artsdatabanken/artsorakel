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
     * Generates a unique filename for a thumbnail
     */
    private fun generateThumbnailFilename(index: Int): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "thumb_${timeStamp}_${index}.jpg"
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

}