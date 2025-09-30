package no.artsdatabanken.artsorakel.core

import android.content.Context
import android.graphics.Typeface
import android.view.View
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.model.PredictionResult
import java.util.Locale

/**
 * Data class containing processed display information for a species.
 * Consolidates the logic for determining how to display species names and certainty.
 * Does not hardcode strings - provides data for components to format using string resources.
 */
data class SpeciesDisplayData(
    val headerText: String,
    val headerTypeface: Int,
    val scientificNameText: String?,
    val scientificNameVisibility: Int,
    val groupText: String,
    val certaintyPercentage: Int,
    val certaintyTextParameter: String, // Parameter for certainty_text string resource
    val placeholderRes: Int
) {
    companion object {
        /**
         * Create display data from a PredictionResult.
         * Centralizes the name display logic that was previously duplicated.
         * @param showNorwegianFallback If true, shows Norwegian name in details page. If false, shows only scientific name.
         */
        fun fromPredictionResult(result: PredictionResult, context: Context? = null, showNorwegianFallback: Boolean = true): SpeciesDisplayData {
            // Get current language from context or use system default
            val currentLanguage = context?.resources?.configuration?.locales?.get(0)?.language ?: "nb"
            val isNorwegian = currentLanguage == "nb" || currentLanguage == "nn"
            val vernacularName = result.getVernacularNameForLanguage(currentLanguage)
            val scientificName = result.scientificName
            val groupName = result.getGroupNameForLanguage(currentLanguage)

            // Debug logging
            android.util.Log.d("SpeciesDisplayData", "Current language: $currentLanguage")
            android.util.Log.d("SpeciesDisplayData", "Available group names: ${result.groupNames}")
            android.util.Log.d("SpeciesDisplayData", "Selected group name: $groupName")
            val probability = result.probability
            val certaintyPercentage = (probability * 100).toInt()

            // Check if we need to show Norwegian fallback (only in details page)
            val norwegianName = if (!isNorwegian && vernacularName == null && showNorwegianFallback) {
                result.getNorwegianName()
            } else null

            // Use scientific name as header if no vernacular name in current language
            val shouldUseScientificAsHeader = vernacularName.isNullOrBlank()

            val (headerText, headerTypeface, scientificNameText, scientificNameVisibility, certaintyTextParam) = if (shouldUseScientificAsHeader) {
                val displayScientific = scientificName ?: "N/A"
                // Include Norwegian name in certainty text only if allowed and available
                val certaintyParam = if (norwegianName != null && context != null) {
                    val norwegianText = context.getString(R.string.in_norwegian_format, norwegianName)
                    "<i>$displayScientific</i> $norwegianText"
                } else {
                    "<i>$displayScientific</i>"
                }
                Quintuple(
                    displayScientific,
                    Typeface.ITALIC,
                    null,
                    View.GONE,
                    certaintyParam
                )
            } else {
                val capitalizedVernacular = vernacularName!!.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
                Quintuple(
                    capitalizedVernacular,
                    Typeface.NORMAL,
                    scientificName ?: "N/A",
                    View.VISIBLE,
                    vernacularName // Use original vernacular name for string resource
                )
            }

            return SpeciesDisplayData(
                headerText = headerText,
                headerTypeface = headerTypeface,
                scientificNameText = scientificNameText,
                scientificNameVisibility = scientificNameVisibility,
                groupText = groupName ?: "",
                certaintyPercentage = certaintyPercentage,
                certaintyTextParameter = certaintyTextParam,
                placeholderRes = SpeciesGroup.getPlaceholderRes(result.groupNames)
            )
        }
        
        /**
         * Create display data for SpeciesDetailFragment.
         * Similar to PredictionResult but with different formatting needs.
         */
        fun fromSpeciesDetail(
            scientificName: String?,
            probability: Double,
            vernacularNames: Map<String, String>? = null,
            groupNames: Map<String, String>? = null,
            context: Context? = null
        ): SpeciesDisplayData {
            val currentLanguage = context?.resources?.configuration?.locales?.get(0)?.language ?: "nb"
            val isNorwegian = currentLanguage == "nb" || currentLanguage == "nn"

            // Get localized group name
            val localizedGroupName = groupNames?.get(currentLanguage)

            // Get name for current language
            val displayVernacularName = vernacularNames?.get(currentLanguage)

            // Check if we need Norwegian fallback
            val norwegianName = if (!isNorwegian && displayVernacularName == null && vernacularNames != null) {
                vernacularNames["nb"] ?: vernacularNames["nn"]
            } else null

            val certaintyPercentage = (probability * 100).toInt()

            // Use scientific name as header if no vernacular name in current language
            val shouldUseScientificAsHeader = displayVernacularName.isNullOrBlank()
            
            val (headerText, headerTypeface, scientificNameText, scientificNameVisibility, certaintyTextParam) = if (shouldUseScientificAsHeader) {
                val displayScientific = scientificName ?: ""
                // Include Norwegian name in certainty text if available
                val certaintyParam = if (norwegianName != null && context != null) {
                    val norwegianText = context.getString(R.string.in_norwegian_format, norwegianName)
                    "<i>$displayScientific</i> $norwegianText"
                } else {
                    "<i>$displayScientific</i>"
                }
                Quintuple(
                    displayScientific,
                    Typeface.BOLD_ITALIC,
                    null,
                    View.GONE,
                    certaintyParam
                )
            } else {
                val capitalizedVernacular = displayVernacularName!!.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
                Quintuple(
                    capitalizedVernacular,
                    Typeface.NORMAL,
                    scientificName ?: "",
                    View.VISIBLE,
                    displayVernacularName // Use original vernacular name for string resource
                )
            }

            return SpeciesDisplayData(
                headerText = headerText,
                headerTypeface = headerTypeface,
                scientificNameText = scientificNameText,
                scientificNameVisibility = scientificNameVisibility,
                groupText = localizedGroupName ?: "",
                certaintyPercentage = certaintyPercentage,
                certaintyTextParameter = certaintyTextParam,
                placeholderRes = SpeciesGroup.getPlaceholderRes(groupNames)
            )
        }
    }
}

/**
 * Helper data class for multiple return values.
 * Kotlin doesn't have built-in tuples, so we create this for internal use.
 */
private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
) 