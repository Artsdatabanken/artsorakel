package no.artsdatabanken.artsorakel.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import no.artsdatabanken.artsorakel.repository.SpeciesRepository
import no.artsdatabanken.artsorakel.repository.SpeciesRepositoryImpl
import no.artsdatabanken.artsorakel.repository.HistoryRepository
import no.artsdatabanken.artsorakel.repository.HistoryRepositoryImpl
import javax.inject.Singleton

/**
 * Hilt module for providing repository dependencies.
 * Binds repository interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    /**
     * Provides the SpeciesRepository implementation as a singleton.
     * Uses SpeciesRepositoryImpl as the concrete implementation.
     */
    @Binds
    @Singleton
    abstract fun bindSpeciesRepository(
        speciesRepositoryImpl: SpeciesRepositoryImpl
    ): SpeciesRepository
    
    /**
     * Provides the HistoryRepository implementation as a singleton.
     * Uses HistoryRepositoryImpl as the concrete implementation.
     */
    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        historyRepositoryImpl: HistoryRepositoryImpl
    ): HistoryRepository
} 