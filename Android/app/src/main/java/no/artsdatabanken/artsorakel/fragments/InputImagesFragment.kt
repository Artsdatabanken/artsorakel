package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import no.artsdatabanken.artsorakel.adapter.ImageAdapter
import no.artsdatabanken.artsorakel.databinding.FragmentInputImagesBinding
import no.artsdatabanken.artsorakel.core.FragmentEvent

@AndroidEntryPoint
class InputImagesFragment : Fragment() {

    private var _binding: FragmentInputImagesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private var imageAdapter: ImageAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(
            onImageClick = { croppedUri ->
                viewModel.handleFragmentEvent(FragmentEvent.ViewImage(croppedUri))
            },
            onAddImageClick = {
                viewModel.handleFragmentEvent(FragmentEvent.AddImage)
            }
        )
        binding.recyclerViewHeaderImages.adapter = imageAdapter
        binding.recyclerViewHeaderImages.layoutManager = LinearLayoutManager(
            requireContext(), 
            LinearLayoutManager.HORIZONTAL, 
            false
        )
    }

    private fun setupClickListeners() {
        binding.buttonIdentify.setOnClickListener { 
            viewModel.handleFragmentEvent(FragmentEvent.IdentifySpecies)
        }
        binding.InputsCardCloseButton.setOnClickListener { 
            viewModel.handleFragmentEvent(FragmentEvent.ResetApp)
        }
    }

    private fun observeViewModel() {
        viewModel.selectedImageUris
            .onEach { uris ->
                imageAdapter?.submitList(uris) {
                    _binding?.recyclerViewHeaderImages?.post {
                        _binding?.recyclerViewHeaderImages?.let { recyclerView ->
                            if (uris.isNotEmpty()) {
                                val targetPosition = imageAdapter?.itemCount?.minus(1) ?: 0
                                if (targetPosition >= 0) {
                                    recyclerView.smoothScrollToPosition(targetPosition)
                                }
                            } else {
                                recyclerView.scrollToPosition(0)
                            }
                        }
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageAdapter = null
        _binding = null
    }

    companion object {
        fun newInstance() = InputImagesFragment()
    }
} 