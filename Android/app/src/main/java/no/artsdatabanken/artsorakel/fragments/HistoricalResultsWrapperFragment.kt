package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.databinding.FragmentHistoricalResultsWrapperBinding

@AndroidEntryPoint
class HistoricalResultsWrapperFragment : Fragment() {
    
    private var _binding: FragmentHistoricalResultsWrapperBinding? = null
    private val binding get() = _binding!!
    

    companion object {
        fun newInstance() = HistoricalResultsWrapperFragment()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoricalResultsWrapperBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWindowInsets()
        setupBackButton()
        setupMenuButton()
        
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.resultsContainer, ResultsFragment.newInstance())
                .commit()
        }
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            view.updatePadding(top = systemBarsInsets.top)
            
            insets
        }
        
        ViewCompat.requestApplyInsets(binding.root)
    }
    
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.navigationManager?.hideHistoricalResults()
        }
    }
    
    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            (activity as? MainActivity)?.drawerManager?.openDrawer()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}