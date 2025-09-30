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
import dagger.hilt.android.AndroidEntryPoint
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

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var predictionResultAdapter: PredictionResultAdapter

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
                            checkForFungi(state.results)
                        }
                        else -> {
                            predictionResultAdapter.submitList(emptyList())
                            hideFungiWarning()
                        }
                    }
                }
            }
        }
    }

    private fun checkForFungi(results: List<no.artsdatabanken.artsorakel.model.PredictionResult>) {
        val hasFungi = results.any { result ->
            val norwegianGroupName = result.getNorwegianGroupName()
            norwegianGroupName != null && SpeciesGroup.fromName(norwegianGroupName) == SpeciesGroup.Sopper
        }

        if (hasFungi) {
            showFungiWarning()
        } else {
            hideFungiWarning()
        }
    }

    private fun showFungiWarning() {
        binding.cardFungiWarning.visibility = View.VISIBLE
        binding.cardFungiWarning.setOnClickListener {
            // Consume click to prevent click-through to underlying views
        }
    }

    private fun hideFungiWarning() {
        binding.cardFungiWarning.visibility = View.GONE
        binding.cardFungiWarning.setOnClickListener(null)
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