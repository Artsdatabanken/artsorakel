package no.artsdatabanken.artsorakel.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.adapter.FaqAdapter
import no.artsdatabanken.artsorakel.databinding.FragmentFaqBinding
import no.artsdatabanken.artsorakel.manager.LanguageManager
import no.artsdatabanken.artsorakel.model.FaqItem
import no.artsdatabanken.artsorakel.model.FaqData
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class FaqFragment : Fragment() {

    private var _binding: FragmentFaqBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var languageManager: LanguageManager

    private lateinit var faqAdapter: FaqAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFaqBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackButton()
        setupMenuButton()
        setupRecyclerView()
        loadFaqItems()
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.navigationManager?.navigateBackFromSettings()
        }
    }

    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            (activity as? MainActivity)?.drawerManager?.toggleDrawer()
        }
    }

    private fun setupRecyclerView() {
        faqAdapter = FaqAdapter()
        binding.faqRecyclerView.apply {
            adapter = faqAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun loadFaqItems() {
        val faqItems = loadFaqFromJson()
        faqAdapter.submitList(faqItems)
    }

    private fun loadFaqFromJson(): List<FaqItem> {
        return try {
            val languageTag = when (languageManager.getCurrentLanguage().languageTag) {
                "nb" -> "nb"
                "nn" -> "nn"
                "nl" -> "nl"
                "es" -> "es"
                "sv" -> "sv"
                else -> "en"
            }

            val jsonFileName = "faq_$languageTag.json"

            requireContext().assets.open(jsonFileName).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val gson = Gson()
                    val faqData = gson.fromJson(reader, FaqData::class.java)
                    faqData.items
                }
            }
        } catch (_: Exception) {
            try {
                requireContext().assets.open("faq_en.json").use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        val gson = Gson()
                        val faqData = gson.fromJson(reader, FaqData::class.java)
                        faqData.items
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}