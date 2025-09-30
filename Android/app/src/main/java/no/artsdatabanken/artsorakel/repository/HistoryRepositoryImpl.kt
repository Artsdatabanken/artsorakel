package no.artsdatabanken.artsorakel.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.artsdatabanken.artsorakel.model.HistoryDao
import no.artsdatabanken.artsorakel.model.IdentificationHistory
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.service.ThumbnailService
import com.google.gson.Gson
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Implementation of HistoryRepository using Room database
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val gson: Gson,
    private val thumbnailService: ThumbnailService
) : HistoryRepository {
    
    override fun getAllHistory(): Flow<List<IdentificationHistory>> {
        return historyDao.getAllHistory()
    }
    
    override suspend fun getHistoryById(id: Long): IdentificationHistory? {
        return withContext(Dispatchers.IO) {
            historyDao.getHistoryById(id)
        }
    }
    
    override suspend fun saveIdentificationToHistory(
        predictionResults: List<PredictionResult>,
        imagePaths: List<String>,
        thumbnailPaths: List<String>
    ): Long {
        return withContext(Dispatchers.IO) {
            // Get the best match (first result with highest probability)
            val bestMatch = predictionResults.firstOrNull()

            // Convert prediction results to JSON string for storage
            val allResultsJson = gson.toJson(predictionResults)

            val historyEntry = IdentificationHistory(
                timestamp = Date(),
                bestMatchVernacularNames = bestMatch?.vernacularNames?.let { gson.toJson(it) },
                bestMatchScientificName = bestMatch?.scientificName,
                bestMatchProbability = bestMatch?.probability ?: 0.0,
                bestMatchGroupNames = bestMatch?.groupNames?.let { gson.toJson(it) },
                bestMatchInfoUrl = bestMatch?.infoUrl,
                allResults = allResultsJson, // Contains full PredictionResults with all vernacularNames
                thumbnailPaths = thumbnailPaths,
                originalImagePaths = imagePaths
            )

            historyDao.insertHistory(historyEntry)
        }
    }
    
    override suspend fun deleteHistory(history: IdentificationHistory) {
        withContext(Dispatchers.IO) {
            historyDao.deleteHistory(history)
        }
    }
    
    override suspend fun deleteHistoryById(id: Long) {
        withContext(Dispatchers.IO) {
            historyDao.deleteHistoryById(id)
        }
    }
    
    override suspend fun deleteAllHistory() {
        withContext(Dispatchers.IO) {
            // Delete all history entries from database
            historyDao.deleteAllHistory()
            // Clear all thumbnail files
            thumbnailService.clearAllThumbnails(context)
        }
    }
    
    override suspend fun getHistoryCount(): Int {
        return withContext(Dispatchers.IO) {
            historyDao.getHistoryCount()
        }
    }
    
    override suspend fun getRecentHistory(limit: Int): List<IdentificationHistory> {
        return withContext(Dispatchers.IO) {
            historyDao.getRecentHistory(limit)
        }
    }
    
    /**
     * Helper function to parse JSON back to PredictionResult list
     */
    override fun parseResultsFromJson(json: String): List<PredictionResult> {
        return try {
            gson.fromJson(json, Array<PredictionResult>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}