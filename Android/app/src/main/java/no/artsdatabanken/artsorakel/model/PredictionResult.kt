package no.artsdatabanken.artsorakel.model

data class ModelInfo(
    val model: String,
    val country: String,
    val locationSource: String
)

enum class WarningCategory {
    DANGER,
    WARNING,
    INFO;

    companion object {
        fun fromString(value: String): WarningCategory {
            return when (value.lowercase()) {
                "danger" -> DANGER
                "warning" -> WARNING
                "info" -> INFO
                else -> INFO
            }
        }
    }
}

data class WarningItem(
    val category: WarningCategory,
    val title: Map<String, String>?,
    val message: Map<String, String>,
    val link: Map<String, String>?,
    val predictionIndex: Int? = null // Only set for prediction-specific warnings
)

data class Warnings(
    val general: List<WarningItem>,
    val predictions: Map<Int, List<WarningItem>>
)

/**
 * Represents a single species prediction result
 */
data class PredictionResult(
    val id: String, // Use scientificNameID as a unique ID
    val vernacularNames: Map<String, String>?, // Language code -> vernacular name
    val scientificName: String?,
    val groupNames: Map<String, String>?, // Language code -> group name
    val probability: Double, // The raw score (0.0 to 1.0)
    val pictureUrl: String?, // URL for the thumbnail, can be null
    val infoUrl: String?, // URL for details page
    val modelInfo: ModelInfo? = null, // Model information from API
    val redListCategory: String? = null, // Red list category code (e.g., "CR", "EN", "VU", "NT", "DD", "LC")
    val invasiveCategory: String? = null // Invasive species category code (e.g., "NK", "LO", "PH", "HI", "SE")
) {
    /**
     * Gets the vernacular name for a specific language
     * Returns null if not available in the requested language
     */
    fun getVernacularNameForLanguage(languageCode: String): String? {
        // Only return vernacular name from the map for the requested language
        return vernacularNames?.get(languageCode)
    }

    /**
     * Gets any available Norwegian name (nb or nn)
     */
    fun getNorwegianName(): String? {
        return vernacularNames?.get("nb") ?: vernacularNames?.get("nn")
    }

    /**
     * Checks if this species has a vernacular name in the given language
     */
    fun hasVernacularNameInLanguage(languageCode: String): Boolean {
        return vernacularNames?.containsKey(languageCode) == true
    }

    /**
     * Gets the group name for a specific language
     * Returns null if not available in the requested language
     */
    fun getGroupNameForLanguage(languageCode: String): String? {
        return groupNames?.get(languageCode)
    }

    /**
     * Gets any available Norwegian group name (nb or nn)
     */
    fun getNorwegianGroupName(): String? {
        return groupNames?.get("nb") ?: groupNames?.get("nn")
    }
}