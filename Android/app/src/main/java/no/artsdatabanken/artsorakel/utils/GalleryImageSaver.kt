package no.artsdatabanken.artsorakel.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError

/**
 * Utility class for saving images to the device gallery in a custom "Artsorakel" folder.
 * Handles both modern (Android 10+) MediaStore API and legacy file storage.
 */
class GalleryImageSaver(private val context: Context) {

    companion object {
        private const val CUSTOM_FOLDER_NAME = "Artsorakel"
    }

    /**
     * Saves an image from URI to the gallery in DCIM/Artsorakel folder.
     * Preserves EXIF orientation data to prevent rotation issues.
     * @param imageUri The URI of the image to save
     * @return true if saved successfully, false otherwise
     */
    fun saveImageToGallery(imageUri: Uri): Boolean {
        return try {
            val filename = generateFileName()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore API for Android 10+ (Scoped Storage)
                saveWithMediaStorePreservingExif(imageUri, filename)
            } else {
                // Use legacy file storage for older Android versions
                saveWithLegacyStoragePreservingExif(imageUri, filename)
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("GalleryImageSaver")
            false
        }
    }

    /**
     * Saves a bitmap to the gallery in DCIM/Artsorakel folder.
     * @param bitmap The bitmap to save
     * @return true if saved successfully, false otherwise
     */
    fun saveImageToGallery(bitmap: Bitmap): Boolean {
        return try {
            val filename = generateFileName()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore API for Android 10+ (Scoped Storage)
                saveWithMediaStore(bitmap, filename)
            } else {
                // Use legacy file storage for older Android versions
                saveWithLegacyStorage(bitmap, filename)
            }
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("GalleryImageSaver")
            false
        }
    }

    /**
     * Save image using MediaStore API (Android 10+)
     */
    private fun saveWithMediaStore(bitmap: Bitmap, filename: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$CUSTOM_FOLDER_NAME")
            
            // Mark as pending while we write the file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        return imageUri?.let { uri ->
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                
                // Clear the pending flag
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                true
            } catch (e: Exception) {
                // If there's an error, remove the entry
                resolver.delete(uri, null, null)
                throw e
            }
        } ?: false
    }

    /**
     * Save image using legacy storage (Android 9 and below)
     */
    private fun saveWithLegacyStorage(bitmap: Bitmap, filename: String): Boolean {
        val dcimDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val artsorakelDirectory = File(dcimDirectory, CUSTOM_FOLDER_NAME)
        
        // Create the custom folder if it doesn't exist
        if (!artsorakelDirectory.exists()) {
            artsorakelDirectory.mkdirs()
        }
        
        val imageFile = File(artsorakelDirectory, filename)
        
        return try {
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            
            // Notify the MediaScanner to make the image visible in gallery immediately
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                put(MediaStore.Images.Media.TITLE, filename)
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }
            
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            // Image saved silently
            true
        } catch (e: Exception) {
            // Clean up the file if there was an error
            if (imageFile.exists()) {
                imageFile.delete()
            }
            throw e
        }
    }

    /**
     * Save image using MediaStore API preserving EXIF data (Android 10+)
     */
    private fun saveWithMediaStorePreservingExif(sourceUri: Uri, filename: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$CUSTOM_FOLDER_NAME")
            
            // Mark as pending while we write the file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        return imageUri?.let { uri ->
            try {
                // Copy the image data directly without decoding
                resolver.openInputStream(sourceUri)?.use { inputStream ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Clear the pending flag
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                true
            } catch (e: Exception) {
                // If there's an error, remove the entry
                resolver.delete(uri, null, null)
                throw e
            }
        } ?: false
    }

    /**
     * Save image using legacy storage preserving EXIF data (Android 9 and below)
     */
    private fun saveWithLegacyStoragePreservingExif(sourceUri: Uri, filename: String): Boolean {
        val dcimDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val artsorakelDirectory = File(dcimDirectory, CUSTOM_FOLDER_NAME)
        
        // Create the custom folder if it doesn't exist
        if (!artsorakelDirectory.exists()) {
            artsorakelDirectory.mkdirs()
        }
        
        val imageFile = File(artsorakelDirectory, filename)
        
        return try {
            // Copy the image data directly without decoding
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(imageFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Notify the MediaScanner to make the image visible in gallery immediately
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                put(MediaStore.Images.Media.TITLE, filename)
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }
            
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            true
        } catch (e: Exception) {
            // Clean up the file if there was an error
            if (imageFile.exists()) {
                imageFile.delete()
            }
            throw e
        }
    }

    /**
     * Generate a unique filename for the image
     */
    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "ARTSORAKEL_${timeStamp}.jpg"
    }
}