package no.artsdatabanken.artsorakel.adapter

import android.animation.ObjectAnimator
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import no.artsdatabanken.artsorakel.R
import kotlin.math.abs

/**
 * Custom touch handler for swipe-to-reveal delete functionality.
 * This replaces ItemTouchHelper for better control over partial swipes.
 */
class SwipeToRevealTouchHelper(
    private val recyclerView: RecyclerView,
    private val onDeleteClick: (position: Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val gestureDetector = GestureDetector(recyclerView.context, SwipeGestureListener())
    private var swipedViewHolder: RecyclerView.ViewHolder? = null
    private var isSwipeEnabled = true
    
    // Constants for swipe behavior
    private val maxSwipeDistance = 200f // Maximum swipe distance in dp (120dp button + generous padding for easy clicking)
    private val swipeThreshold = 100f // Minimum distance to trigger reveal

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (!isSwipeEnabled) return false
        
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                val child = rv.findChildViewUnder(e.x, e.y)
                if (child != null) {
                    val viewHolder = rv.getChildViewHolder(child)
                    val cardView = viewHolder.itemView.findViewById<MaterialCardView>(R.id.cardView)
                    
                    // Check if card is swiped and touch is on delete button
                    if (cardView.translationX > 0) {
                        val deleteButton = viewHolder.itemView.findViewById<FloatingActionButton>(R.id.delete_item_fab)
                        if (deleteButton != null) {
                            val deleteBounds = intArrayOf(0, 0)
                            deleteButton.getLocationOnScreen(deleteBounds)
                            
                            val eventXGlobal = e.rawX
                            val eventYGlobal = e.rawY
                            
                            if (eventXGlobal >= deleteBounds[0] && 
                                eventXGlobal <= deleteBounds[0] + deleteButton.width &&
                                eventYGlobal >= deleteBounds[1] && 
                                eventYGlobal <= deleteBounds[1] + deleteButton.height) {
                                
                                // Touch is on the delete button - handle it
                                onDeleteClick(viewHolder.bindingAdapterPosition)
                                closeSwipedItem()
                                return true
                            }
                        }
                    }
                    
                    // Check if touch is on the chevron - if so, don't intercept
                    val chevron = viewHolder.itemView.findViewById<ImageView>(R.id.imageViewArrow)
                    if (chevron != null) {
                        val chevronBounds = intArrayOf(0, 0)
                        chevron.getLocationOnScreen(chevronBounds)
                        
                        val eventXGlobal = e.rawX
                        val eventYGlobal = e.rawY
                        
                        if (eventXGlobal >= chevronBounds[0] && 
                            eventXGlobal <= chevronBounds[0] + chevron.width &&
                            eventYGlobal >= chevronBounds[1] && 
                            eventYGlobal <= chevronBounds[1] + chevron.height) {
                            
                            // Touch is on the chevron - don't intercept, allow it to handle click
                            return false
                        }
                    }
                    
                    // Close any currently swiped item if touching a different item
                    if (swipedViewHolder != null && swipedViewHolder != viewHolder) {
                        closeSwipedItem()
                    }
                }
                
                // Start tracking the gesture
                gestureDetector.onTouchEvent(e)
                return false // Don't intercept yet, just track
            }
            MotionEvent.ACTION_MOVE -> {
                // Only intercept if we've determined this is a horizontal swipe
                if (isHorizontalSwipe) {
                    return true
                }
                // Let gesture detector determine if this should be a horizontal swipe
                gestureDetector.onTouchEvent(e)
                return isHorizontalSwipe
            }
            else -> return false
        }
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (isHorizontalSwipe) {
            gestureDetector.onTouchEvent(e)
            
            // Handle touch up events for snap-to behavior
            if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                handleTouchUp()
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Allow parent to control touch interception
    }

    private var currentViewHolder: RecyclerView.ViewHolder? = null
    private var isHorizontalSwipe = false

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private var initialX = 0f
        private var initialY = 0f

        override fun onDown(e: MotionEvent): Boolean {
            initialX = e.x
            initialY = e.y
            this@SwipeToRevealTouchHelper.isHorizontalSwipe = false
            
            val child = recyclerView.findChildViewUnder(e.x, e.y)
            this@SwipeToRevealTouchHelper.currentViewHolder = child?.let { recyclerView.getChildViewHolder(it) }
            
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null || this@SwipeToRevealTouchHelper.currentViewHolder == null) return false

            val deltaX = e2.x - initialX
            val deltaY = e2.y - initialY

            // Only determine swipe direction once we've moved enough
            if (!this@SwipeToRevealTouchHelper.isHorizontalSwipe) {
                // Need significant movement to determine direction
                if (abs(deltaX) < 15 && abs(deltaY) < 15) {
                    return false
                }
                
                // Strongly prioritize vertical scrolling
                if (abs(deltaY) > 10) {
                    // Any significant vertical movement = vertical scroll
                    return false
                }
                
                // Only consider horizontal swipe if movement is strongly horizontal
                // and it's a right swipe with significant distance
                if (deltaX > 30 && abs(deltaX) > abs(deltaY) * 2) {
                    this@SwipeToRevealTouchHelper.isHorizontalSwipe = true
                    recyclerView.requestDisallowInterceptTouchEvent(true)
                } else if (swipedViewHolder == this@SwipeToRevealTouchHelper.currentViewHolder && deltaX < -20) {
                    // Allow closing an already swiped item with left swipe
                    this@SwipeToRevealTouchHelper.isHorizontalSwipe = true
                    recyclerView.requestDisallowInterceptTouchEvent(true)
                }
            }

            if (this@SwipeToRevealTouchHelper.isHorizontalSwipe) {
                val cardView = this@SwipeToRevealTouchHelper.currentViewHolder!!.itemView.findViewById<MaterialCardView>(R.id.cardView)

                // Check if this is the currently swiped item
                val isCurrentlySwipedItem = swipedViewHolder == this@SwipeToRevealTouchHelper.currentViewHolder
                
                if (deltaX > 0) { // Right swipe - reveal delete button
                    val translation = deltaX.coerceAtMost(maxSwipeDistance)
                    cardView.translationX = translation
                    return true
                } else if (deltaX < 0 && isCurrentlySwipedItem) { // Left swipe on already swiped item - close it
                    // When swiping left on an already revealed item, start from maxSwipeDistance and reduce
                    val translation = (maxSwipeDistance + deltaX).coerceAtLeast(0f)
                    cardView.translationX = translation

                    return true
                }
            }

            return false
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            return this@SwipeToRevealTouchHelper.isHorizontalSwipe
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val child = recyclerView.findChildViewUnder(e.x, e.y)
            if (child != null) {
                val viewHolder = recyclerView.getChildViewHolder(child)
                val cardView = viewHolder.itemView.findViewById<MaterialCardView>(R.id.cardView)
                
                // Check if delete button was clicked (when card is swiped)
                if (cardView.translationX > 0) {
                    val deleteCard = viewHolder.itemView.findViewById<FloatingActionButton>(R.id.delete_item_fab)
                    val deleteCardBounds = intArrayOf(0, 0)
                    deleteCard.getLocationOnScreen(deleteCardBounds)
                    
                    val eventXGlobal = e.rawX
                    val eventYGlobal = e.rawY
                    
                    if (eventXGlobal >= deleteCardBounds[0] && 
                        eventXGlobal <= deleteCardBounds[0] + deleteCard.width &&
                        eventYGlobal >= deleteCardBounds[1] && 
                        eventYGlobal <= deleteCardBounds[1] + deleteCard.height) {
                        
                        // Delete button clicked
                        onDeleteClick(viewHolder.bindingAdapterPosition)
                        closeSwipedItem()
                        return true
                    }
                }
                
                // Regular item click - close any swiped item first
                if (swipedViewHolder != null) {
                    closeSwipedItem()
                    return true
                }
            }
            return false
        }
    }

    fun handleTouchUp() {
        if (currentViewHolder != null && isHorizontalSwipe) {
            val cardView = currentViewHolder!!.itemView.findViewById<MaterialCardView>(R.id.cardView)
            val currentTranslation = cardView.translationX
            val isCurrentlySwipedItem = swipedViewHolder == currentViewHolder
            
            if (currentTranslation > swipeThreshold) {
                // Snap to revealed position
                animateToPosition(cardView, maxSwipeDistance)
                swipedViewHolder = currentViewHolder
            } else {
                // Snap back to original position
                animateToPosition(cardView, 0f)

                // Clear swiped state if this was the swiped item
                if (isCurrentlySwipedItem) {
                    swipedViewHolder = null
                }
            }
            
            recyclerView.requestDisallowInterceptTouchEvent(false)
        }
        
        currentViewHolder = null
        isHorizontalSwipe = false
    }

    private fun animateToPosition(cardView: MaterialCardView, targetX: Float) {
        ObjectAnimator.ofFloat(cardView, "translationX", targetX).apply {
            duration = 200
            start()
        }
    }

    private fun closeSwipedItem() {
        swipedViewHolder?.let { viewHolder ->
            val cardView = viewHolder.itemView.findViewById<MaterialCardView>(R.id.cardView)
            animateToPosition(cardView, 0f)
            swipedViewHolder = null
        }
    }

} 