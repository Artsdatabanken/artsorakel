package no.artsdatabanken.artsorakel.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import no.artsdatabanken.artsorakel.R
import no.artsdatabanken.artsorakel.model.FaqItem

class FaqAdapter : ListAdapter<FaqItem, FaqAdapter.FaqViewHolder>(FaqDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faq, parent, false)
        return FaqViewHolder(view)
    }

    override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FaqViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val questionText: TextView = itemView.findViewById(R.id.questionText)
        private val answerText: TextView = itemView.findViewById(R.id.answerText)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        private val rootLayout: ConstraintLayout = itemView.findViewById(R.id.rootLayout)

        fun bind(item: FaqItem) {
            questionText.text = item.question
            answerText.text = item.answer
            
            // Set initial visibility
            answerText.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            expandIcon.rotation = if (item.isExpanded) 180f else 0f
            
            // Handle click to expand/collapse
            rootLayout.setOnClickListener {
                item.isExpanded = !item.isExpanded
                
                // Animate the transition
                val transition = AutoTransition().apply {
                    duration = 200
                }
                TransitionManager.beginDelayedTransition(itemView.parent as ViewGroup, transition)
                
                // Update visibility and icon
                answerText.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
                expandIcon.animate()
                    .rotation(if (item.isExpanded) 180f else 0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    class FaqDiffCallback : DiffUtil.ItemCallback<FaqItem>() {
        override fun areItemsTheSame(oldItem: FaqItem, newItem: FaqItem): Boolean {
            return oldItem.question == newItem.question
        }

        override fun areContentsTheSame(oldItem: FaqItem, newItem: FaqItem): Boolean {
            return oldItem == newItem
        }
    }
}