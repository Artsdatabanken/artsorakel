package no.artsdatabanken.artsorakel.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.model.IdentificationHistory
import java.text.SimpleDateFormat
import java.util.Locale

class ExpandedHistoryAdapter(
    private val onViewResultsClick: (IdentificationHistory) -> Unit = {},
) : ListAdapter<IdentificationHistory, ExpandedHistoryAdapter.ExpandedHistoryViewHolder>(ExpandedHistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpandedHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_stack_card_swipeable, parent, false)
        return ExpandedHistoryViewHolder(view, onViewResultsClick)
    }

    override fun onBindViewHolder(holder: ExpandedHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }



    class ExpandedHistoryViewHolder(
        itemView: View,
        private val onViewResultsClick: (IdentificationHistory) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        private val imageViewThumbnail: ImageView = itemView.findViewById(R.id.imageViewThumbnail)
        private val textViewSpeciesName: TextView = itemView.findViewById(R.id.textViewSpeciesName)
        private val textViewScientificName: TextView = itemView.findViewById(R.id.textViewScientificName)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        private val imageViewArrow: ImageView = itemView.findViewById(R.id.imageViewArrow)
        private val cardView: View = itemView.findViewById(R.id.cardView)

        private var currentHistory: IdentificationHistory? = null

        init {
            // Make the entire card clickable to view results
            cardView.setOnClickListener {
                currentHistory?.let { onViewResultsClick(it) }
            }
            
            // Also keep the chevron clickable for visual feedback
            imageViewArrow.setOnClickListener {
                currentHistory?.let { onViewResultsClick(it) }
            }
        }

        fun bind(history: IdentificationHistory) {
            currentHistory = history

            // Set consistent elevation for all cards in expanded view
            itemView.findViewById<androidx.cardview.widget.CardView>(R.id.cardView).cardElevation = 2f

            // Get the current language from resources
            val currentLanguage = itemView.context.resources.configuration.locales.get(0).language
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

            // Set timestamp
            val dateFormat = SimpleDateFormat("dd. MMM yyyy", Locale.getDefault())
            textViewTimestamp.text = dateFormat.format(history.timestamp)

            // Load thumbnail image
            if (history.thumbnailPaths.isNotEmpty()) {
                val thumbnailPath = history.thumbnailPaths.first()
                imageViewThumbnail.load(thumbnailPath) {
                    placeholder(R.drawable.ic_image_placeholder)
                    error(R.drawable.ic_image_placeholder)
                    crossfade(true)
                }
            } else {
                imageViewThumbnail.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
    }

    class ExpandedHistoryDiffCallback : DiffUtil.ItemCallback<IdentificationHistory>() {
        override fun areItemsTheSame(oldItem: IdentificationHistory, newItem: IdentificationHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: IdentificationHistory, newItem: IdentificationHistory): Boolean {
            return oldItem == newItem
        }
    }
} 