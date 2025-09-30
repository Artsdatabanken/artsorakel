package no.artsdatabanken.artsorakel.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load // Coil extension function
import no.artsdatabanken.artsorakel.databinding.ItemImageThumbnailBinding // Use ViewBinding
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.core.Constants

class ImageAdapter(
    private var isHistoricalMode: Boolean = false,
    private val onImageClick: (Uri) -> Unit,
    private val onAddImageClick: () -> Unit
) : ListAdapter<Uri, RecyclerView.ViewHolder>(UriDiffCallback()) {

    fun updateMode(historical: Boolean) {
        if (this.isHistoricalMode != historical) {
            this.isHistoricalMode = historical
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (!isHistoricalMode && position == itemCount - 1) {
            Constants.ViewTypes.VIEW_TYPE_ADD_BUTTON
        } else {
            Constants.ViewTypes.VIEW_TYPE_IMAGE
        }
    }

    override fun getItemCount(): Int {
        return if (isHistoricalMode) {
            super.getItemCount() // No add button in historical mode
        } else {
            super.getItemCount() + 1 // +1 for the add button
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            Constants.ViewTypes.VIEW_TYPE_IMAGE -> {
                val binding = ItemImageThumbnailBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ImageViewHolder(binding)
            }
            Constants.ViewTypes.VIEW_TYPE_ADD_BUTTON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_add_image_button, parent, false)
                AddButtonViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> {
                val uri = getItem(position)
                holder.bind(uri)
            }
            is AddButtonViewHolder -> {
                holder.buttonAddImage.setOnClickListener {
                    onAddImageClick()
                }
            }
        }
    }

    inner class ImageViewHolder(private val binding: ItemImageThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                if (!isHistoricalMode) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < currentList.size) {
                        onImageClick(getItem(position))
                    }
                }
            }
        }

        fun bind(uri: Uri) {
            binding.imageViewThumbnail.load(uri) {
                crossfade(true) // Optional: Add crossfade animation
                placeholder(android.R.drawable.ic_menu_gallery) // Optional: Placeholder
                error(android.R.drawable.ic_menu_report_image) // Optional: Error image
            }
        }
    }

    class AddButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val buttonAddImage: ImageButton = itemView.findViewById(R.id.buttonAddImage)
    }

    class UriDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem.toString() == newItem.toString()
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem.toString() == newItem.toString()
        }
    }
}