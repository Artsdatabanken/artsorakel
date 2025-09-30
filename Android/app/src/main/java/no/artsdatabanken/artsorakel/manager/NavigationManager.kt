package no.artsdatabanken.artsorakel.manager

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.core.AppConfig
import no.artsdatabanken.artsorakel.fragments.ExpandedHistoryWrapperFragment
import no.artsdatabanken.artsorakel.fragments.AboutFragment
import no.artsdatabanken.artsorakel.fragments.FaqFragment
import no.artsdatabanken.artsorakel.fragments.HistoricalResultsWrapperFragment
import no.artsdatabanken.artsorakel.fragments.InputImagesFragment
import no.artsdatabanken.artsorakel.fragments.MainScreenFragment
import no.artsdatabanken.artsorakel.fragments.ResultsFragment
import no.artsdatabanken.artsorakel.fragments.SettingsFragment
import no.artsdatabanken.artsorakel.fragments.SpeciesDetailFragment
import no.artsdatabanken.artsorakel.fragments.SpeciesDetailWrapperFragment
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.model.ModelInfo
import javax.inject.Inject

class NavigationManager @Inject constructor(
    private val stackManager: NavigationStackManager,
    private val gson: Gson
) : DefaultLifecycleObserver {
    
    private lateinit var activity: AppCompatActivity
    private var fragmentContainerId: Int = R.id.fragmentContainer

    private val fragmentManager: FragmentManager get() = activity.supportFragmentManager
    private var speciesDetailFragment: SpeciesDetailFragment? = null
    
    fun initialize(activity: AppCompatActivity, fragmentContainerId: Int = R.id.fragmentContainer) {
        this.activity = activity
        this.fragmentContainerId = fragmentContainerId
        activity.lifecycle.addObserver(this)
        
        val overlayViews = mapOf<Int, android.view.View>(
            R.id.historyOverlayContainer to activity.findViewById(R.id.historyOverlayContainer),
            R.id.historicalResultsOverlayContainer to activity.findViewById(R.id.historicalResultsOverlayContainer),
            R.id.speciesDetailsOverlayContainer to activity.findViewById(R.id.speciesDetailsOverlayContainer),
            R.id.settingsOverlayContainer to activity.findViewById(R.id.settingsOverlayContainer)
        )
        stackManager.initialize(overlayViews)
        
        if (stackManager.size() > 0) {
            restoreNavigationState()
        }
    }
    
    private fun restoreNavigationState() {
        val entries = stackManager.getStack()
        
        activity.findViewById<android.view.View>(R.id.historyOverlayContainer)?.visibility = android.view.View.GONE
        activity.findViewById<android.view.View>(R.id.historicalResultsOverlayContainer)?.visibility = android.view.View.GONE
        activity.findViewById<android.view.View>(R.id.speciesDetailsOverlayContainer)?.visibility = android.view.View.GONE
        activity.findViewById<android.view.View>(R.id.settingsOverlayContainer)?.visibility = android.view.View.GONE
        
        entries.forEach { entry ->
            if (entry.containerId != fragmentContainerId) {
                val existingFragment = fragmentManager.findFragmentById(entry.containerId)
                if (existingFragment == null) {
                    when (entry.type) {
                        NavigationType.EXPANDED_HISTORY -> {
                            val fragment = ExpandedHistoryWrapperFragment.newInstance()
                            fragmentManager.beginTransaction()
                                .replace(entry.containerId, fragment, entry.tag)
                                .commitNow()
                            activity.findViewById<android.view.View>(entry.containerId)?.visibility = android.view.View.VISIBLE
                        }
                        NavigationType.HISTORICAL_RESULTS -> {
                            val fragment = HistoricalResultsWrapperFragment.newInstance()
                            fragmentManager.beginTransaction()
                                .replace(entry.containerId, fragment, entry.tag)
                                .commitNow()
                            activity.findViewById<android.view.View>(entry.containerId)?.visibility = android.view.View.VISIBLE
                        }
                        NavigationType.SPECIES_DETAILS -> {
                            val data = entry.data
                            val fragment = SpeciesDetailWrapperFragment.newInstance(
                                data["scientificName"] as? String,
                                data["probability"]?.toString()?.toDoubleOrNull() ?: 0.0,
                                data["pictureUrl"] as? String,
                                data["infoUrl"] as? String,
                                data["scientificNameID"] as? String,
                                null, // vernacularNames
                                null, // groupNames
                                null, // modelInfo
                                null, // redListCategory
                                null  // invasiveCategory
                            )
                            fragmentManager.beginTransaction()
                                .replace(entry.containerId, fragment, entry.tag)
                                .commitNow()
                            activity.findViewById<android.view.View>(entry.containerId)?.visibility = android.view.View.VISIBLE
                        }
                        NavigationType.SETTINGS -> {
                            val fragment = SettingsFragment.newInstance()
                            fragmentManager.beginTransaction()
                                .replace(entry.containerId, fragment, entry.tag)
                                .commitNow()
                            activity.findViewById<android.view.View>(entry.containerId)?.visibility = android.view.View.VISIBLE
                        }
                        else -> {}
                    }
                } else {
                    activity.findViewById<android.view.View>(entry.containerId)?.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        speciesDetailFragment = null
        stackManager.onDestroy(owner)
    }

    fun showMainScreen() {
        val tag = "main_screen"
        val fragment = MainScreenFragment.newInstance()
        
        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(fragmentContainerId, fragment, tag)
            .commitNow()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.MAIN_SCREEN,
            containerId = fragmentContainerId,
            fragment = fragment,
            tag = tag
        ))
    }

    fun showExpandedHistory() {
        if (fragmentManager.isStateSaved) {
            return
        }

        val overlayContainerId = R.id.historyOverlayContainer
        
        if (stackManager.contains(NavigationType.EXPANDED_HISTORY)) {
            return
        }
        
        val fragment = ExpandedHistoryWrapperFragment.newInstance()
        val tag = "history_overlay"
        
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(overlayContainerId, fragment)
            .addToBackStack(tag)
            .commit()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.EXPANDED_HISTORY,
            containerId = overlayContainerId,
            fragment = fragment,
            tag = tag
        ))
    }
    
    fun hideExpandedHistory() {
        val entry = stackManager.findEntry(NavigationType.EXPANDED_HISTORY)
        if (entry != null) {
            stackManager.popOfType(NavigationType.EXPANDED_HISTORY)
            fragmentManager.popBackStack(entry.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }
    

    fun showInputImagesFragment() {
        if (fragmentManager.isStateSaved) {
            return
        }

        val current = fragmentManager.findFragmentById(fragmentContainerId)
        if (current is InputImagesFragment) {
            return
        }

        val tag = "input_images"
        val fragment = InputImagesFragment.newInstance()
        
        fragmentManager.beginTransaction()
            .replace(fragmentContainerId, fragment, tag)
            .commitNow()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.INPUT_IMAGES,
            containerId = fragmentContainerId,
            fragment = fragment,
            tag = tag
        ))
    }

    fun showResultsFragment(fragmentContainer: android.view.View) {
        if (fragmentManager.isStateSaved) {
            return
        }
        
        val tag = "results"
        val fragment = ResultsFragment.newInstance()
        
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        
        fragmentManager.beginTransaction()
            .replace(fragmentContainerId, fragment, tag)
            .commit()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.RESULTS,
            containerId = fragmentContainerId,
            fragment = fragment,
            tag = tag
        ))
        
        fragmentContainer.postDelayed({
            if (fragmentContainer.visibility != android.view.View.VISIBLE && 
                !activity.isFinishing && !activity.isDestroyed) {
                fragmentContainer.visibility = android.view.View.VISIBLE
            }
        }, AppConfig.Timeouts.FRAGMENT_VISIBILITY_DELAY_MS)
    }

    fun showHistoricalResultsFragment(fragmentContainer: android.view.View) {
        if (fragmentManager.isStateSaved) {
            return
        }
        
        val overlayContainerId = R.id.historicalResultsOverlayContainer
        
        if (stackManager.contains(NavigationType.HISTORICAL_RESULTS)) {
            return
        }
        
        val fragment = HistoricalResultsWrapperFragment.newInstance()
        val tag = "historical_results_overlay"
        
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(overlayContainerId, fragment)
            .addToBackStack(tag)
            .commit()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.HISTORICAL_RESULTS,
            containerId = overlayContainerId,
            fragment = fragment,
            tag = tag
        ))
        
        fragmentContainer.postDelayed({
            if (fragmentContainer.visibility != android.view.View.VISIBLE && 
                !activity.isFinishing && !activity.isDestroyed) {
                fragmentContainer.visibility = android.view.View.VISIBLE
            }
        }, AppConfig.Timeouts.FRAGMENT_VISIBILITY_DELAY_MS)
    }
    
    fun hideHistoricalResults() {
        val entry = stackManager.findEntry(NavigationType.HISTORICAL_RESULTS)
        if (entry != null) {
            stackManager.popOfType(NavigationType.HISTORICAL_RESULTS)
            fragmentManager.popBackStack(entry.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    fun showSpeciesDetailFragment(
        scientificName: String?,
        probability: Double,
        pictureUrl: String?,
        infoUrl: String?,
        scientificNameID: String?,
        vernacularNames: Map<String, String>? = null,
        groupNames: Map<String, String>? = null,
        modelInfo: ModelInfo? = null,
        redListCategory: String? = null,
        invasiveCategory: String? = null
    ) {
        val overlayContainerId = R.id.speciesDetailsOverlayContainer
        
        val existingEntry = stackManager.findEntry(NavigationType.SPECIES_DETAILS)
        if (existingEntry != null) {
            val sameId = existingEntry.data["scientificNameID"] == scientificNameID
            if (sameId) {
                return
            }
        }

        val fragment = SpeciesDetailWrapperFragment.newInstance(
            scientificName, probability, pictureUrl, infoUrl, scientificNameID, vernacularNames, groupNames, modelInfo, redListCategory, invasiveCategory
        )
        val tag = "species_detail_overlay"
        
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(overlayContainerId, fragment)
            .addToBackStack(tag)
            .commit()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.SPECIES_DETAILS,
            containerId = overlayContainerId,
            fragment = fragment,
            tag = tag,
            data = mapOf<String, Any>(
                "scientificNameID" to (scientificNameID ?: ""),
                "scientificName" to (scientificName ?: ""),
                "probability" to probability.toString(),
                "pictureUrl" to (pictureUrl ?: ""),
                "infoUrl" to (infoUrl ?: "")
            )
        ))
    }

    fun hideSpeciesDetailFragment() {
        val entry = stackManager.findEntry(NavigationType.SPECIES_DETAILS)
        if (entry != null) {
            stackManager.popOfType(NavigationType.SPECIES_DETAILS)
            fragmentManager.popBackStack(entry.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    fun navigateBackFromSpeciesDetail() {
        hideSpeciesDetailFragment()
    }

    fun showSettingsFragment() {
        if (fragmentManager.isStateSaved) {
            return
        }

        val overlayContainerId = R.id.settingsOverlayContainer

        if (stackManager.contains(NavigationType.SETTINGS)) {
            hideSettingsFragment()
        }

        val fragment = SettingsFragment.newInstance()
        val tag = "settings_overlay"
        
        fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(overlayContainerId, fragment)
            .addToBackStack(tag)
            .commit()
        
        stackManager.push(NavigationStackEntry(
            type = NavigationType.SETTINGS,
            containerId = overlayContainerId,
            fragment = fragment,
            tag = tag
        ))
    }

    fun hideSettingsFragment() {
        val entry = stackManager.findEntry(NavigationType.SETTINGS)
        if (entry != null) {
            stackManager.popOfType(NavigationType.SETTINGS)
            fragmentManager.popBackStack(entry.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    fun navigateBackFromSettings() {
        hideSettingsFragment()
    }

    fun isSpeciesDetailFragmentVisible(): Boolean {
        return stackManager.isTopmost(NavigationType.SPECIES_DETAILS)
    }
    
    fun popTopmostOverlay(): Boolean {
        val topmost = stackManager.peek() ?: return false
        
        if (topmost.containerId != fragmentContainerId) {
            val entry = stackManager.pop()
            if (entry != null) {
                fragmentManager.popBackStack(entry.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                return true
            }
        }
        return false
    }

    fun handleSpeciesItemClick(result: PredictionResult) {
        showSpeciesDetailFragment(
            result.scientificName,
            result.probability,
            result.pictureUrl,
            result.infoUrl,
            result.id,
            result.vernacularNames,
            result.groupNames,
            result.modelInfo,
            result.redListCategory,
            result.invasiveCategory
        )
    }

    fun restoreFragmentForState(
        hasImages: Boolean,
        isSuccessState: Boolean,
        isLoadingState: Boolean,
        isErrorState: Boolean,
        fragmentContainer: android.view.View
    ) {
        when {
            isSuccessState -> {
                showResultsFragment(fragmentContainer)
            }
            isLoadingState -> {
                // Currently loading, show input fragment if we have images
                if (hasImages) {
                    showInputImagesFragment()
                } else {
                    showMainScreen()
                }
            }
            isErrorState -> {
                // Error state, show appropriate fragment
                if (hasImages) {
                    showInputImagesFragment()
                } else {
                    showMainScreen()
                }
            }
            else -> {
                // Idle state
                if (hasImages) {
                    showInputImagesFragment()
                } else {
                    showMainScreen()
                }
            }
        }
    }

    fun showAboutFragment() {
        if (fragmentManager.isStateSaved) {
            return
        }
        val overlayContainerId = R.id.settingsOverlayContainer

        if (stackManager.contains(NavigationType.SETTINGS)) {
            hideSettingsFragment()
        }
        val fragment = AboutFragment()
        val tag = "about_overlay"

        fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(overlayContainerId, fragment)
            .addToBackStack(tag)
            .commit()

        stackManager.push(NavigationStackEntry(
            type = NavigationType.SETTINGS,
            containerId = overlayContainerId,
            fragment = fragment,
            tag = tag
        ))

        activity.findViewById<android.view.View>(overlayContainerId)?.visibility = android.view.View.VISIBLE
    }

    fun showFaqFragment() {
        if (fragmentManager.isStateSaved) {
            return
        }
        val overlayContainerId = R.id.settingsOverlayContainer

        if (stackManager.contains(NavigationType.SETTINGS)) {
            hideSettingsFragment()
        }
        val fragment = FaqFragment()
        val tag = "faq_overlay"

        fragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(overlayContainerId, fragment)
            .addToBackStack(tag)
            .commit()

        stackManager.push(NavigationStackEntry(
            type = NavigationType.SETTINGS,
            containerId = overlayContainerId,
            fragment = fragment,
            tag = tag
        ))

        activity.findViewById<android.view.View>(overlayContainerId)?.visibility = android.view.View.VISIBLE
    }
} 