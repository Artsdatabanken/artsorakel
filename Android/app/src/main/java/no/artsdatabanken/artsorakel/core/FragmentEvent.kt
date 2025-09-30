package no.artsdatabanken.artsorakel.core

import android.net.Uri
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.IdentificationHistory

/**
 * Centralized event system for fragment communication.
 * Replaces individual listener interfaces with a unified event pattern.
 */
sealed class FragmentEvent {
    // Navigation events
    data object NavigateBack : FragmentEvent()

    // Action events
    data object IdentifySpecies : FragmentEvent()
    data object ResetApp : FragmentEvent()
    data object AddImage : FragmentEvent()
    data class ViewImage(val uri: Uri) : FragmentEvent()
    data class SelectSpecies(val result: PredictionResult) : FragmentEvent()
    data class ViewHistoryResults(val historyItem: IdentificationHistory) : FragmentEvent()
    
    // UI state events
    data object ResultsReady : FragmentEvent()
    data object BackToHistoryList : FragmentEvent()
}


