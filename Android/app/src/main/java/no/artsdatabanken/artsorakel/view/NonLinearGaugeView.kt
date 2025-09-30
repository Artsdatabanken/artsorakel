package no.artsdatabanken.artsorakel.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import no.artsdatabanken.artsorakel.R // Import your R class
import androidx.core.content.withStyledAttributes

class NonLinearGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Configurable properties ---
    private var probability: Double = 0.0 // Value between 0.0 and 1.0
    private var circleDiameter: Float = 8f.dpToPx()
    private var outlineColor: Int = Color.GRAY
    private var unfilledColor: Int = Color.LTGRAY

    // --- Drawing tools ---
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundRect = RectF()


    // --- Color interpolation ---
    private val colors = listOf(
        Color.rgb(170, 0, 0),       // Red
        Color.rgb(195, 107, 22),
        Color.rgb(220, 214, 43),    // Yellow
        Color.rgb(148, 195, 62),
        Color.rgb(76, 175, 80)      // Green
    )


	// --- Fill thresholds (percent) ---
	// Circle 1 is always filled. The remaining circles (2..5) are filled if probability exceeds these percentages.
	private val fillThresholdPercents: List<Int> = listOf(35, 65, 85, 95)

    init {
        // Read custom attributes
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.NonLinearGaugeView, defStyleAttr, 0) {
                probability = getFloat(R.styleable.NonLinearGaugeView_gaugeProbability, 0.0f).toDouble()
                // New circle attrs with fallback to legacy names
                circleDiameter = getDimension(
                    R.styleable.NonLinearGaugeView_gaugeCircleDiameter,
                    getDimension(R.styleable.NonLinearGaugeView_gaugeBarHeight, 8f.dpToPx())
                )
                outlineColor = getColor(
                    R.styleable.NonLinearGaugeView_gaugeOutlineColor,
                    getColor(R.styleable.NonLinearGaugeView_gaugeTickColor, Color.GRAY)
                )
                unfilledColor = getColor(
                    R.styleable.NonLinearGaugeView_gaugeUnfilledColor,
                    getColor(R.styleable.NonLinearGaugeView_gaugeBackgroundColor, Color.LTGRAY)
                )
            }
        }

        // Configure paints
        backgroundPaint.style = Paint.Style.FILL
        foregroundPaint.style = Paint.Style.FILL
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 1f.dpToPx()
        outlinePaint.color = outlineColor
    }

    fun setProbability(prob: Double) {
        // Clamp probability between 0.0 and 1.0
        this.probability = prob.coerceIn(0.0, 1.0)
        // Request a redraw
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        // Set measured height based on barHeight + some padding for potential ticks outside
        val desiredHeight = (circleDiameter + 4f.dpToPx()).toInt()
        setMeasuredDimension(desiredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update bounds for drawing based on new size and padding
        backgroundRect.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat() + (h - circleDiameter) / 2,
            (w - paddingRight).toFloat(),
            paddingTop.toFloat() + (h + circleDiameter) / 2
        )
    }

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		// Compute layout area based on backgroundRect (centered vertically using barHeight)
		val contentLeft = backgroundRect.left
		val contentCenterY = (backgroundRect.top + backgroundRect.bottom) / 2f

        // Circles configuration
		val circleRadius = circleDiameter / 2f
        val numCircles = 1 + fillThresholdPercents.size // First circle always filled
        val gapSize = circleDiameter / 5f

		// Determine how many circles should be filled
        val probabilityPercent = probability * 100.0
		var filledCount = 1 // First circle is always filled
		for (threshold in fillThresholdPercents) {
			if (probabilityPercent > threshold) {
				filledCount += 1
			}
		}
		filledCount = filledCount.coerceIn(1, numCircles)

		// Colors
        val filledColor = colors[filledCount-1]

        val emptyColor = unfilledColor

		// Draw circles from left to right
		var cx = contentLeft + circleRadius
		for (index in 0 until numCircles) {
			val isFilled = index < filledCount
            foregroundPaint.color = if (isFilled) filledColor else emptyColor
            foregroundPaint.alpha = if (isFilled) 255 else 255/3
			canvas.drawCircle(cx, contentCenterY, circleRadius, foregroundPaint)
			cx += circleDiameter + gapSize
		}
	}


    // Helper to convert dp to pixels
    private fun Float.dpToPx(): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)
    }
} 