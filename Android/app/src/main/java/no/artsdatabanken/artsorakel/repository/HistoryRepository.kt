package no.artsdatabanken.artsorakel.repository

import kotlinx.coroutines.flow.Flow
import no.artsdatabanken.artsorakel.model.IdentificationHistory
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.Warnings

/**
 * Repository interface for identification history operations.
 * Abstracts the data layer from the presentation layer, providing a clean API
 * for history management while hiding implementation details.
 */
interface HistoryRepository {
    
    /**
     * Gets all identification history entries as a Flow for reactive UI updates
     * @return Flow of history entries ordered by timestamp (newest first)
     */
    fun getAllHistory(): Flow<List<IdentificationHistory>>
    
    /**
     * Gets a specific history entry by ID
     * @param id The unique identifier of the history entry
     * @return The history entry or null if not found
     */
    suspend fun getHistoryById(id: Long): IdentificationHistory?
    
    /**
     * Saves a new identification result to history
     * @param predictionResults List of all prediction results from identification
     * @param warnings Warnings from the identification (optional)
     * @param imagePaths List of image file paths used for identification
     * @param thumbnailPaths List of saved thumbnail paths (200x200)
     * @param fullSizeImagePaths List of saved full-size image paths (1024x1024) for reporting
     * @return The ID of the saved history entry
     */
    suspend fun saveIdentificationToHistory(
        predictionResults: List<PredictionResult>,
        warnings: Warnings? = null,
        imagePaths: List<String>,
        thumbnailPaths: List<String>,
        fullSizeImagePaths: List<String> = emptyList()
    ): Long
    
    /**
     * Deletes a specific history entry
     * @param history The history entry to delete
     */
    suspend fun deleteHistory(history: IdentificationHistory)
    
    /**
     * Deletes a history entry by ID
     * @param id The ID of the history entry to delete
     */
    suspend fun deleteHistoryById(id: Long)
    
    /**
     * Deletes all history entries and their associated thumbnails
     */
    suspend fun deleteAllHistory()
    
    /**
     * Gets the total count of history entries
     * @return The number of saved history entries
     */
    suspend fun getHistoryCount(): Int
    
    /**
     * Gets the most recent history entries
     * @param limit Maximum number of entries to return
     * @return List of recent history entries
     */
    suspend fun getRecentHistory(limit: Int): List<IdentificationHistory>
    
    /**
     * Parses JSON string back to PredictionResult list
     * @param json The JSON string containing serialized prediction results
     * @return List of PredictionResult objects, or empty list if parsing fails
     */
    fun parseResultsFromJson(json: String): List<PredictionResult>
}