package no.artsdatabanken.artsorakel.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that draws a semi-transparent overlay with a transparent square cutout
 * for image cropping visualization.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 178
    }
    
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = null
    }
    
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    
    private var cropRect = RectF()
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateCropRect(w, h)
    }
    
    private fun calculateCropRect(viewWidth: Int, viewHeight: Int) {
        val size = minOf(viewWidth, viewHeight).toFloat()
        val left = (viewWidth - size) / 2f
        val top = (viewHeight - size) / 2f
        cropRect.set(left, top, left + size, top + size)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(cropRect, clearPaint)
        canvas.drawRect(cropRect, borderPaint)
    }
    
    /**
     * Get the crop rectangle in view coordinates
     */
    fun getCropRect(): RectF {
        return RectF(cropRect)
    }
}