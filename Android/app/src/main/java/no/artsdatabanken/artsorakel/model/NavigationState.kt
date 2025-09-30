package no.artsdatabanken.artsorakel.model

import no.artsdatabanken.artsorakel.manager.NavigationType
import java.io.Serializable

data class NavigationStateEntry(
    val type: NavigationType,
    val containerId: Int,
    val tag: String,
    val data: Map<String, String> = emptyMap()
) : Serializable

data class NavigationState(
    val entries: List<NavigationStateEntry>
) : Serializable