package no.artsdatabanken.artsorakel.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.util.TypedValue
import no.artsdatabanken.artsorakel.utils.Logger
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import coil.load
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil.transform.CircleCropTransformation
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import no.artsdatabanken.artsorakel.service.ImageCacheService
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import javax.inject.Inject
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.adapter.ImageCarouselAdapter
import no.artsdatabanken.artsorakel.databinding.FragmentSpeciesDetailBinding
import no.artsdatabanken.artsorakel.core.Constants
import no.artsdatabanken.artsorakel.core.SpeciesDisplayData
import no.artsdatabanken.artsorakel.model.ModelInfo
import no.artsdatabanken.artsorakel.repository.SpeciesRepository
import no.artsdatabanken.artsorakel.service.ImageProcessingService
import androidx.core.net.toUri
import kotlin.math.roundToInt

@AndroidEntryPoint
class SpeciesDetailFragment : Fragment() {

    private var _binding: FragmentSpeciesDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var carouselAdapter: ImageCarouselAdapter
    private val indicatorDots = mutableListOf<ImageView>()

    @Inject
    lateinit var imageCacheService: ImageCacheService

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var speciesRepository: SpeciesRepository

    @Inject
    lateinit var imageProcessingService: ImageProcessingService

    private var vernacularNames: Map<String, String>? = null
    private var scientificName: String? = null
    private var probability: Double = 0.0
    private var pictureUrl: String? = null
    private var groupNames: Map<String, String>? = null
    private var infoUrl: String? = null
    private var scientificNameID: String? = null
    private var modelInfo: ModelInfo? = null
    private var redListCategory: String? = null
    private var invasiveCategory: String? = null


    companion object {

        fun newInstance(
            vernacularNames: Map<String, String>?,
            scientificName: String?,
            probability: Double,
            pictureUrl: String?,
            groupNames: Map<String, String>?,
            infoUrl: String?,
            scientificNameID: String?,
            modelInfo: ModelInfo? = null,
            redListCategory: String? = null,
            invasiveCategory: String? = null
        ): SpeciesDetailFragment {
            return SpeciesDetailFragment().apply {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val vernacularNamesJson = it.getString(Constants.FragmentArgs.ARG_VERNACULAR_NAMES)
            vernacularNames = vernacularNamesJson?.let { json ->
                gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
            }
            scientificName = it.getString(Constants.FragmentArgs.ARG_SCIENTIFIC_NAME)
            probability = it.getDouble(Constants.FragmentArgs.ARG_PROBABILITY, 0.0)
            pictureUrl = it.getString(Constants.FragmentArgs.ARG_PICTURE_URL)
            val groupNamesJson = it.getString(Constants.FragmentArgs.ARG_GROUP_NAMES)
            groupNames = groupNamesJson?.let { json ->
                gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
            }
            infoUrl = it.getString(Constants.FragmentArgs.ARG_INFO_URL)
            scientificNameID = it.getString(Constants.FragmentArgs.ARG_SCIENTIFIC_NAME_ID)
            val modelInfoJson = it.getString(Constants.FragmentArgs.ARG_MODEL_INFO)
            modelInfo = modelInfoJson?.let { json ->
                gson.fromJson(json, ModelInfo::class.java)
            }
            redListCategory = it.getString(Constants.FragmentArgs.ARG_REDLIST_CATEGORY)
            invasiveCategory = it.getString(Constants.FragmentArgs.ARG_INVASIVE_CATEGORY)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeciesDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Logger.d("SpeciesDetail", "onViewCreated called")
        Logger.d("SpeciesDetail", "Fragment arguments - scientificNameID: $scientificNameID")
        
        setupWindowInsets()
        setupUI()
        setupImageCarousel()
        loadSpeciesData()
        observeInputImages()
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
                    resources.getDimensionPixelSize(R.dimen.species_detail_button_margin)
                } catch (_: Exception) {
                    (24 * resources.displayMetrics.density).toInt()
                }
                
                val layoutParams = binding.buttonContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.bottomMargin = originalMargin + bottomInset
                binding.buttonContainer.layoutParams = layoutParams
                
                view.updatePadding(
                    left = leftInset,
                    right = rightInset
                )
                
                Logger.d("WindowInsets",
                    "SpeciesDetailFragment - Applied insets - bottom: $bottomInset, left: $leftInset, right: $rightInset")
                
                windowInsets
            }
        } else {
            val originalMargin = try {
                resources.getDimensionPixelSize(R.dimen.species_detail_button_margin)
            } catch (_: Exception) {
                (24 * resources.displayMetrics.density).toInt()
            }
            
            val layoutParams = binding.buttonContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            layoutParams.bottomMargin = originalMargin
            binding.buttonContainer.layoutParams = layoutParams
        }
    }

    private fun setupUI() {
        // Set up Read More link with dynamic text
        if (!infoUrl.isNullOrBlank()) {
            val context = requireContext()
            val languageManager = no.artsdatabanken.artsorakel.manager.LanguageManager(context)
            val currentLanguage = languageManager.getCurrentLanguageTag()
            val vernacularForLanguage = vernacularNames?.get(currentLanguage)

            val displayName = if (vernacularForLanguage == null) {
                // Use scientific name in italics
                scientificName?.let { "<i>$it</i>" } ?: getString(R.string.unknown_species)
            } else {
                vernacularForLanguage
            }

            val readMoreText = getString(R.string.read_more_detailed, displayName)
            binding.textViewReadMore.text = Html.fromHtml(readMoreText, FROM_HTML_MODE_LEGACY)
            binding.textViewReadMore.visibility = View.VISIBLE
            binding.textViewReadMore.setOnClickListener {
                openInfoUrl()
            }
        } else {
            binding.textViewReadMore.visibility = View.GONE
        }

        // Set up Report link with dynamic text - only show for Norway observations
        val isNorwayObservation = modelInfo?.country == "NO" || modelInfo?.country == "Norway"
        val hasValidId = extractIdAfterColon(scientificNameID) != null

        if (hasValidId && isNorwayObservation) {
            binding.textViewReport.visibility = View.VISIBLE
            binding.textViewReport.setOnClickListener {
                showReportDialog()
            }
        } else {
            binding.textViewReport.visibility = View.GONE
        }
    }

    private fun getDisplayName(): String {
        // Get the appropriate display name based on current language
        val context = requireContext()
        val languageManager = no.artsdatabanken.artsorakel.manager.LanguageManager(context)
        val currentLanguage = languageManager.getCurrentLanguageTag()

        return vernacularNames?.get(currentLanguage)
            ?: scientificName
            ?: getString(R.string.unknown_species)
    }

    private fun openInfoUrl() {
        infoUrl?.let { url ->
            if (url.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showReportDialog() {
        extractIdAfterColon(scientificNameID)?.let { extractedId ->
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.report_dialog_title))
                .setMessage(getString(R.string.report_dialog_message))
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(getString(R.string.report_dialog_continue)) { dialog, _ ->
                    dialog.dismiss()
                    uploadImagesAndReport(extractedId)
                }
                .show()
        }
    }

    private fun uploadImagesAndReport(scientificNameId: String) {
        // Use selectedImagePairs to get the actual user's cropped images (not historical thumbnails)
        val imagePairs = viewModel.selectedImagePairs.value
        if (imagePairs.isEmpty()) {
            // No images to upload, open URL without image reference
            openReportUrl(scientificNameId, null, null)
            return
        }

        // Extract cropped URIs from the image pairs
        val croppedUris = imagePairs.map { it.croppedUri }

        // Show loading state
        binding.textViewReport.isEnabled = false
        val originalText = binding.textViewReport.text
        binding.textViewReport.text = getString(R.string.report_uploading)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Process images for upload (resize and compress)
                val (imageDataList, filenames, _) = imageProcessingService.processMultipleImages(
                    context = requireContext(),
                    uris = croppedUris,
                    targetWidth = 1024,
                    targetHeight = 1024,
                    quality = 85
                )

                if (imageDataList.isEmpty()) {
                    // All images failed to process, open URL without image reference
                    Logger.e("SpeciesDetail", "All images failed to process for upload")
                    openReportUrl(scientificNameId, null, null)
                    return@launch
                }

                // Upload images to server
                val saveResult = speciesRepository.saveImagesForReport(imageDataList, filenames)

                saveResult.fold(
                    onSuccess = { response ->
                        Logger.d("SpeciesDetail", "Images saved successfully: id=${response.id}")
                        openReportUrl(scientificNameId, response.id, response.password)
                    },
                    onFailure = { exception ->
                        Logger.e("SpeciesDetail", "Failed to save images", exception)
                        Toast.makeText(context, getString(R.string.report_upload_failed), Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Logger.e("SpeciesDetail", "Error during image upload", e)
                Toast.makeText(context, getString(R.string.report_upload_failed), Toast.LENGTH_LONG).show()
            } finally {
                // Restore button state
                binding.textViewReport.text = originalText
                binding.textViewReport.isEnabled = true
            }
        }
    }

    private fun openReportUrl(scientificNameId: String, imageId: String?, password: String?) {
        // Build the report URL with all metadata
        // Format: https://mobil.artsobservasjoner.no/#/orakel?scientificnameid=<id>&meta=from%3Dorakel%7Cplatform%3Dandroid%7Cpercentage%3D<prob>&id=<imageId>&password=<password>

        val probabilityPercent = (probability * 100).roundToInt()

        val urlBuilder = StringBuilder("https://mobil.artsobservasjoner.no/#/orakel")
        urlBuilder.append("?scientificnameid=$scientificNameId")
        urlBuilder.append("&meta=from%3Dorakel%7Cplatform%3Dandroid%7Cpercentage%3D$probabilityPercent")

        // Add image reference if available
        if (imageId != null && password != null) {
            urlBuilder.append("&id=$imageId")
            urlBuilder.append("&password=$password")
        }

        val reportUrl = urlBuilder.toString()
        Logger.d("SpeciesDetail", "Opening report URL: $reportUrl")

        try {
            val intent = Intent(Intent.ACTION_VIEW, reportUrl.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open report link", Toast.LENGTH_SHORT).show()
        }
    }



    private fun setupImageCarousel() {
        carouselAdapter = ImageCarouselAdapter()
        binding.viewPagerImageCarousel.adapter = carouselAdapter
        
        binding.viewPagerImageCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicatorDots(position)
            }
        })
    }

    private fun observeInputImages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedImageUris.collect { uris ->
                    if (uris.isNotEmpty()) {
                        carouselAdapter.submitList(uris)
                        setupIndicatorDots(uris.size)
                        binding.indicatorCard.visibility = if (uris.size > 1) View.VISIBLE else View.GONE
                    } else {
                        binding.carouselContainer.visibility = View.GONE
                        binding.indicatorCard.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupIndicatorDots(count: Int) {
        binding.indicatorContainer.removeAllViews()
        indicatorDots.clear()

        if (count <= 1) return

        for (i in 0 until count) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_indicator_dot))
                val typedValue = TypedValue()
                requireContext().theme.resolveAttribute(R.attr.text_primary, typedValue, true)
                imageTintList = ColorStateList.valueOf(typedValue.data)
                scaleX = 0.75f
                scaleY = 0.75f
            }
            binding.indicatorContainer.addView(dot)
            indicatorDots.add(dot)
        }
        
        if (indicatorDots.isNotEmpty()) {
            updateIndicatorDots(0)
        }
    }

    private fun updateIndicatorDots(selectedPosition: Int) {
        val typedValue = TypedValue()
        indicatorDots.forEachIndexed { index, dot ->
            if (index == selectedPosition) {
                requireContext().theme.resolveAttribute(R.attr.surface_accent, typedValue, true)
                dot.imageTintList = ColorStateList.valueOf(typedValue.data)
                dot.scaleX = 1.0f
                dot.scaleY = 1.0f
            } else {
                requireContext().theme.resolveAttribute(R.attr.text_primary, typedValue, true)
                dot.imageTintList = ColorStateList.valueOf(typedValue.data)
                dot.scaleX = 0.75f
                dot.scaleY = 0.75f
            }
        }
    }

    private fun loadSpeciesData() {
        val displayData = SpeciesDisplayData.fromSpeciesDetail(
            scientificName, probability, vernacularNames, groupNames, requireContext()
        )

        binding.textViewVernacularName.text = displayData.headerText
        val currentTypeface = binding.textViewVernacularName.typeface
        binding.textViewVernacularName.setTypeface(currentTypeface, displayData.headerTypeface)

        binding.textViewScientificName.text = displayData.scientificNameText
        binding.textViewScientificName.visibility = displayData.scientificNameVisibility

        binding.textViewGroupName.text = displayData.groupText

        // Check if location was used from ViewModel (simpler than passing through all layers)
        val wasLocationUsed = viewModel.wasLocationUsed()
        val certaintyStringRes = if (wasLocationUsed) R.string.certainty_text_with_location else R.string.certainty_text
        val text: String = getString(certaintyStringRes, displayData.certaintyPercentage, displayData.certaintyTextParameter)
        val styledText: Spanned = Html.fromHtml(text, FROM_HTML_MODE_LEGACY)
        binding.textViewCertainty.text = styledText

        pictureUrl?.let { url ->
            viewLifecycleOwner.lifecycleScope.launch {
                val cachedBitmap = imageCacheService.getImage(url, ImageCacheService.CacheType.SPECIES_IMAGE)
                
                if (cachedBitmap != null) {
                    binding.imageViewSpeciesProfile.load(cachedBitmap) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                    }
                } else {
                    binding.imageViewSpeciesProfile.load(url) {
                        placeholder(displayData.placeholderRes)
                        error(displayData.placeholderRes)
                        crossfade(true)
                        transformations(CircleCropTransformation())
                    }
                }
            }
        } ?: run {
            binding.imageViewSpeciesProfile.load(displayData.placeholderRes) {
                transformations(CircleCropTransformation())
            }
        }

        // Display category badges
        displayCategoryBadges()

        loadDistributionMap()
    }

    private fun displayCategoryBadges() {
        binding.categoryBadgesContainer.removeAllViews()

        var hasBadges = false

        // Add red list badge if present
        redListCategory?.let { category ->
            hasBadges = true
            val categoryLower = category.lowercase()
            val stringResId = resources.getIdentifier("redlist_$categoryLower", "string", requireContext().packageName)
            val colorResId = resources.getIdentifier("redlist_$categoryLower", "color", requireContext().packageName)

            if (stringResId != 0 && colorResId != 0) {
                addCategoryBadge(
                    category.uppercase(),
                    getString(stringResId),
                    colorResId
                )
            }
        }

        // Add invasive badge if present
        invasiveCategory?.let { category ->
            hasBadges = true
            val categoryLower = category.lowercase()
            val stringResId = resources.getIdentifier("invasive_$categoryLower", "string", requireContext().packageName)
            val colorResId = resources.getIdentifier("invasive_$categoryLower", "color", requireContext().packageName)

            if (stringResId != 0 && colorResId != 0) {
                addCategoryBadge(
                    category.uppercase(),
                    getString(stringResId),
                    colorResId
                )
            }
        }

        binding.categoryBadgesContainer.visibility = if (hasBadges) View.VISIBLE else View.GONE
    }

    private fun addCategoryBadge(code: String, name: String, colorResId: Int) {
        val badgeView = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_category_badge, binding.categoryBadgesContainer, false)

        val badge = badgeView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.categoryBadge)
        val codeText = badgeView.findViewById<TextView>(R.id.categoryCode)
        val nameText = badgeView.findViewById<TextView>(R.id.categoryName)

        codeText.text = code.uppercase()
        nameText.text = name

        // Set background color
        val color = ContextCompat.getColor(requireContext(), colorResId)
        badge.setCardBackgroundColor(color)

        binding.categoryBadgesContainer.addView(badgeView)
    }

    private fun extractIdAfterColon(fullId: String?): String? {
        return fullId?.substringAfter(":", "")?.takeIf { it.isNotBlank() }
    }
    
        private fun loadDistributionMap() {
        Logger.d("SpeciesDetail", "loadDistributionMap called")
        Logger.d("SpeciesDetail", "scientificNameID: $scientificNameID")
        
        val extractedId = extractIdAfterColon(scientificNameID)
        Logger.d("SpeciesDetail", "Extracted ID after colon: $extractedId")
        
        extractedId?.let { nameId ->
            if (nameId.isNotBlank()) {
                val distributionUrl = "https://artskart.artsdatabanken.no/appapi/api/raster/distribution/" +
                    "?BBOX=-350770,6400000,1100000,9000000&height=800&width=500&ScientificNameId=$nameId"
                Logger.d("SpeciesDetail", "Loading distribution map from: $distributionUrl")

                binding.let { b ->
                    b.textViewDistributionTitle.visibility = View.VISIBLE
                    b.distributionMapCard.visibility = View.VISIBLE
                    b.progressBarDistributionMap.visibility = View.VISIBLE
                }
                
                viewLifecycleOwner.lifecycleScope.launch {
                    val cachedBitmap = imageCacheService.getImage(distributionUrl, ImageCacheService.CacheType.DISTRIBUTION_MAP)
                    
                    if (cachedBitmap != null) {
                        Logger.d("SpeciesDetail", "Distribution map loaded from cache")
                        binding.progressBarDistributionMap.visibility = View.GONE
                        binding.imageViewDistributionMap.visibility = View.VISIBLE
                        binding.imageViewDistributionMap.load(cachedBitmap) {
                            crossfade(true)
                            allowHardware(false)
                        }
                    } else {
                        binding.imageViewDistributionMap.load(distributionUrl) {
                            crossfade(true)
                            allowHardware(false)
                            listener(
                                onStart = {
                                    Logger.d("SpeciesDetail", "Distribution map loading started")
                                    binding.progressBarDistributionMap.visibility = View.VISIBLE
                                },
                                onSuccess = { _, result ->
                                    Logger.d("SpeciesDetail", "Distribution map loaded successfully")
                                    binding.progressBarDistributionMap.visibility = View.GONE
                                    binding.imageViewDistributionMap.visibility = View.VISIBLE
                                },
                                onError = { _, error ->
                                    Logger.e("SpeciesDetail", "Distribution map load failed", error.throwable)
                                    Logger.e("SpeciesDetail", "Error details: ${error.throwable.message}")
                                    binding.progressBarDistributionMap.visibility = View.GONE
                                    binding.distributionMapCard.visibility = View.GONE
                                    binding.textViewDistributionTitle.visibility = View.GONE
                                }
                            )
                        }
                    }
                }
            } else {
                Logger.d("SpeciesDetail", "Extracted ID is blank")
                hideDistributionSection()
            }
        } ?: run {
            Logger.d("SpeciesDetail", "Could not extract ID from scientificNameID")
            hideDistributionSection()
        }
    }
    
    private fun hideDistributionSection() {
        binding.let { b ->
            b.textViewDistributionTitle.visibility = View.GONE
            b.distributionMapCard.visibility = View.GONE
            b.progressBarDistributionMap.visibility = View.GONE
            b.imageViewDistributionMap.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 