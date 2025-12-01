package no.artsdatabanken.artsorakel.fragments

import android.content.Intent
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.activities.MainActivity
import no.artsdatabanken.artsorakel.databinding.FragmentMainScreenBinding
import no.artsdatabanken.artsorakel.model.IdentificationHistory
import no.artsdatabanken.artsorakel.model.RssFeedItem
import no.artsdatabanken.artsorakel.model.RssCategory
import no.artsdatabanken.artsorakel.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.net.toUri

@AndroidEntryPoint
class MainScreenFragment : Fragment() {

    private var _binding: FragmentMainScreenBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAvatarForCurrentTheme()
        setupHistoryStack()
        setupRssFeedDismiss()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        setupAvatarForCurrentTheme()
        viewModel.checkAndHandleLanguageChange()
        (activity as? MainActivity)?.hideHeaderBackButton()
    }

    private fun setupAvatarForCurrentTheme() {
        binding.imageViewAvatarPicture.setImageDrawable(null)

        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                // MODE_NIGHT_FOLLOW_SYSTEM or MODE_NIGHT_AUTO_BATTERY
                val config = resources.configuration
                val currentNightMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        
        setupAvatarImage()
    }

    private fun setupAvatarImage() {
        binding.imageViewAvatarPicture.setImageResource(R.drawable.ic_avatar_photo)
    }

    private fun setupHistoryStack() {
        binding.includeHistoryStack.includeHistoryCard.root.setOnClickListener {
            expandHistoryView()
        }
        if (viewModel.recentHistory.value.isEmpty()) {
            binding.includeHistoryStack.root.visibility = View.GONE
        } else {
            binding.includeHistoryStack.root.visibility = View.VISIBLE
        }
    }

    private fun expandHistoryView() {
        (activity as? MainActivity)?.navigationManager?.showExpandedHistory()
    }

    private fun setupRssFeedDismiss() {
        binding.buttonDismissRss.setOnClickListener {
            viewModel.dismissRssFeed()
            // The UI will be updated via the observer when the new item (or null) is set
        }
    }

    // Removed isHistoryExpanded and collapseHistoryViewFromBackButton - history is now handled via overlays

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recentHistory.collect { historyList ->
                updateRecentHistoryUI(historyList)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rssFeedItem.collect { feedItem ->
                updateRssFeedUI(feedItem)
            }
        }
    }

    private fun updateRecentHistoryUI(historyList: List<IdentificationHistory>) {
        if (historyList.isEmpty()) {
            binding.includeHistoryStack.root.visibility = View.GONE
            binding.includeHistoryStack.includeHistoryCard.root.visibility = View.GONE
        } else {
            val mostRecentItem = historyList.firstOrNull()
            if (mostRecentItem != null) {
                binding.includeHistoryStack.includeHistoryCard.root.visibility = View.VISIBLE
                setupHistoryCard(binding.includeHistoryStack.includeHistoryCard.root, mostRecentItem)
                binding.includeHistoryStack.root.visibility = View.VISIBLE
            } else {
                binding.includeHistoryStack.includeHistoryCard.root.visibility = View.GONE
                binding.includeHistoryStack.root.visibility = View.GONE
            }
        }
    }
    
    private fun setupHistoryCard(cardView: View, history: IdentificationHistory) {
        val imageViewThumbnail = cardView.findViewById<ImageView>(R.id.imageViewThumbnail)
        val textViewSpeciesName = cardView.findViewById<TextView>(R.id.textViewSpeciesName)
        val textViewScientificName = cardView.findViewById<TextView>(R.id.textViewScientificName)
        val textViewTimestamp = cardView.findViewById<TextView>(R.id.textViewTimestamp)

        // Get the current language from resources
        val currentLanguage = resources.configuration.locales.get(0).language
        val vernacularName = history.getVernacularNameForLanguage(currentLanguage)

        // If no vernacular name in current language, show scientific name as header
        val useScientificAsHeader = vernacularName == null
        val displayName = if (useScientificAsHeader) {
            history.bestMatchScientificName ?: "Unknown"
        } else {
            vernacularName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
        textViewSpeciesName.text = displayName
        // Apply italic style if showing scientific name, but preserve bold
        val currentTypeface = textViewSpeciesName.typeface
        textViewSpeciesName.setTypeface(currentTypeface, if (useScientificAsHeader) Typeface.BOLD_ITALIC else Typeface.BOLD)

        // Only show scientific name below vernacular, never show Norwegian fallback
        if (!useScientificAsHeader && history.bestMatchScientificName != null) {
            // Show scientific name if we have vernacular name as header
            textViewScientificName.text = history.bestMatchScientificName
            textViewScientificName.visibility = View.VISIBLE
        } else {
            textViewScientificName.visibility = View.GONE
        }

        val dateFormat = SimpleDateFormat("dd. MMM yyyy", Locale.getDefault())
        textViewTimestamp.text = dateFormat.format(history.timestamp)

        if (history.thumbnailPaths.isNotEmpty()) {
            val thumbnailPath = history.thumbnailPaths.first()
            imageViewThumbnail.load(thumbnailPath) {
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder)
            }
            imageViewThumbnail.visibility = View.VISIBLE
        } else {
            imageViewThumbnail.visibility = View.GONE
        }
    }

    private fun updateRssFeedUI(feedItem: RssFeedItem?) {
        if (feedItem != null) {
            binding.cardRssFeed.visibility = View.VISIBLE
            binding.textViewTagline.visibility = View.GONE
            binding.textRssTitle.text = feedItem.title

            // Hide dismiss button for permanent items
            binding.buttonDismissRss.visibility = if (feedItem.isPermanent) View.GONE else View.VISIBLE

            val typedValue = TypedValue()
            val theme = requireContext().theme
            var textColor: Int

            when (feedItem.category) {
                RssCategory.DANGER -> {
                    // Set card stroke and left border color
                    theme.resolveAttribute(R.attr.alert_danger_border_primary, typedValue, true)
                    val borderColor = typedValue.data
                    binding.cardRssFeed.strokeColor = borderColor
                    binding.rssLeftBorder.setBackgroundColor(borderColor)

                    // Set background color
                    theme.resolveAttribute(R.attr.alert_danger_surface_subtle, typedValue, true)
                    binding.rssContentFrame.setBackgroundColor(typedValue.data)

                    // Set text colors
                    theme.resolveAttribute(R.attr.alert_danger_text_primary, typedValue, true)
                    textColor = typedValue.data
                    binding.textRssTitle.setTextColor(textColor)
                    binding.textRssDescription.setTextColor(textColor)
                    binding.imageRssCategory.setImageResource(R.drawable.ic_alert_info)
                    binding.imageRssCategory.setColorFilter(textColor)
                }
                RssCategory.WARNING -> {
                    // Set card stroke and left border color
                    theme.resolveAttribute(R.attr.alert_warning_border_primary, typedValue, true)
                    val borderColor = typedValue.data
                    binding.cardRssFeed.strokeColor = borderColor
                    binding.rssLeftBorder.setBackgroundColor(borderColor)

                    // Set background color
                    theme.resolveAttribute(R.attr.alert_warning_surface_subtle, typedValue, true)
                    binding.rssContentFrame.setBackgroundColor(typedValue.data)

                    // Set text colors
                    theme.resolveAttribute(R.attr.alert_warning_text_primary, typedValue, true)
                    textColor = typedValue.data
                    binding.textRssTitle.setTextColor(textColor)
                    binding.textRssDescription.setTextColor(textColor)
                    binding.imageRssCategory.setImageResource(R.drawable.ic_alert_warning)
                    binding.imageRssCategory.setColorFilter(textColor)
                }
                RssCategory.INFO -> {
                    // Set card stroke and left border color
                    theme.resolveAttribute(R.attr.alert_info_border_primary, typedValue, true)
                    val borderColor = typedValue.data
                    binding.cardRssFeed.strokeColor = borderColor
                    binding.rssLeftBorder.setBackgroundColor(borderColor)

                    // Set background color
                    theme.resolveAttribute(R.attr.alert_info_surface_subtle, typedValue, true)
                    binding.rssContentFrame.setBackgroundColor(typedValue.data)

                    // Set text colors
                    theme.resolveAttribute(R.attr.alert_info_text_primary, typedValue, true)
                    textColor = typedValue.data
                    binding.textRssTitle.setTextColor(textColor)
                    binding.textRssDescription.setTextColor(textColor)
                    binding.imageRssCategory.setImageResource(R.drawable.ic_alert_info)
                    binding.imageRssCategory.setColorFilter(textColor)
                }
            }

            // Set up description with "Read more" link and external link icon if link is available
            if (!feedItem.link.isNullOrEmpty()) {
                val descriptionText = SpannableStringBuilder(feedItem.description)
                descriptionText.append(" ") // Add space before link text

                // Get the accent color for the link
                val accentTypedValue = TypedValue()
                theme.resolveAttribute(R.attr.text_accent, accentTypedValue, true)
                val linkColor = accentTypedValue.data

                // Add "Read more" text
                val readMoreText = getString(R.string.read_more)
                val linkStartIndex = descriptionText.length
                descriptionText.append(readMoreText)
                descriptionText.append(" ")

                // Add the external link icon
                val linkIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_external_link)
                linkIcon?.let { icon ->
                    val iconSize = (14 * resources.displayMetrics.density).toInt() // 14dp
                    icon.setBounds(0, 0, iconSize, iconSize)
                    icon.colorFilter = PorterDuffColorFilter(linkColor, PorterDuff.Mode.SRC_IN)
                    val imageSpan = ImageSpan(icon, ImageSpan.ALIGN_BASELINE)
                    descriptionText.append(" ")
                    descriptionText.setSpan(imageSpan, descriptionText.length - 1, descriptionText.length, 0)
                }

                // Apply link color to "Read more" text and icon
                val linkEndIndex = descriptionText.length
                descriptionText.setSpan(ForegroundColorSpan(linkColor), linkStartIndex, linkEndIndex, 0)

                binding.textRssDescription.text = descriptionText

                binding.rssContentFrame.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, feedItem.link.toUri())
                    startActivity(intent)
                }
                binding.rssContentFrame.isClickable = true
                binding.rssContentFrame.isFocusable = true
            } else {
                binding.textRssDescription.text = feedItem.description
                binding.rssContentFrame.setOnClickListener(null)
                binding.rssContentFrame.isClickable = false
                binding.rssContentFrame.isFocusable = false
            }
        } else {
            binding.cardRssFeed.visibility = View.GONE
            binding.textViewTagline.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = MainScreenFragment()
    }
}