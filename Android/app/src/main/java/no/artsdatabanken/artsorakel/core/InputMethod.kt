package no.artsdatabanken.artsorakel.core

/**
 * Sealed class representing different image input methods.
 * Provides better type safety and extensibility compared to enum.
 */
sealed class InputMethod(val name: String) {
    object Gallery : InputMethod("GALLERY")
    object Camera : InputMethod("CAMERA")
    
    companion object {
        /**
         * Convert from string name to InputMethod instance.
         * Returns Gallery as default for unknown values.
         */
        fun fromName(name: String?): InputMethod {
            return when (name?.uppercase()) {
                "CAMERA" -> Camera
                "GALLERY", null -> Gallery
                else -> Gallery
            }
        }
        
        /**
         * Get all available input methods.
         */
        fun values(): List<InputMethod> = listOf(Gallery, Camera)
    }
} 