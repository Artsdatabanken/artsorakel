package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import no.artsdatabanken.artsorakel.adapter.ExpandedHistoryAdapter
import no.artsdatabanken.artsorakel.adapter.SwipeToRevealTouchHelper
import no.artsdatabanken.artsorakel.core.FragmentEvent
import no.artsdatabanken.artsorakel.databinding.FragmentExpandedHistoryBinding
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel

@AndroidEntryPoint
class ExpandedHistoryFragment : Fragment() {
    
    private var _binding: FragmentExpandedHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var expandedHistoryAdapter: ExpandedHistoryAdapter
    private var scrollPosition = 0
    
    companion object {
        fun newInstance() = ExpandedHistoryFragment()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpandedHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupExpandedHistory()
        setupWindowInsets()
        observeHistory()
        
        viewModel.loadAllHistory()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerViewExpandedHistory) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }
    
    private fun setupExpandedHistory() {
        expandedHistoryAdapter = ExpandedHistoryAdapter(
            onViewResultsClick = { historyItem ->
                viewModel.handleFragmentEvent(FragmentEvent.ViewHistoryResults(historyItem))
            }
        )

        binding.recyclerViewExpandedHistory.apply {
            adapter = expandedHistoryAdapter
            layoutManager = LinearLayoutManager(requireContext())
            
            val swipeHelper = SwipeToRevealTouchHelper(this) { position ->
                val historyItem = expandedHistoryAdapter.currentList.getOrNull(position)
                if (historyItem != null) {
                    viewModel.deleteHistoryItem(historyItem)
                }
            }
            addOnItemTouchListener(swipeHelper)
        }
    }
    
    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allHistory.collect { historyItems ->
                expandedHistoryAdapter.submitList(historyItems)
                
                if (scrollPosition > 0 && historyItems.isNotEmpty()) {
                    binding.recyclerViewExpandedHistory.post {
                        (binding.recyclerViewExpandedHistory.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(scrollPosition, 0)
                        scrollPosition = 0
                    }
                }
            }
        }
    }
    

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}