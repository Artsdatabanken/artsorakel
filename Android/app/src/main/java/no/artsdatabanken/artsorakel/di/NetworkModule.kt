package no.artsdatabanken.artsorakel.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import no.artsdatabanken.artsorakel.core.AppConfig
import no.artsdatabanken.artsorakel.network.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for providing network dependencies.
 * Provides network service instances for dependency injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    /**
     * Provides a configured OkHttpClient as a singleton.
     * This client is shared between ApiService and ImageCacheService for consistency.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(AppConfig.Network.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.Network.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.Network.TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // Add Authorization header only for artsdatabanken.no API requests
        if (AppConfig.Network.BEARER_TOKEN.isNotEmpty()) {
            clientBuilder.addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host

                // Only add bearer token for artsdatabanken.no domains
                val request = if (host.endsWith("artsdatabanken.no")) {
                    original.newBuilder()
                        .header("Authorization", "Bearer ${AppConfig.Network.BEARER_TOKEN}")
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
        }

        // Only add logging interceptor in debug builds
        if (AppConfig.Network.ENABLE_LOGGING) {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            clientBuilder.addInterceptor(logger)
        }

        return clientBuilder.build()
    }
    
    /**
     * Provides the ApiService as a singleton.
     * This service handles HTTP communication with the species identification API.
     */
    @Provides
    @Singleton
    fun provideApiService(okHttpClient: OkHttpClient): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.Network.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
} 