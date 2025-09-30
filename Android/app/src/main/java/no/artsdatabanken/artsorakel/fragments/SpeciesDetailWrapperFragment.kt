package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.core.Constants
import no.artsdatabanken.artsorakel.databinding.FragmentSpeciesDetailWrapperBinding
import no.artsdatabanken.artsorakel.model.ModelInfo
import javax.inject.Inject

@AndroidEntryPoint
class SpeciesDetailWrapperFragment : Fragment() {

    @Inject
    lateinit var gson: Gson

    private var _binding: FragmentSpeciesDetailWrapperBinding? = null
    private val binding get() = _binding!!
    
    companion object {
        fun newInstance(
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
        ): SpeciesDetailWrapperFragment {
            return SpeciesDetailWrapperFragment().apply {
                arguments = Bundle().apply {
                    vernacularNames?.let {
                        putString(Constants.FragmentArgs.ARG_VERNACULAR_NAMES, Gson().toJson(it))
                    }
                    putString(Constants.FragmentArgs.ARG_SCIENTIFIC_NAME, scientificName)
                    putDouble(Constants.FragmentArgs.ARG_PROBABILITY, probability)
                    putString(Constants.FragmentArgs.ARG_PICTURE_URL, pictureUrl)
                    groupNames?.let {
                        putString(Constants.FragmentArgs.ARG_GROUP_NAMES, Gson().toJson(it))
                    }
                    putString(Constants.FragmentArgs.ARG_INFO_URL, infoUrl)
                    putString(Constants.FragmentArgs.ARG_SCIENTIFIC_NAME_ID, scientificNameID)
                    modelInfo?.let {
                        putString(Constants.FragmentArgs.ARG_MODEL_INFO, Gson().toJson(it))
                    }
                    putString(Constants.FragmentArgs.ARG_REDLIST_CATEGORY, redListCategory)
                    putString(Constants.FragmentArgs.ARG_INVASIVE_CATEGORY, invasiveCategory)
                }
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeciesDetailWrapperBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWindowInsets()
        setupBackButton()
        setupMenuButton()
        
        binding.textViewTitle.text = getString(R.string.details)
        
        // Get the species data from arguments to pass to child fragment
        val vernacularNamesJson = arguments?.getString(Constants.FragmentArgs.ARG_VERNACULAR_NAMES)
        val vernacularNames: Map<String, String>? = vernacularNamesJson?.let {
            gson.fromJson(it, object : TypeToken<Map<String, String>>() {}.type)
        }
        val groupNamesJson = arguments?.getString(Constants.FragmentArgs.ARG_GROUP_NAMES)
        val groupNames: Map<String, String>? = groupNamesJson?.let {
            gson.fromJson(it, object : TypeToken<Map<String, String>>() {}.type)
        }
        val modelInfoJson = arguments?.getString(Constants.FragmentArgs.ARG_MODEL_INFO)
        val modelInfo: ModelInfo? = modelInfoJson?.let {
            gson.fromJson(it, ModelInfo::class.java)
        }
        val scientificName = arguments?.getString(Constants.FragmentArgs.ARG_SCIENTIFIC_NAME)

        if (savedInstanceState == null) {
            val fragment = SpeciesDetailFragment.newInstance(
                vernacularNames,
                scientificName,
                arguments?.getDouble(Constants.FragmentArgs.ARG_PROBABILITY, 0.0) ?: 0.0,
                arguments?.getString(Constants.FragmentArgs.ARG_PICTURE_URL),
                groupNames,
                arguments?.getString(Constants.FragmentArgs.ARG_INFO_URL),
                arguments?.getString(Constants.FragmentArgs.ARG_SCIENTIFIC_NAME_ID),
                modelInfo,
                arguments?.getString(Constants.FragmentArgs.ARG_REDLIST_CATEGORY),
                arguments?.getString(Constants.FragmentArgs.ARG_INVASIVE_CATEGORY)
            )
            childFragmentManager.beginTransaction()
                .replace(R.id.detailsContainer, fragment)
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
            (activity as? MainActivity)?.navigationManager?.hideSpeciesDetailFragment()
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