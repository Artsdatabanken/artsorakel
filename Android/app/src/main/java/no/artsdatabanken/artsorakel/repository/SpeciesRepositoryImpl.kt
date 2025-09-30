package no.artsdatabanken.artsorakel.repository

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import no.artsdatabanken.artsorakel.core.AppConfig
import no.artsdatabanken.artsorakel.core.errors.ErrorContext
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.ModelInfo
import no.artsdatabanken.artsorakel.model.GeoLocation
import no.artsdatabanken.artsorakel.network.ApiResponse
import no.artsdatabanken.artsorakel.network.ApiService
import no.artsdatabanken.artsorakel.network.TaxonItemDto
import kotlin.coroutines.coroutineContext
import javax.inject.Inject

/**
 * Concrete implementation of SpeciesRepository.
 * Handles API communication and data mapping for species identification.
 */
class SpeciesRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : SpeciesRepository {
    
    // Track current operation for cancellation support
    private var currentJob: Job? = null
    
    override suspend fun identifySpecies(
        imageDataList: List<ByteArray>,
        imageFilenames: List<String>,
        location: GeoLocation?
    ): Result<List<PredictionResult>> {
        
        return try {
            // Ensure we're not cancelled before starting
            coroutineContext.ensureActive()
            
            // Prepare image parts for multipart request
            val imageParts = imageDataList.mapIndexed { index, imageData ->
                val requestBody = imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val filename = imageFilenames.getOrElse(index) { "image_$index.jpg" }
                MultipartBody.Part.createFormData("image", filename, requestBody)
            }
            
            // Prepare application type part
            val applicationValue = AppConfig.Api.APPLICATION_TYPE
            val applicationRequestBody: RequestBody = applicationValue.toRequestBody("text/plain".toMediaTypeOrNull())

            // Prepare location parts if available (rounded to 2 decimal places)
            val latitudePart = location?.let {
                val lat = String.format("%.1f", it.latitude)
                android.util.Log.d("SpeciesRepository", "Sending latitude to server: $lat")
                lat.toRequestBody("text/plain".toMediaTypeOrNull())
            }
            val longitudePart = location?.let {
                val lon = String.format("%.1f", it.longitude)
                android.util.Log.d("SpeciesRepository", "Sending longitude to server: $lon")
                lon.toRequestBody("text/plain".toMediaTypeOrNull())
            }

            if (location == null) {
                android.util.Log.d("SpeciesRepository", "No location data available, server will use IP-based geolocation")
            }

            // Check for cancellation before API call
            coroutineContext.ensureActive()

            // Make the API call
            val apiResponse: ApiResponse = apiService.classifyImages(
                image = imageParts,
                application = applicationRequestBody,
                latitude = latitudePart,
                longitude = longitudePart
            )
            
            // Check for cancellation before processing response
            coroutineContext.ensureActive()
            
            // Parse and map response to domain models
            val predictionResults = mapApiResponseToPredictionResults(apiResponse)
            
            if (predictionResults.isNotEmpty()) {
                Result.success(predictionResults)
            } else {
                val error = ErrorMapper.createNoResultsFoundError()
                error.logError("SpeciesRepository")
                Result.failure(Exception(error.message))
            }
            
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e, ErrorContext.NETWORK)
            error.logError("SpeciesRepository")
            Result.failure(e)
        }
    }
    
    override suspend fun cancelIdentification() {
        currentJob?.cancel()
        currentJob = null
    }
    
    override fun isIdentificationInProgress(): Boolean {
        return currentJob?.isActive == true
    }
    
    /**
     * Maps API response to domain model objects.
     * Handles the conversion from network DTOs to UI-friendly models.
     */
    private fun mapApiResponseToPredictionResults(apiResponse: ApiResponse?): List<PredictionResult> {
        // Defensive null checks
        if (apiResponse == null) {
            return emptyList()
        }

        // Access items via the DTO structure with defensive null checks
        val predictions = apiResponse.predictions
        if (predictions.isNullOrEmpty()) {
            return emptyList()
        }

        val firstPrediction = predictions.firstOrNull()
        if (firstPrediction == null) {
            return emptyList()
        }

        val taxa = firstPrediction.taxa
        if (taxa == null) {
            return emptyList()
        }

        val predictionItems = taxa.items
        if (predictionItems.isNullOrEmpty()) {
            return emptyList()
        }

        // Extract model info if available
        val modelInfo = apiResponse.modelInfo?.let { info ->
            ModelInfo(
                model = info.model ?: "",
                country = info.country ?: "",
                locationSource = info.locationSource ?: ""
            )
        }

        // Map TaxonItemDto (from network) to PredictionResult (for UI)
        return predictionItems
            .mapNotNull { dto ->
                try {
                    // Defensive validation for each field
                    val scientificNameId = dto.scientificNameId
                    val probability = dto.probability

                    // Return null if essential data is missing or invalid
                    // Note: dto is guaranteed non-null by mapNotNull
                    if (scientificNameId.isNullOrBlank() ||
                        probability == null ||
                        probability <= 0.0) {
                        null
                    } else {
                        PredictionResult(
                            id = scientificNameId,
                            vernacularNames = dto.vernacularNames?.filterValues { it.isNotBlank() },
                            scientificName = dto.scientificName?.takeIf { it.isNotBlank() },
                            groupNames = dto.groupNames?.filterValues { it.isNotBlank() },
                            probability = probability,
                            pictureUrl = dto.picture?.takeIf { it.isNotBlank() },
                            infoUrl = dto.infoUrl?.takeIf { it.isNotBlank() },
                            modelInfo = modelInfo,
                            redListCategory = dto.redListCategory?.takeIf { it.isNotBlank() },
                            invasiveCategory = dto.invasiveCategory?.takeIf { it.isNotBlank() }
                        )
                    }
                } catch (e: Exception) {
                    // Log the error but don't fail the entire mapping
                    val error = ErrorMapper.mapException(e)
                    error.logError("SpeciesRepositoryImpl")
                    null
                }
            }
            .sortedByDescending { it.probability }
            .take(5) // Limit to top 5 results
    }
} 