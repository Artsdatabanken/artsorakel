package no.artsdatabanken.artsorakel.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import no.artsdatabanken.artsorakel.BuildConfig
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import no.artsdatabanken.artsorakel.model.GeoLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImageOperationsManager(
    private val context: Context,
    private val takePictureLauncher: ActivityResultLauncher<Uri>,
    private val pickImageLauncher: ActivityResultLauncher<Array<String>>
) {

    var tempImageUri: Uri? = null
        private set

    var pendingCameraLocation: GeoLocation? = null
        private set

    private val locationManager = LocationManager(context)

    fun launchCamera() {
        createTempImageFile()?.let { uri ->
            tempImageUri = uri

            CoroutineScope(Dispatchers.IO).launch {
                pendingCameraLocation = locationManager.getCurrentLocation()
            }

            takePictureLauncher.launch(uri)
        } ?: run {
            Toast.makeText(context, "Failed to create temporary image file", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchImagePicker() {
        // Use array of MIME types to allow various image formats
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    fun clearTempUri() {
        tempImageUri = null
        pendingCameraLocation = null
    }

    fun extractLocationFromImage(uri: Uri): GeoLocation? {
        val location = locationManager.extractLocationFromImage(uri)
        if (BuildConfig.DEBUG) android.util.Log.d("ImageOperationsManager", "Extracted location from $uri: $location")
        return location
    }

    fun writeLocationToTempImage(): Boolean {
        val uri = tempImageUri ?: return false
        val location = pendingCameraLocation ?: return false

        return locationManager.writeLocationToImage(uri, location)
    }

    private fun createTempImageFile(): Uri? {
        return try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir: File? = context.getExternalFilesDir(null)
            val imageFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
            FileProvider.getUriForFile(
                context,
                "${context.applicationContext.packageName}.provider",
                imageFile
            )
        } catch (ex: Exception) {
            val error = ErrorMapper.mapFileException(ex, "temporary image file")
            error.logError("ImageOperationsManager")
            Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            null
        }
    }
} 