package no.artsdatabanken.artsorakel.repository

import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.GeoLocation

/**
 * Repository interface for species identification operations.
 * Abstracts the data layer from the presentation layer, providing a clean API
 * for species identification while hiding implementation details.
 */
interface SpeciesRepository {
    
    /**
     * Identifies species from a list of processed image data.
     * 
     * @param imageDataList List of image byte arrays ready for API submission
     * @param imageFilenames List of filenames corresponding to each image
     * @return Result containing either a list of prediction results or an error
     */
    suspend fun identifySpecies(
        imageDataList: List<ByteArray>,
        imageFilenames: List<String>,
        location: GeoLocation? = null
    ): Result<List<PredictionResult>>
    
    /**
     * Cancels any ongoing species identification operation.
     * This is useful for cleanup when the user cancels the operation.
     */
    suspend fun cancelIdentification()
    
    /**
     * Checks if an identification operation is currently in progress.
     * 
     * @return true if an operation is ongoing, false otherwise
     */
    fun isIdentificationInProgress(): Boolean
} 