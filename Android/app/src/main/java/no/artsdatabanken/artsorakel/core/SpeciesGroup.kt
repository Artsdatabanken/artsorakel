package no.artsdatabanken.artsorakel.core

import no.artsdatabanken.artsorakel.R

/**
 * Sealed class representing different species groups with their associated placeholder images.
 * Centralizes the mapping logic that was previously duplicated across multiple files.
 */
sealed class SpeciesGroup(
    val displayName: String,
    val placeholderRes: Int
) {
    object Karplanter : SpeciesGroup("karplanter", R.drawable.placeholder_karplanter)
    object Fugler : SpeciesGroup("fugler", R.drawable.placeholder_fugler)
    object Pattedyr : SpeciesGroup("pattedyr", R.drawable.placeholder_pattedyr)
    object Lav : SpeciesGroup("lav", R.drawable.placeholder_lav)
    object Sommerfugler : SpeciesGroup("sommerfugler", R.drawable.placeholder_sommerfugler)
    object Sopper : SpeciesGroup("sopper", R.drawable.placeholder_sopper)
    object Nebbmunner : SpeciesGroup("nebbmunner", R.drawable.placeholder_nebbmunner)
    object Moser : SpeciesGroup("moser", R.drawable.placeholder_moser)
    object Bløtdyr : SpeciesGroup("bløtdyr", R.drawable.placeholder_bloetdyr)
    object Edderkoppdyr : SpeciesGroup("edderkoppdyr", R.drawable.placeholder_edderkoppdyr)
    object NettvingereOsv : SpeciesGroup("nebbfluer, kamelhalsfluer, mudderfluer, nettvinger", R.drawable.placeholder_nettvinger_osv)
    object Veps : SpeciesGroup("veps", R.drawable.placeholder_veps)
    object Biller : SpeciesGroup("biller", R.drawable.placeholder_biller)
    object Tovinger : SpeciesGroup("tovinger", R.drawable.placeholder_tovinger)
    object Fisker : SpeciesGroup("fisker", R.drawable.placeholder_fisker)
    object AmfibierReptiler : SpeciesGroup("amfibier, reptiler", R.drawable.placeholder_reptiler_osv)
    object DøgnfluerOsv : SpeciesGroup("døgnfluer, øyenstikkere, steinfluer, vårfluer", R.drawable.placeholder_doegnfluer_osv)
    object ArmfotingerOsv : SpeciesGroup("armfotinger, pigghuder, kappedyr", R.drawable.placeholder_pigghuder_osv)
    object Unknown : SpeciesGroup("unknown", R.drawable.placeholder_generic)

    companion object {
        /**
         * Map a species group name to its corresponding SpeciesGroup object.
         * Returns Unknown if no match is found.
         */
        fun fromName(groupName: String?): SpeciesGroup {
            if (groupName.isNullOrBlank()) return Unknown
            
            val normalized = groupName.lowercase().trim()
            return when (normalized) {
                "karplanter" -> Karplanter
                "fugler" -> Fugler
                "pattedyr" -> Pattedyr
                "lav" -> Lav
                "sommerfugler" -> Sommerfugler
                "sopper" -> Sopper
                "nebbmunner" -> Nebbmunner
                "moser" -> Moser
                "bløtdyr" -> Bløtdyr
                "edderkoppdyr" -> Edderkoppdyr
                "nettvinger, kakerlakker, saksedyr" -> NettvingereOsv
                "veps" -> Veps
                "biller" -> Biller
                "tovinger" -> Tovinger
                "fisker" -> Fisker
                "amfibier, reptiler" -> AmfibierReptiler
                "døgnfluer, øyenstikkere, steinfluer, vårfluer" -> DøgnfluerOsv
                "armfotinger, pigghuder, kappedyr" -> ArmfotingerOsv
                else -> Unknown
            }
        }
        
        /**
         * Get the placeholder drawable resource for a species group names map.
         * Uses the Norwegian (nb) translation for matching.
         */
        fun getPlaceholderRes(groupNames: Map<String, String>?): Int {
            val norwegianGroupName = groupNames?.get("nb") ?: groupNames?.get("nn")
            return fromName(norwegianGroupName).placeholderRes
        }
    }
} 