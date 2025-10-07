package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import no.artsdatabanken.artsorakel.viewmodel.UiState
import no.artsdatabanken.artsorakel.adapter.ImageAdapter
import no.artsdatabanken.artsorakel.adapter.PredictionResultAdapter
import no.artsdatabanken.artsorakel.databinding.FragmentResultsBinding
import no.artsdatabanken.artsorakel.view.ResultDividerItemDecoration
import no.artsdatabanken.artsorakel.core.FragmentEvent
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.utils.Logger
import no.artsdatabanken.artsorakel.core.SpeciesGroup
import no.artsdatabanken.artsorakel.model.WarningItem
import no.artsdatabanken.artsorakel.model.WarningCategory
import no.artsdatabanken.artsorakel.manager.LanguageManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView
import android.view.Gravity
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var predictionResultAdapter: PredictionResultAdapter

    @Inject
    lateinit var languageManager: LanguageManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWindowInsets()
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
    }
    
    override fun onResume() {
        super.onResume()
        if (!isInOverlay()) {
            (activity as? MainActivity)?.apply {
                showHeaderBackButton()
                setHeaderTitle(R.string.results)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (!isInOverlay()) {
            (activity as? MainActivity)?.hideHeaderBackButton()
        }
    }
    
    private fun isInOverlay(): Boolean {
        return parentFragment is HistoricalResultsWrapperFragment
    }

    private fun setupWindowInsets() {
        if (Build.VERSION.SDK_INT >= 35) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
                val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                
                val bottomInset = maxOf(systemBarsInsets.bottom, displayCutoutInsets.bottom)
                val leftInset = maxOf(systemBarsInsets.left, displayCutoutInsets.left)
                val rightInset = maxOf(systemBarsInsets.right, displayCutoutInsets.right)
                
                val originalMargin = try {
                    resources.getDimensionPixelSize(R.dimen.results_button_margin)
                } catch (_: Exception) {
                    (16 * resources.displayMetrics.density).toInt()
                }
                val layoutParams = binding.buttonReset.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.bottomMargin = originalMargin + bottomInset
                binding.buttonReset.layoutParams = layoutParams
                
                view.updatePadding(
                    left = leftInset,
                    right = rightInset
                )
                
                Logger.d("WindowInsets", 
                    "ResultsFragment - Applied insets - bottom: $bottomInset, left: $leftInset, right: $rightInset")
                
                windowInsets
            }
        } else {
            val originalMargin = try {
                resources.getDimensionPixelSize(R.dimen.results_button_margin)
            } catch (_: Exception) {
                (16 * resources.displayMetrics.density).toInt()
            }
            
            val layoutParams = binding.buttonReset.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            layoutParams.bottomMargin = originalMargin
            binding.buttonReset.layoutParams = layoutParams
        }
    }

    private fun setupRecyclerViews() {
        imageAdapter = ImageAdapter(
            isHistoricalMode = false,
            onImageClick = { croppedUri ->
                viewModel.handleFragmentEvent(FragmentEvent.ViewImage(croppedUri))
            },
            onAddImageClick = {
                viewModel.handleFragmentEvent(FragmentEvent.AddImage)
            }
        )
        binding.recyclerViewResultInput.adapter = imageAdapter
        binding.recyclerViewResultInput.layoutManager = LinearLayoutManager(
            requireContext(), 
            LinearLayoutManager.HORIZONTAL, 
            false
        )

        predictionResultAdapter = PredictionResultAdapter { result ->
            viewModel.handleFragmentEvent(FragmentEvent.SelectSpecies(result))
        }
        binding.recyclerViewResults.adapter = predictionResultAdapter
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(requireContext())

        val dividerDecoration = ResultDividerItemDecoration(requireContext())
        binding.recyclerViewResults.addItemDecoration(dividerDecoration)
    }

    private fun setupClickListeners() {
        binding.buttonReset.setOnClickListener { 
            viewModel.handleFragmentEvent(FragmentEvent.ResetApp)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedImageUris.collect { uris ->
                    imageAdapter.submitList(uris)
                }
            }
        }

        // Note: Location data is still collected and sent to server,
        // just not displayed in UI anymore

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            if (state.isHistorical && state.historicalDate != null) {
                                showHistoricalDate(state.historicalDate)
                                setupImageAdapterForHistorical()
                            } else {
                                hideHistoricalDate()
                                setupImageAdapterForNormal()
                            }
                            predictionResultAdapter.submitList(state.results)

                            // Display warnings from API
                            if (state.warnings != null) {
                                showWarnings(state.warnings, state.results)
                            } else {
                                hideWarnings()
                            }
                        }
                        else -> {
                            predictionResultAdapter.submitList(emptyList())
                            hideWarnings()
                        }
                    }
                }
            }
        }
    }

    private fun showWarnings(warnings: no.artsdatabanken.artsorakel.model.Warnings, results: List<no.artsdatabanken.artsorakel.model.PredictionResult>) {
        binding.warningsContainer.removeAllViews()
        binding.warningsContainer.visibility = View.VISIBLE

        val currentLanguage = languageManager.getCurrentLanguageTag()
        val allWarnings = mutableListOf<Pair<WarningItem, String?>>()

        // Add general warnings
        warnings.general.forEach { warning ->
            allWarnings.add(warning to null)
        }

        // Add prediction-specific warnings with species name
        warnings.predictions.forEach { (index, warningList) ->
            val speciesName = results.getOrNull(index)?.let { prediction ->
                prediction.getVernacularNameForLanguage(currentLanguage) ?: prediction.scientificName
            }
            warningList.forEach { warning ->
                allWarnings.add(warning to speciesName)
            }
        }

        // Display all warnings
        allWarnings.forEach { (warning, speciesName) ->
            val warningCard = createWarningCard(warning, speciesName, currentLanguage)
            binding.warningsContainer.addView(warningCard)
        }
    }

    private fun createWarningCard(warning: WarningItem, speciesName: String?, currentLanguage: String): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardElevation = 0f
            radius = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.card_stroke_width)
        }

        // Set colors based on category
        val (surfaceAttr, borderAttr, textAttr, iconRes) = when (warning.category) {
            WarningCategory.DANGER -> listOf(
                R.attr.alert_danger_surface_subtle,
                R.attr.alert_danger_border_primary,
                R.attr.alert_danger_text_primary,
                R.drawable.ic_alert_info
            )
            WarningCategory.WARNING -> listOf(
                R.attr.alert_warning_surface_subtle,
                R.attr.alert_warning_border_primary,
                R.attr.alert_warning_text_primary,
                R.drawable.ic_alert_warning
            )
            WarningCategory.INFO -> listOf(
                R.attr.alert_info_surface_subtle,
                R.attr.alert_info_border_primary,
                R.attr.alert_info_text_primary,
                R.drawable.ic_alert_info
            )
        }

        val typedValue = android.util.TypedValue()

        // Resolve colors
        requireContext().theme.resolveAttribute(surfaceAttr, typedValue, true)
        val surfaceColor = typedValue.data

        requireContext().theme.resolveAttribute(borderAttr, typedValue, true)
        val borderColor = typedValue.data

        requireContext().theme.resolveAttribute(textAttr, typedValue, true)
        val textColor = typedValue.data

        card.setCardBackgroundColor(surfaceColor)
        card.strokeColor = borderColor

        val container = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        // Left border
        val leftBorder = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.warning_left_border_width),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(borderColor)
        }
        container.addView(leftBorder)

        // Content container
        val contentContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.HORIZONTAL
            val padding = resources.getDimensionPixelSize(R.dimen.warning_content_padding)
            setPadding(padding, padding, padding, padding)
        }

        // Icon
        val icon = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.warning_icon_size),
                resources.getDimensionPixelSize(R.dimen.warning_icon_size)
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.warning_icon_margin)
                gravity = Gravity.CENTER_VERTICAL
            }
            setImageResource(iconRes)
            setColorFilter(textColor)
        }
        contentContainer.addView(icon)

        // Text container
        val textContainer = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }

        // Title (if present)
        warning.title?.get(currentLanguage)?.let { titleText ->
            val title = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = if (speciesName != null) "$titleText ($speciesName)" else titleText
                textSize = 16f
                setTextColor(textColor)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textContainer.addView(title)
        }

        // Message
        val messageText = warning.message[currentLanguage] ?: warning.message.values.firstOrNull() ?: ""
        val message = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = if (speciesName != null && warning.title == null) {
                "$speciesName: $messageText"
            } else {
                messageText
            }
            textSize = 14f
            setTextColor(textColor)
        }
        textContainer.addView(message)

        contentContainer.addView(textContainer)
        container.addView(contentContainer)
        card.addView(container)

        // Handle link click
        warning.link?.get(currentLanguage)?.let { url ->
            card.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Logger.e("ResultsFragment", "Failed to open warning link: ${e.message}")
                }
            }
        } ?: run {
            card.setOnClickListener {
                // Consume click to prevent click-through
            }
        }

        return card
    }

    private fun hideWarnings() {
        binding.warningsContainer.visibility = View.GONE
        binding.warningsContainer.removeAllViews()
    }
    
    private fun showHistoricalDate(date: java.util.Date) {
        val dateFormat = java.text.SimpleDateFormat("dd. MMM yyyy", java.util.Locale.getDefault())
        binding.textViewHistoricalDate.text = getString(R.string.historical_result_from, dateFormat.format(date))
        binding.textViewHistoricalDate.visibility = View.VISIBLE
        binding.buttonReset.visibility = View.GONE
    }
    
    private fun hideHistoricalDate() {
        binding.textViewHistoricalDate.visibility = View.GONE
        binding.buttonReset.visibility = View.VISIBLE
    }
    
    private fun setupImageAdapterForHistorical() {
        imageAdapter.updateMode(true)
        binding.recyclerViewResultInput.adapter = imageAdapter
        imageAdapter.submitList(viewModel.selectedImageUris.value)
    }
    
    private fun setupImageAdapterForNormal() {
        imageAdapter.updateMode(false)
        binding.recyclerViewResultInput.adapter = imageAdapter
        imageAdapter.submitList(viewModel.selectedImageUris.value)
    }

    fun isHistoricalResults(): Boolean {
        val currentState = viewModel.uiState.value
        return currentState is UiState.Success && currentState.isHistorical
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ResultsFragment()
    }
} 