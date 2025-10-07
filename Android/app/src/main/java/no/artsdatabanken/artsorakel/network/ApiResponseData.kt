package no.artsdatabanken.artsorakel.network
import com.google.gson.annotations.SerializedName

data class ApiResponse(
    val predictions: List<PredictionDto>?,
    val modelInfo: ModelInfoDto?,
    val warnings: WarningsDto?
)

data class WarningsDto(
    val general: List<WarningItemDto>?,
    val predictions: Map<String, List<WarningItemDto>>?
)

data class WarningItemDto(
    val category: String,
    val title: Map<String, String>?,
    val message: Map<String, String>,
    val link: Map<String, String>?
)

data class ModelInfoDto(
    val model: String?,
    val country: String?,
    val locationSource: String?
)

// Represents one object inside the "predictions" list
data class PredictionDto(
    // Use @SerializedName if your Kotlin variable name differs from the JSON key
    @SerializedName("region_group_id")
    val regionGroupId: String?,
    val taxa: TaxaInfoDto?
)

// Represents the "taxa" object inside a Prediction
data class TaxaInfoDto(
    val items: List<TaxonItemDto>?, // List of the actual species predictions
    val type: String?
)

// Represents one item (a specific species prediction) inside the "items" list
// This is the DTO mapping directly to the JSON structure
data class TaxonItemDto(
    val probability: Double?,
    @SerializedName("scientific_name")
    val scientificName: String?,
    @SerializedName("scientific_name_id")
    val scientificNameId: String?,
    val vernacularNames: Map<String, String>?,
    val groupNames: Map<String, String>?,
    val name: String?,
    val infoUrl: String?,
    val picture: String?,
    val redListCategory: String?, // Red list category code (e.g., "CR", "EN", "VU")
    val invasiveCategory: String? // Invasive species category code (e.g., "NK", "LO", "HI")
)
    