// File: network/ApiService.kt
package no.artsdatabanken.artsorakel.network

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit
import no.artsdatabanken.artsorakel.core.AppConfig

// --- Retrofit Service Interface ---
interface ApiService {

    @Headers("Accept: */*")
    @Multipart
    @POST("/identify")
    suspend fun classifyImages(
        @Part image: List<MultipartBody.Part>,
        @Part("application") application: RequestBody,
        @Part("latitude") latitude: RequestBody? = null,
        @Part("longitude") longitude: RequestBody? = null
    ): ApiResponse

    /**
     * Saves images to the server for reporting.
     * Returns an ID and password that can be used to reference the images
     * when redirecting to artsobservasjoner.no for observation reporting.
     */
    @Headers("Accept: */*")
    @Multipart
    @POST("/save")
    suspend fun saveImages(
        @Part image: List<MultipartBody.Part>
    ): SaveImageResponse

    // --- Retrofit Client Setup ---
    companion object {
        fun create(): ApiService {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(AppConfig.Network.TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AppConfig.Network.TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(AppConfig.Network.TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // Only add logging interceptor in debug builds
            val logger = okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            }
            clientBuilder.addInterceptor(logger)

            val client = clientBuilder.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(AppConfig.Network.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}