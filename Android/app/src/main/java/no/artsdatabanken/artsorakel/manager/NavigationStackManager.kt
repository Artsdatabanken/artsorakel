package no.artsdatabanken.artsorakel.manager

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import no.artsdatabanken.artsorakel.model.NavigationState
import no.artsdatabanken.artsorakel.model.NavigationStateEntry
import java.util.Stack
import javax.inject.Inject
import javax.inject.Singleton

data class NavigationStackEntry(
    val type: NavigationType,
    val containerId: Int,
    val fragment: Fragment? = null,
    val tag: String,
    val data: Map<String, Any> = emptyMap()
)

enum class NavigationType {
    MAIN_SCREEN,
    INPUT_IMAGES,
    RESULTS,
    EXPANDED_HISTORY,
    HISTORICAL_RESULTS,
    SPECIES_DETAILS,
    SETTINGS
}

@Singleton
class NavigationStackManager @Inject constructor() : DefaultLifecycleObserver {
    
    private val navigationStack = Stack<NavigationStackEntry>()
    private val overlayContainers = mutableMapOf<Int, View>()
    
    fun initialize(overlayViews: Map<Int, View>) {
        overlayContainers.clear()
        overlayContainers.putAll(overlayViews)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        clear()
    }
    
    fun push(entry: NavigationStackEntry) {
        if (!isStackableType(entry.type)) {
            navigationStack.removeAll { it.type == entry.type }
        }
        navigationStack.push(entry)
        updateVisibility()
    }
    
    private fun isStackableType(type: NavigationType): Boolean {
        return type == NavigationType.SPECIES_DETAILS
    }
    
    fun pop(): NavigationStackEntry? {
        if (navigationStack.isEmpty()) return null
        val entry = navigationStack.pop()
        updateVisibility()
        return entry
    }
    
    fun peek(): NavigationStackEntry? {
        return if (navigationStack.isEmpty()) null else navigationStack.peek()
    }

    fun popOfType(type: NavigationType): NavigationStackEntry? {
        val topEntry = navigationStack.lastOrNull { it.type == type }
        if (topEntry != null && navigationStack.peek() == topEntry) {
            return pop()
        }
        return null
    }
    
    fun contains(type: NavigationType): Boolean {
        return navigationStack.any { it.type == type }
    }
    
    fun findEntry(type: NavigationType): NavigationStackEntry? {
        return navigationStack.findLast { it.type == type }
    }
    
    fun clear() {
        navigationStack.clear()
        updateVisibility()
    }

    fun size(): Int = navigationStack.size
    
    fun getStack(): List<NavigationStackEntry> = navigationStack.toList()
    
    fun isTopmost(type: NavigationType): Boolean {
        return peek()?.type == type
    }
    
    private fun updateVisibility() {
        overlayContainers.forEach { (containerId, view) ->
            val hasEntry = navigationStack.any { it.containerId == containerId }
            view.visibility = if (hasEntry) View.VISIBLE else View.GONE
        }
    }

    fun saveState(): NavigationState {
        val entries = navigationStack.map { entry ->
            NavigationStateEntry(
                type = entry.type,
                containerId = entry.containerId,
                tag = entry.tag,
                data = entry.data.mapValues { it.value.toString() }
            )
        }
        return NavigationState(entries)
    }
    
    fun restoreFromState(state: NavigationState) {
        navigationStack.clear()
        state.entries.forEach { stateEntry ->
            navigationStack.push(NavigationStackEntry(
                type = stateEntry.type,
                containerId = stateEntry.containerId,
                fragment = null,
                tag = stateEntry.tag,
                data = stateEntry.data
            ))
        }
    }
}