package no.artsdatabanken.artsorakel.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.artsdatabanken.artsorakel.model.AppDatabase
import no.artsdatabanken.artsorakel.model.HistoryDao
import javax.inject.Singleton

/**
 * Hilt module for providing database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides the Room database instance as a singleton.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    /**
     * Provides the HistoryDao from the database.
     */
    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }
    
    /**
     * Provides Gson instance for JSON serialization/deserialization.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}