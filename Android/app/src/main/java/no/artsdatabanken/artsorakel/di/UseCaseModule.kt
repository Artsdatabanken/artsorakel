package no.artsdatabanken.artsorakel.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import no.artsdatabanken.artsorakel.repository.SpeciesRepository
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import no.artsdatabanken.artsorakel.usecase.ClassifySpeciesUseCase
import no.artsdatabanken.artsorakel.usecase.ManageImagesUseCase
import no.artsdatabanken.artsorakel.utils.SettingsManager
import javax.inject.Singleton

/**
 * Hilt module for providing use case dependencies.
 * Provides use case instances that coordinate business logic.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    /**
     * Provides the ClassifySpeciesUseCase as a singleton.
     * This use case coordinates image processing and species identification.
     */
    @Provides
    @Singleton
    fun provideClassifySpeciesUseCase(
        repository: SpeciesRepository,
        imageProcessingService: ImageProcessingService,
        settingsManager: SettingsManager
    ): ClassifySpeciesUseCase {
        return ClassifySpeciesUseCase(
            repository = repository,
            imageProcessingService = imageProcessingService,
            settingsManager = settingsManager
        )
    }
    
    /**
     * Provides the ManageImagesUseCase as a singleton.
     * This use case handles image management operations including file cleanup.
     */
    @Provides
    @Singleton
    fun provideManageImagesUseCase(
        imageProcessingService: ImageProcessingService
    ): ManageImagesUseCase {
        return ManageImagesUseCase(
            imageProcessingService = imageProcessingService
        )
    }
} 