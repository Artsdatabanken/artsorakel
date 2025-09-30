package no.artsdatabanken.artsorakel.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.artsdatabanken.artsorakel.service.ImageCacheService
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import no.artsdatabanken.artsorakel.service.ThumbnailService
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for providing service dependencies.
 * Provides service instances for dependency injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    /**
     * Provides the ImageProcessingService as a singleton.
     * This service handles image processing and file operations.
     */
    @Provides
    @Singleton
    fun provideImageProcessingService(): ImageProcessingService {
        return ImageProcessingService()
    }
    
    /**
     * Provides the ImageCacheService as a singleton.
     * This service handles aggressive caching of species images and distribution maps.
     */
    @Provides
    @Singleton
    fun provideImageCacheService(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageCacheService {
        return ImageCacheService(context, okHttpClient)
    }
    
    /**
     * Provides the ThumbnailService as a singleton.
     * This service handles creating and managing thumbnails for identification history.
     */
    @Provides
    @Singleton
    fun provideThumbnailService(): ThumbnailService {
        return ThumbnailService()
    }
} 