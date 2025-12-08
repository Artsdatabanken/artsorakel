package no.artsdatabanken.artsorakel.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import no.artsdatabanken.artsorakel.BuildConfig
import no.artsdatabanken.artsorakel.model.GeoLocation

private fun logD(tag: String, message: String) {
    if (BuildConfig.DEBUG) android.util.Log.d(tag, message)
}

private fun logE(tag: String, message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) android.util.Log.e(tag, message, throwable)
}

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): GeoLocation? {
        if (!hasLocationPermission()) {
            logD("LocationManager", "No location permission granted")
            return null
        }

        return try {
            // Use appropriate priority based on permission level
            val priority = if (hasFineLocationPermission()) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            logD("LocationManager", "Requesting current location with priority: $priority")

            val location = fusedLocationClient.getCurrentLocation(priority, null).await()

            location?.let {
                logD("LocationManager", "Got current location: lat=${it.latitude}, lon=${it.longitude}")
                GeoLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = if (it.hasAltitude()) it.altitude else null
                )
            } ?: run {
                logD("LocationManager", "getCurrentLocation returned null")
                null
            }
        } catch (e: Exception) {
            logE("LocationManager", "Error getting current location", e)
            null
        }
    }

    fun extractLocationFromImage(uri: Uri): GeoLocation? {
        return try {
            logD("LocationManager", "Extracting location from URI: $uri (scheme: ${uri.scheme})")
            val result = when (uri.scheme) {
                "file" -> extractFromFile(uri.path ?: return null)
                "content" -> extractFromContent(uri)
                else -> null
            }
            logD("LocationManager", "Extraction result: $result")
            result
        } catch (e: Exception) {
            logE("LocationManager", "Error extracting location", e)
            null
        }
    }

    private fun extractFromFile(path: String): GeoLocation? {
        return try {
            logD("LocationManager", "Extracting from file path: $path")
            val exif = ExifInterface(path)
            extractGeoLocation(exif)
        } catch (e: Exception) {
            logE("LocationManager", "Error extracting from file: $path", e)
            null
        }
    }

    private fun extractFromContent(uri: Uri): GeoLocation? {
        logD("LocationManager", "extractFromContent URI: $uri, authority: ${uri.authority}")

        // Try regular URI first (works for OpenDocument URIs which preserve EXIF)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val result = extractGeoLocation(exif)
                if (result != null) {
                    logD("LocationManager", "Regular read succeeded with location")
                    return result
                }
            }
        } catch (e: Exception) {
            logE("LocationManager", "Regular read failed", e)
        }

        // If no location found, check if we have full media access and can query MediaStore directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasMediaLoc = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasReadMedia = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED

            logD("LocationManager", "Perms: mediaLoc=$hasMediaLoc, readMedia=$hasReadMedia")

            if (hasMediaLoc && hasReadMedia) {
                // Try to get location directly from MediaStore (bypasses sharing app redaction)
                val mediaStoreLocation = tryGetLocationFromMediaStore(uri)
                if (mediaStoreLocation != null) {
                    logD("LocationManager", "Got location from MediaStore query")
                    return mediaStoreLocation
                }

                // Try setRequireOriginal for MediaStore URIs
                val isMediaStoreUri = uri.authority == "media" ||
                    uri.authority == MediaStore.AUTHORITY ||
                    uri.authority?.contains("media") == true

                if (isMediaStoreUri) {
                    try {
                        val originalUri = MediaStore.setRequireOriginal(uri)
                        logD("LocationManager", "Trying setRequireOriginal")
                        context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                            val exif = ExifInterface(inputStream)
                            val result = extractGeoLocation(exif)
                            if (result != null) {
                                logD("LocationManager", "setRequireOriginal succeeded!")
                                return result
                            }
                        }
                    } catch (e: Exception) {
                        logE("LocationManager", "setRequireOriginal failed", e)
                    }
                }
            }
        }

        // Location data is either not present or was redacted by the sharing app
        logD("LocationManager", "No unredacted location available - sharing app may have stripped it")
        return null
    }

    /**
     * Try to get location from MediaStore by finding the original file and reading its EXIF.
     * Uses setRequireOriginal() to bypass Android's location redaction.
     */
    private fun tryGetLocationFromMediaStore(uri: Uri): GeoLocation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        try {
            // For non-picker URIs, try to extract the media ID and read EXIF directly
            val mediaId = getMediaIdFromUri(uri)
            if (mediaId != null) {
                logD("LocationManager", "Got media ID: $mediaId")
                val mediaUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
                val location = readLocationFromMediaStoreUri(mediaUri)
                if (location != null) return location
            }

            // Fallback: find image in MediaStore by matching file size
            logD("LocationManager", "Trying file size matching fallback")
            return findImageInMediaStoreBySize(uri)
        } catch (e: Exception) {
            logE("LocationManager", "MediaStore query failed", e)
            return null
        }
    }

    /**
     * Find an image in MediaStore by matching various attributes, then get its location.
     * This works when the picker URI doesn't expose the media ID.
     */
    @Suppress("DEPRECATION")
    private fun findImageInMediaStoreBySize(uri: Uri): GeoLocation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        try {
            // First try to get display name from the picker URI
            var displayName: String? = null
            var fileSize: Long? = null

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Fallback to getting size from file descriptor
            if (fileSize == null) {
                fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize
                }
            }

            logD("LocationManager", "Looking for image: name=$displayName, size=$fileSize bytes")

            // Try to find by display name first (more reliable than size for edited images)
            if (displayName != null) {
                val location = findByDisplayName(displayName!!)
                if (location != null) return location
            }

            // Fall back to size matching
            if (fileSize != null) {
                val location = findBySize(fileSize!!)
                if (location != null) return location
            }

        } catch (e: Exception) {
            logE("LocationManager", "Image matching failed", e)
        }

        return null
    }

    private fun findByDisplayName(displayName: String): GeoLocation? {
        logD("LocationManager", "Searching MediaStore by display name: $displayName")

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(displayName)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            logD("LocationManager", "Found ${cursor.count} images with matching name")

            while (cursor.moveToNext()) {
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                if (idIndex >= 0) {
                    val mediaId = cursor.getLong(idIndex)
                    logD("LocationManager", "Trying to read EXIF from MediaStore ID: $mediaId")

                    val mediaUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    val location = readLocationFromMediaStoreUri(mediaUri)
                    if (location != null) {
                        logD("LocationManager", "Found location via display name match!")
                        return location
                    }
                }
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun findBySize(fileSize: Long): GeoLocation? {
        logD("LocationManager", "Searching MediaStore by size: $fileSize bytes")

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.SIZE} = ?"
        val selectionArgs = arrayOf(fileSize.toString())

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            logD("LocationManager", "Found ${cursor.count} images with matching size")

            while (cursor.moveToNext()) {
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                if (idIndex >= 0) {
                    val mediaId = cursor.getLong(idIndex)
                    logD("LocationManager", "Trying to read EXIF from MediaStore ID: $mediaId")

                    // Construct the MediaStore URI and try to read EXIF with setRequireOriginal
                    val mediaUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    val location = readLocationFromMediaStoreUri(mediaUri)
                    if (location != null) {
                        logD("LocationManager", "Found location via size match + EXIF read!")
                        return location
                    }
                }
            }
        }
        return null
    }

    /**
     * Read location from a MediaStore URI using setRequireOriginal to bypass redaction.
     */
    private fun readLocationFromMediaStoreUri(mediaUri: Uri): GeoLocation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        try {
            val originalUri = MediaStore.setRequireOriginal(mediaUri)
            logD("LocationManager", "Reading EXIF from MediaStore URI with setRequireOriginal")

            context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val result = extractGeoLocation(exif)
                if (result != null) {
                    logD("LocationManager", "Successfully read location from MediaStore file!")
                    return result
                }
            }
        } catch (e: Exception) {
            logE("LocationManager", "Failed to read from MediaStore URI", e)
        }

        return null
    }

    private fun getMediaIdFromUri(uri: Uri): Long? {
        // Try to extract media ID from various URI formats
        try {
            val uriString = uri.toString()

            // Skip picker URIs - their IDs are NOT MediaStore IDs
            if (uriString.contains("/picker/") || uriString.contains("photopicker")) {
                logD("LocationManager", "Skipping picker URI for media ID extraction")
                return null
            }

            // For content://media/external/images/media/123 format
            val lastSegment = uri.lastPathSegment
            if (lastSegment != null) {
                val id = lastSegment.toLongOrNull()
                if (id != null) return id
            }

            // For document URIs like content://com.android.providers.media.documents/document/image%3A123
            val documentId = uri.lastPathSegment
            if (documentId != null && documentId.startsWith("image:")) {
                return documentId.removePrefix("image:").toLongOrNull()
            }

            // Query the URI for _ID column
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idIndex >= 0) {
                        return cursor.getLong(idIndex)
                    }
                }
            }
        } catch (_: Exception) {
            logD("LocationManager", "Could not extract media ID from URI")
        }
        return null
    }

    private fun extractGeoLocation(exif: ExifInterface): GeoLocation? {
        // Log all GPS-related EXIF tags for debugging
        val latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        val longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)

        logD("LocationManager", "EXIF GPS Tags: lat=$latitude, latRef=$latitudeRef, lon=$longitude, lonRef=$longitudeRef")

        // First try manual parsing, as it's more reliable
        if (latitude != null && longitude != null) {
            logD("LocationManager", "GPS tags found, attempting manual parsing...")
            logD("LocationManager", "Latitude isEmpty: ${latitude.isEmpty()}, Longitude isEmpty: ${longitude.isEmpty()}")

            // Check if the values are not empty strings
            if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
                try {
                    val lat = convertDMSToDecimal(latitude, latitudeRef ?: "N")
                    val lon = convertDMSToDecimal(longitude, longitudeRef ?: "E")
                    logD("LocationManager", "Manual parsing successful: lat=$lat, lon=$lon")

                    // Treat 0,0 as "no location" (redacted data)
                    if (lat == 0.0 && lon == 0.0) {
                        logD("LocationManager", "Coordinates are 0,0 - treating as no location")
                        return null
                    }

                    val altitude = try {
                        exif.getAltitude(Double.NaN)
                    } catch (e: Exception) {
                        logE("LocationManager", "Error getting altitude", e)
                        Double.NaN
                    }

                    return GeoLocation(
                        latitude = lat,
                        longitude = lon,
                        altitude = if (altitude.isNaN()) null else altitude
                    )
                } catch (e: Exception) {
                    logE("LocationManager", "Manual GPS parsing failed", e)
                }
            } else {
                logD("LocationManager", "GPS tags are empty strings")
            }
        } else {
            logD("LocationManager", "No GPS tags found (null values)")
        }

        // Fall back to the built-in method
        try {
            val latLong = exif.latLong
            if (latLong != null) {
                logD("LocationManager", "Using built-in latLong: lat=${latLong[0]}, lon=${latLong[1]}")
                val altitude = exif.getAltitude(Double.NaN)
                return GeoLocation(
                    latitude = latLong[0],
                    longitude = latLong[1],
                    altitude = if (altitude.isNaN()) null else altitude
                )
            }
        } catch (e: Exception) {
            logE("LocationManager", "Built-in latLong failed", e)
        }

        logD("LocationManager", "No GPS location could be extracted")
        return null
    }

    private fun convertDMSToDecimal(dms: String, ref: String): Double {
        logD("LocationManager", "Converting DMS: '$dms' with ref: '$ref'")
        logD("LocationManager", "DMS string length: ${dms.length}")

        // Android ExifInterface may return DMS in different formats
        // Format 1: "degrees/denominator,minutes/denominator,seconds/denominator"
        // Format 2: Space-separated rationals

        val separator = when {
            dms.contains(",") -> ","
            dms.contains(" ") -> " "
            else -> ","
        }

        logD("LocationManager", "Using separator: '$separator'")

        val parts = dms.split(separator).map { it.trim() }
        logD("LocationManager", "Split into ${parts.size} parts: $parts")

        if (parts.size != 3) {
            logE("LocationManager", "Invalid DMS format: expected 3 parts, got ${parts.size}")
            throw IllegalArgumentException("Invalid DMS format: $dms")
        }

        // Parse each part as a rational number (numerator/denominator)
        fun parseRational(rational: String, name: String): Double {
            logD("LocationManager", "Parsing $name: '$rational'")

            return try {
                val components = rational.split("/")
                val result = if (components.size == 2) {
                    val num = components[0].trim().toDouble()
                    val den = components[1].trim().toDouble()
                    if (den == 0.0) {
                        logE("LocationManager", "$name has zero denominator!")
                        0.0
                    } else {
                        num / den
                    }
                } else {
                    // Might be a direct decimal value
                    rational.toDouble()
                }
                logD("LocationManager", "$name parsed as: $result")
                result
            } catch (e: Exception) {
                logE("LocationManager", "Failed to parse $name: '$rational'", e)
                0.0
            }
        }

        val degrees = parseRational(parts[0], "degrees")
        val minutes = parseRational(parts[1], "minutes")
        val seconds = parseRational(parts[2], "seconds")

        logD("LocationManager", "Parsed components - D: $degrees, M: $minutes, S: $seconds")

        val decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
        val result = if (ref == "S" || ref == "W") -decimal else decimal

        logD("LocationManager", "Final decimal result: $result")
        return result
    }

    fun writeLocationToImage(imagePath: String, location: GeoLocation): Boolean {
        return try {
            val exif = ExifInterface(imagePath)

            // Use the newer setLatLong method if available
            exif.setLatLong(location.latitude, location.longitude)

            location.altitude?.let {
                exif.setAltitude(it)
            }

            exif.saveAttributes()
            logD("LocationManager", "Wrote location to image: lat=${location.latitude}, lon=${location.longitude}")
            true
        } catch (e: Exception) {
            logE("LocationManager", "Failed to write location to image", e)
            false
        }
    }

    fun writeLocationToImage(uri: Uri, location: GeoLocation): Boolean {
        return when (uri.scheme) {
            "file" -> uri.path?.let { writeLocationToImage(it, location) } ?: false
            else -> false
        }
    }


    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }
}