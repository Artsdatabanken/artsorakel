package no.artsdatabanken.artsorakel.usecase

import android.net.Uri
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import no.artsdatabanken.artsorakel.model.ImagePair
import no.artsdatabanken.artsorakel.model.GeoLocation
import javax.inject.Inject

/**
 * Use Case for image management operations.
 * Handles the business logic for managing image pairs including file cleanup.
 */
class ManageImagesUseCase @Inject constructor(
    private val imageProcessingService: ImageProcessingService
) {
    
    /**
     * Result of image management operations
     */
    sealed class ImageManagementResult {
        data object Success : ImageManagementResult()
        data class PartialSuccess(val cleanedCount: Int, val totalCount: Int) : ImageManagementResult()
        data class Error(val message: String) : ImageManagementResult()
    }
    
    /**
     * Adds a new image pair, preventing duplicates that can occur during sharing.
     * Supports multiple images added one by one, but prevents the same image being added twice.
     * 
     * @param currentPairs Current list of image pairs
     * @param croppedUri URI of the cropped image
     * @param originalUri URI of the original image
     * @param isRecrop Whether this is an explicit recrop operation (should replace existing)
     * @param oldCroppedUri The specific cropped URI to replace when recropping
     * @return Pair containing the updated list and cleanup result
     */
    suspend fun addImagePair(
        currentPairs: List<ImagePair>,
        croppedUri: Uri,
        originalUri: Uri,
        location: GeoLocation? = null,
        isRecrop: Boolean = false,
        oldCroppedUri: Uri? = null
    ): Pair<List<ImagePair>, ImageManagementResult> {
        
        val updatedList = currentPairs.toMutableList()
        
        // Check if we already have this exact cropped image (prevents re-adding the same crop)
        val existingCroppedIndex = updatedList.indexOfFirst { 
            it.croppedUri.toString() == croppedUri.toString() 
        }
        
        if (existingCroppedIndex != -1) {
            // We already have this exact cropped image, don't add it again
            return Pair(updatedList.toList(), ImageManagementResult.Success)
        }
        
        val cleanupResult = if (isRecrop && oldCroppedUri != null) {
            // Find the specific image to replace by its cropped URI
            val indexToReplace = updatedList.indexOfFirst { 
                it.croppedUri.toString() == oldCroppedUri.toString() 
            }
            
            if (indexToReplace != -1) {
                // Remove the old cropped image file
                val oldUri = updatedList[indexToReplace].croppedUri
                val cleanupSuccess = imageProcessingService.deleteFile(oldUri)
                
                // Replace with new pair including location
                updatedList[indexToReplace] = ImagePair(croppedUri, originalUri, location)
                
                if (cleanupSuccess) {
                    ImageManagementResult.Success
                } else {
                    ImageManagementResult.PartialSuccess(cleanedCount = 0, totalCount = 1)
                }
            } else {
                // Old cropped URI not found, just add as new with location
                updatedList.add(ImagePair(croppedUri, originalUri, location))
                ImageManagementResult.Success
            }
        } else {
            // Add as new image pair (not recropping or no old URI specified) with location
            updatedList.add(ImagePair(croppedUri, originalUri, location))
            ImageManagementResult.Success
        }
        
        return Pair(updatedList.toList(), cleanupResult)
    }
    
    /**
     * Removes an image pair and cleans up associated files.
     * 
     * @param currentPairs Current list of image pairs
     * @param pairToRemove The image pair to remove
     * @return Pair containing the updated list and cleanup result
     */
    suspend fun removeImagePair(
        currentPairs: List<ImagePair>,
        pairToRemove: ImagePair
    ): Pair<List<ImagePair>, ImageManagementResult> {
        
        // Delete both the cropped and original image files
        val cleanedCount = imageProcessingService.deleteFiles(
            listOf(pairToRemove.croppedUri, pairToRemove.originalUri)
        )
        
        // Remove the pair from the list
        val updatedList = currentPairs.toMutableList().apply {
            remove(pairToRemove)
        }
        
        val result = when (cleanedCount) {
            2 -> ImageManagementResult.Success
            1 -> ImageManagementResult.PartialSuccess(cleanedCount = 1, totalCount = 2)
            0 -> ImageManagementResult.PartialSuccess(cleanedCount = 0, totalCount = 2)
            else -> ImageManagementResult.Success // shouldn't happen, but treat as success
        }
        
        return Pair(updatedList.toList(), result)
    }
    
    /**
     * Clears all image pairs and cleans up associated files.
     * 
     * @param currentPairs Current list of image pairs to clear
     * @return Cleanup result indicating how many files were successfully deleted
     */
    suspend fun clearAllImages(
        currentPairs: List<ImagePair>
    ): ImageManagementResult {
        
        if (currentPairs.isEmpty()) {
            return ImageManagementResult.Success
        }
        
        // Delete all image files
        val allUris = currentPairs.flatMap { listOf(it.croppedUri, it.originalUri) }
        val cleanedCount = imageProcessingService.deleteFiles(allUris)
        val totalCount = allUris.size
        
        return when {
            cleanedCount == totalCount -> ImageManagementResult.Success
            cleanedCount > 0 -> ImageManagementResult.PartialSuccess(cleanedCount, totalCount)
            else -> ImageManagementResult.PartialSuccess(0, totalCount)
        }
    }
    
    /**
     * Validates if the image pair list is ready for classification.
     * 
     * @param currentPairs Current list of image pairs
     * @return true if the list contains at least one valid image pair
     */
    fun isReadyForClassification(currentPairs: List<ImagePair>): Boolean {
        return currentPairs.isNotEmpty()
    }
    
    /**
     * Gets the cropped URIs from image pairs for processing.
     * 
     * @param currentPairs Current list of image pairs
     * @return List of cropped URIs ready for classification
     */
    fun getCroppedUris(currentPairs: List<ImagePair>): List<Uri> {
        return currentPairs.map { it.croppedUri }
    }
} 