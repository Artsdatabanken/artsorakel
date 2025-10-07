package no.artsdatabanken.artsorakel.model

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * Room database for the app
 */
@Database(
    entities = [IdentificationHistory::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(DateConverter::class, UriListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "artsorakel_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}