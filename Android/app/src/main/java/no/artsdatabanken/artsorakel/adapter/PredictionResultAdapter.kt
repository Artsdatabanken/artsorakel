// File: PredictionResultAdapter.kt
package no.artsdatabanken.artsorakel.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import no.artsdatabanken.artsorakel.databinding.ListItemPredictionBinding
import no.artsdatabanken.artsorakel.model.PredictionResult
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.core.SpeciesDisplayData
import java.util.Locale

class PredictionResultAdapter(
    private val onItemClick: (PredictionResult) -> Unit = {}
) : ListAdapter<PredictionResult, PredictionResultAdapter.PredictionViewHolder>(PredictionDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        // Use a stable hash of the unique ID
        return getItem(position).id?.hashCode()?.toLong() ?: RecyclerView.NO_ID
    }

    // ViewHolder holds references to the views in list_item_prediction.xml
    inner class PredictionViewHolder(private val binding: ListItemPredictionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: PredictionResult) {
            // Use the centralized display data logic - don't show Norwegian fallback in results list
            val displayData = SpeciesDisplayData.fromPredictionResult(result, binding.root.context, showNorwegianFallback = false)

            // Apply the display data to views
            binding.textViewVernacularName.text = displayData.headerText
            val currentTypeface = binding.textViewVernacularName.typeface
            binding.textViewVernacularName.setTypeface(currentTypeface, displayData.headerTypeface)

            // In results list, just show scientific name, no Norwegian fallback
            binding.textViewScientificName.text = displayData.scientificNameText
            binding.textViewScientificName.visibility = displayData.scientificNameVisibility
            
            binding.textViewGroupName.text = displayData.groupText

            // Load image using Coil with group-specific placeholder
            binding.imageViewPrediction.load(result.pictureUrl) {
                placeholder(displayData.placeholderRes)
                error(displayData.placeholderRes)
                crossfade(true)
            }

            // Set probability on the custom gauge view
            binding.gaugeCertainty.setProbability(result.probability)

            // Set click listener for the chevron arrow to open detail view
            binding.viewPrediction.setOnClickListener {
                onItemClick(result)
            }
        }
    }

    // Creates new ViewHolders
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredictionViewHolder {
        val binding = ListItemPredictionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PredictionViewHolder(binding)
    }

    // Updates the contents of a ViewHolder
    override fun onBindViewHolder(holder: PredictionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

// DiffUtil helps RecyclerView efficiently update the list
class PredictionDiffCallback : DiffUtil.ItemCallback<PredictionResult>() {
    override fun areItemsTheSame(oldItem: PredictionResult, newItem: PredictionResult): Boolean {
        return oldItem.id == newItem.id // Use the unique ID
    }

    override fun areContentsTheSame(oldItem: PredictionResult, newItem: PredictionResult): Boolean {
        return oldItem == newItem // Data class checks all fields
    }
}