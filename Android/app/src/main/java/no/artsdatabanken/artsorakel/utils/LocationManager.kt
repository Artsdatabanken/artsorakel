package no.artsdatabanken.artsorakel.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.io.InputStream
import no.artsdatabanken.artsorakel.model.GeoLocation

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): GeoLocation? {
        if (!hasLocationPermission()) {
            android.util.Log.d("LocationManager", "No location permission granted")
            return null
        }

        return try {
            // Use appropriate priority based on permission level
            val priority = if (hasFineLocationPermission()) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            android.util.Log.d("LocationManager", "Requesting current location with priority: $priority")

            val location = fusedLocationClient.getCurrentLocation(priority, null).await()

            location?.let {
                android.util.Log.d("LocationManager", "Got current location: lat=${it.latitude}, lon=${it.longitude}")
                GeoLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = if (it.hasAltitude()) it.altitude else null
                )
            } ?: run {
                android.util.Log.d("LocationManager", "getCurrentLocation returned null")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationManager", "Error getting current location", e)
            null
        }
    }

    fun extractLocationFromImage(uri: Uri): GeoLocation? {
        return try {
            android.util.Log.d("LocationManager", "Extracting location from URI: $uri (scheme: ${uri.scheme})")
            val result = when (uri.scheme) {
                "file" -> extractFromFile(uri.path ?: return null)
                "content" -> extractFromContent(uri)
                else -> null
            }
            android.util.Log.d("LocationManager", "Extraction result: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("LocationManager", "Error extracting location", e)
            null
        }
    }

    private fun extractFromFile(path: String): GeoLocation? {
        return try {
            android.util.Log.d("LocationManager", "Extracting from file path: $path")
            val exif = ExifInterface(path)
            extractGeoLocation(exif)
        } catch (e: Exception) {
            android.util.Log.e("LocationManager", "Error extracting from file: $path", e)
            null
        }
    }

    private fun extractFromContent(uri: Uri): GeoLocation? {
        return try {
            android.util.Log.d("LocationManager", "Extracting from content URI: $uri")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                android.util.Log.d("LocationManager", "Successfully opened input stream for URI")
                val exif = ExifInterface(inputStream)
                extractGeoLocation(exif)
            } ?: run {
                android.util.Log.e("LocationManager", "Failed to open input stream for URI: $uri")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationManager", "Error extracting from content: $uri", e)
            null
        }
    }

    private fun extractGeoLocation(exif: ExifInterface): GeoLocation? {
        // Log all GPS-related EXIF tags for debugging
        val latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        val longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)

        android.util.Log.d("LocationManager", "EXIF GPS Tags:")
        android.util.Log.d("LocationManager", "  TAG_GPS_LATITUDE: $latitude")
        android.util.Log.d("LocationManager", "  TAG_GPS_LATITUDE_REF: $latitudeRef")
        android.util.Log.d("LocationManager", "  TAG_GPS_LONGITUDE: $longitude")
        android.util.Log.d("LocationManager", "  TAG_GPS_LONGITUDE_REF: $longitudeRef")

        // First try manual parsing, as it's more reliable
        if (latitude != null && longitude != null) {
            android.util.Log.d("LocationManager", "GPS tags found, attempting manual parsing...")
            android.util.Log.d("LocationManager", "Latitude isEmpty: ${latitude.isEmpty()}, Longitude isEmpty: ${longitude.isEmpty()}")

            // Check if the values are not empty strings
            if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
                try {
                    val lat = convertDMSToDecimal(latitude, latitudeRef ?: "N")
                    val lon = convertDMSToDecimal(longitude, longitudeRef ?: "E")
                    android.util.Log.d("LocationManager", "Manual parsing successful: lat=$lat, lon=$lon")

                val altitude = try {
                    exif.getAltitude(Double.NaN)
                } catch (e: Exception) {
                    android.util.Log.e("LocationManager", "Error getting altitude", e)
                    Double.NaN
                }

                    return GeoLocation(
                        latitude = lat,
                        longitude = lon,
                        altitude = if (altitude.isNaN()) null else altitude
                    )
                } catch (e: Exception) {
                    android.util.Log.e("LocationManager", "Manual GPS parsing failed", e)
                }
            } else {
                android.util.Log.d("LocationManager", "GPS tags are empty strings")
            }
        } else {
            android.util.Log.d("LocationManager", "No GPS tags found (null values)")
        }

        // Fall back to the built-in method
        try {
            val latLong = exif.latLong
            if (latLong != null) {
                android.util.Log.d("LocationManager", "Using built-in latLong: lat=${latLong[0]}, lon=${latLong[1]}")
                val altitude = exif.getAltitude(Double.NaN)
                return GeoLocation(
                    latitude = latLong[0],
                    longitude = latLong[1],
                    altitude = if (altitude.isNaN()) null else altitude
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationManager", "Built-in latLong failed", e)
        }

        android.util.Log.d("LocationManager", "No GPS location could be extracted")
        return null
    }

    private fun convertDMSToDecimal(dms: String, ref: String): Double {
        android.util.Log.d("LocationManager", "Converting DMS: '$dms' with ref: '$ref'")
        android.util.Log.d("LocationManager", "DMS string length: ${dms.length}")

        // Android ExifInterface may return DMS in different formats
        // Format 1: "degrees/denominator,minutes/denominator,seconds/denominator"
        // Format 2: Space-separated rationals

        val separator = when {
            dms.contains(",") -> ","
            dms.contains(" ") -> " "
            else -> ","
        }

        android.util.Log.d("LocationManager", "Using separator: '$separator'")

        val parts = dms.split(separator).map { it.trim() }
        android.util.Log.d("LocationManager", "Split into ${parts.size} parts: $parts")

        if (parts.size != 3) {
            android.util.Log.e("LocationManager", "Invalid DMS format: expected 3 parts, got ${parts.size}")
            throw IllegalArgumentException("Invalid DMS format: $dms")
        }

        // Parse each part as a rational number (numerator/denominator)
        fun parseRational(rational: String, name: String): Double {
            android.util.Log.d("LocationManager", "Parsing $name: '$rational'")

            return try {
                val components = rational.split("/")
                val result = if (components.size == 2) {
                    val num = components[0].trim().toDouble()
                    val den = components[1].trim().toDouble()
                    if (den == 0.0) {
                        android.util.Log.e("LocationManager", "$name has zero denominator!")
                        0.0
                    } else {
                        num / den
                    }
                } else {
                    // Might be a direct decimal value
                    rational.toDouble()
                }
                android.util.Log.d("LocationManager", "$name parsed as: $result")
                result
            } catch (e: Exception) {
                android.util.Log.e("LocationManager", "Failed to parse $name: '$rational'", e)
                0.0
            }
        }

        val degrees = parseRational(parts[0], "degrees")
        val minutes = parseRational(parts[1], "minutes")
        val seconds = parseRational(parts[2], "seconds")

        android.util.Log.d("LocationManager", "Parsed components - D: $degrees, M: $minutes, S: $seconds")

        val decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
        val result = if (ref == "S" || ref == "W") -decimal else decimal

        android.util.Log.d("LocationManager", "Final decimal result: $result")
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
            android.util.Log.d("LocationManager", "Wrote location to image: lat=${location.latitude}, lon=${location.longitude}")
            true
        } catch (e: Exception) {
            android.util.Log.e("LocationManager", "Failed to write location to image", e)
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