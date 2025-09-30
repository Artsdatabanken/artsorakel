package no.artsdatabanken.artsorakel.usecase

import android.content.Context
import android.net.Uri
import no.artsdatabanken.artsorakel.core.errors.AppError
import no.artsdatabanken.artsorakel.core.errors.ErrorMapper
import no.artsdatabanken.artsorakel.core.errors.logError
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.model.GeoLocation
import no.artsdatabanken.artsorakel.repository.SpeciesRepository
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import no.artsdatabanken.artsorakel.utils.SettingsManager
import javax.inject.Inject

/**
 * Use Case for species classification business logic.
 * Coordinates image processing and species identification to provide a complete classification workflow.
 */
class ClassifySpeciesUseCase @Inject constructor(
    private val repository: SpeciesRepository,
    private val imageProcessingService: ImageProcessingService,
    private val settingsManager: SettingsManager
) {
    
    /**
     * Configuration for image processing
     */
    data class ImageProcessingConfig(
        val targetWidth: Int = 500,
        val targetHeight: Int = 500,
        val quality: Int = 85
    )
    
    /**
     * Result of the classification operation
     */
    sealed class ClassificationResult {
        data class Success(val predictions: List<PredictionResult>) : ClassificationResult()
        data class PartialFailure(
            val predictions: List<PredictionResult>,
            val failedImageCount: Int
        ) : ClassificationResult()
        data class Failure(val error: AppError) : ClassificationResult()
    }
    
    /**
     * Classifies species from a list of image URIs.
     * 
     * @param context Android context for content resolution
     * @param imageUris List of image URIs to process and classify
     * @param config Configuration for image processing (optional)
     * @return ClassificationResult containing predictions or error information
     */
    suspend fun classifySpecies(
        context: Context,
        imageUris: List<Uri>,
        imagePairs: List<ImagePair>? = null,
        config: ImageProcessingConfig = ImageProcessingConfig()
    ): ClassificationResult {
        
        // Validate input
        if (imageUris.isEmpty()) {
            val error = ErrorMapper.createNoImagesSelectedError()
            error.logError("ClassifySpeciesUseCase")
            return ClassificationResult.Failure(error)
        }
        
        try {
            // Step 1: Process all images
            val (processedImages, imageFilenames, failedUris) = imageProcessingService.processMultipleImages(
                context = context,
                uris = imageUris,
                targetWidth = config.targetWidth,
                targetHeight = config.targetHeight,
                quality = config.quality
            )
            
            // Step 2: Check if we have any successfully processed images
            if (processedImages.isEmpty()) {
                val error = ErrorMapper.createAllImagesFailedError(failedUris.size)
                error.logError("ClassifySpeciesUseCase")
                return ClassificationResult.Failure(error)
            }
            
            // Step 3: Extract location from the first image that has location data
            // Only use location if the setting is enabled
            val location = if (settingsManager.isUseLocationForIdEnabled()) {
                imagePairs?.firstNotNullOfOrNull { it.location }
            } else {
                null
            }

            // Step 4: Submit processed images for species identification
            val repositoryResult = repository.identifySpecies(
                imageDataList = processedImages,
                imageFilenames = imageFilenames,
                location = location
            )
            
            // Step 4: Handle repository result
            return repositoryResult.fold(
                onSuccess = { predictions ->
                    if (failedUris.isNotEmpty()) {
                        // Some images failed processing but we got results
                        ClassificationResult.PartialFailure(
                            predictions = predictions,
                            failedImageCount = failedUris.size
                        )
                    } else {
                        // All images processed successfully
                        ClassificationResult.Success(predictions)
                    }
                },
                onFailure = { exception ->
                    val error = ErrorMapper.mapException(exception)
                    error.logError("ClassifySpeciesUseCase")
                    ClassificationResult.Failure(error)
                }
            )
            
        } catch (e: Exception) {
            val error = ErrorMapper.mapException(e)
            error.logError("ClassifySpeciesUseCase")
            return ClassificationResult.Failure(error)
        }
    }
    
    /**
     * Cancels any ongoing classification operation.
     */
    suspend fun cancelClassification() {
        repository.cancelIdentification()
    }
    
    /**
     * Checks if a classification operation is currently in progress.
     */
    fun isClassificationInProgress(): Boolean {
        return repository.isIdentificationInProgress()
    }
} 