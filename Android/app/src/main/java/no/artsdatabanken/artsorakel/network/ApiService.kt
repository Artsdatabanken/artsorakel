package no.artsdatabanken.artsorakel.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit service interface for species identification API.
 * The actual Retrofit instance is provided via Hilt DI in NetworkModule.
 */
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
}