package no.artsdatabanken.artsorakel.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

/**
 * Data class to store both cropped and original image URIs.
 * Represents the relationship between a user-selected image and its processed version.
 */
data class ImagePair(
    val croppedUri: Uri,
    val originalUri: Uri,
    val location: GeoLocation? = null
)

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
)

/**
 * Entity representing a saved identification history entry
 */
@Entity(tableName = "identification_history")
@TypeConverters(DateConverter::class, UriListConverter::class)
data class IdentificationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Date,
    val bestMatchVernacularNames: String?,
    val bestMatchScientificName: String?,
    val bestMatchProbability: Double,
    val bestMatchGroupNames: String? = null,
    val bestMatchInfoUrl: String?,
    val allResults: String, // JSON string of all PredictionResults (includes all vernacularNames)
    val thumbnailPaths: List<String>, // Local paths to saved thumbnails (200x200)
    val originalImagePaths: List<String>, // Original image locations (content URIs, may expire)
    val fullSizeImagePaths: List<String> = emptyList(), // Local paths to saved full-size images (1024x1024) for reporting
    val warnings: String? = null // JSON string of Warnings object
) {
    /**
     * Gets the vernacular name for a specific language from stored JSON
     * Returns null if not available in the requested language
     */
    fun getVernacularNameForLanguage(languageCode: String): String? {
        // Only use the JSON map if available
        bestMatchVernacularNames?.let { json ->
            try {
                val namesMap = com.google.gson.Gson().fromJson(
                    json,
                    object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                ) as? Map<String, String>

                return namesMap?.get(languageCode)
            } catch (e: Exception) {
                // Return null on error
            }
        }
        return null
    }

    /**
     * Gets any available Norwegian name (nb or nn)
     */
    fun getNorwegianName(): String? {
        bestMatchVernacularNames?.let { json ->
            try {
                val namesMap = com.google.gson.Gson().fromJson(
                    json,
                    object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                ) as? Map<String, String>

                return namesMap?.get("nb") ?: namesMap?.get("nn")
            } catch (e: Exception) {
                // Return null on error
            }
        }
        return null
    }

    /**
     * Gets the group name for a specific language from stored JSON
     * Returns null if not available in the requested language
     */
    fun getGroupNameForLanguage(languageCode: String): String? {
        bestMatchGroupNames?.let { json ->
            try {
                val namesMap = com.google.gson.Gson().fromJson(
                    json,
                    object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                ) as? Map<String, String>

                return namesMap?.get(languageCode)
            } catch (e: Exception) {
                // Return null on error
            }
        }
        return null
    }
}

/**
 * TypeConverters for Room database
 */
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}

class UriListConverter {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun fromString(value: String): List<String> = 
        if (value.isEmpty()) emptyList() else value.split(",")
} 