package no.artsdatabanken.artsorakel.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ResultDividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    
    private val dividerPaint = Paint().apply {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(
            no.artsdatabanken.artsorakel.R.attr.border_default,
            typedValue,
            true
        )
        color = typedValue.data
        style = Paint.Style.FILL
    }
    
    private val dividerHeight = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        2f,
        context.resources.displayMetrics
    ).toInt()
    
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // Add bottom offset for all items except the last one
        val position = parent.getChildAdapterPosition(view)
        if (position < (parent.adapter?.itemCount ?: 0) - 1) {
            outRect.bottom = dividerHeight
        }
    }
    
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight
        
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            
            // Draw divider for all items except the last one
            if (position < (parent.adapter?.itemCount ?: 0) - 1) {
                val params = child.layoutParams as RecyclerView.LayoutParams
                val top = child.bottom + params.bottomMargin
                val bottom = top + dividerHeight
                
                c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), dividerPaint)
            }
        }
    }
} 