package no.artsdatabanken.artsorakel.core

/**
 * Centralized constants for the entire application.
 * All hardcoded values should be defined here to improve maintainability.
 * 
 * Note: Network and timeout configuration has been moved to AppConfig.kt
 * to support environment-specific values (debug vs release).
 */
object Constants {
    
    /**
     * SharedPreferences keys and names
     */
    object Preferences {
        const val APP_SETTINGS = "AppSettings"
        const val IMAGE_INPUT_PREFS = "image_input_prefs"
        const val THEME_KEY = "SelectedTheme"
        const val LANGUAGE_KEY = "SelectedLanguage"
        const val LAST_INPUT_METHOD = "last_input_method"
        const val SAVED_URIS_KEY = "selectedImageUris"
    }
    
    /**
     * Fragment argument keys
     */
    object FragmentArgs {
        // SpeciesDetailFragment arguments
        const val ARG_VERNACULAR_NAME = "arg_vernacular_name"
        const val ARG_VERNACULAR_NAMES = "arg_vernacular_names" // JSON string of Map<String, String>
        const val ARG_SCIENTIFIC_NAME = "arg_scientific_name"
        const val ARG_PROBABILITY = "arg_probability"
        const val ARG_PICTURE_URL = "arg_picture_url"
        const val ARG_GROUP_NAME = "arg_group_name"
        const val ARG_GROUP_NAMES = "arg_group_names" // JSON string of Map<String, String>
        const val ARG_INFO_URL = "arg_info_url"
        const val ARG_SCIENTIFIC_NAME_ID = "arg_scientific_name_id"
        const val ARG_MODEL_INFO = "arg_model_info"
        const val ARG_REDLIST_CATEGORY = "arg_redlist_category"
        const val ARG_INVASIVE_CATEGORY = "arg_invasive_category"
    }
    
    /**
     * Intent extra keys
     */
    object IntentExtras {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_ORIGINAL_IMAGE_URI = "extra_original_image_uri"
        const val EXTRA_IS_RECROPPING = "extra_is_recropping"
    }
    
    /**
     * RecyclerView view types
     */
    object ViewTypes {
        const val VIEW_TYPE_IMAGE = 0
        const val VIEW_TYPE_ADD_BUTTON = 1
    }
} 