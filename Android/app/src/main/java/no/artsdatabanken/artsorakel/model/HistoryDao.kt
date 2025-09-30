package no.artsdatabanken.artsorakel.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for identification history operations
 */
@Dao
interface HistoryDao {
    
    @Query("SELECT * FROM identification_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<IdentificationHistory>>
    
    @Query("SELECT * FROM identification_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): IdentificationHistory?

    @Insert
    suspend fun insertHistory(history: IdentificationHistory): Long

    @Delete
    suspend fun deleteHistory(history: IdentificationHistory): Int

    @Query("DELETE FROM identification_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long): Int

    @Query("DELETE FROM identification_history")
    suspend fun deleteAllHistory(): Int

    @Query("SELECT COUNT(*) FROM identification_history")
    suspend fun getHistoryCount(): Int

    @Query("SELECT * FROM identification_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<IdentificationHistory>
}