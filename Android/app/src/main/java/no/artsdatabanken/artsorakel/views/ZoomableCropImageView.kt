package no.artsdatabanken.artsorakel.views

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * A custom ImageView that provides zoom and pan functionality for image cropping.
 * This view allows users to zoom in/out and pan around the image while maintaining
 * proper boundaries to ensure the crop area can access all parts of the image.
 */
class ZoomableCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imageMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    
    private var minScale = 0.5f
    private var maxScale = 10f
    private var currentScale = 1f
    private var originalSampleSize = 1
    private var hasOriginalDimensions = false
    
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var imageWidth = 0
    private var imageHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0
    private var cropSize = 0f
    
    init {
        scaleType = ScaleType.MATRIX

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newScale = currentScale * scaleFactor

                if (newScale in minScale..maxScale) {
                    imageMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    currentScale = newScale
                    setImageMatrix(imageMatrix)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                constrainMatrix()
                setImageMatrix(imageMatrix)
                super.onScaleEnd(detector)
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetScale = if (currentScale > minScale * 1.5f) minScale else minScale * 2f
                animateToScale(targetScale, e.x, e.y)
                return true
            }
        })
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)

                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        val constrainedDx = getConstrainedTranslationX(dx)
                        val constrainedDy = getConstrainedTranslationY(dy)

                        if (constrainedDx != 0f || constrainedDy != 0f) {
                            imageMatrix.postTranslate(constrainedDx, constrainedDy)
                            setImageMatrix(imageMatrix)
                        }

                        lastTouchX = x
                        lastTouchY = y
                    }
                } else {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        lastTouchX = event.getX(pointerIndex)
                        lastTouchY = event.getY(pointerIndex)
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        
        return true
    }
    
    private fun animateToScale(targetScale: Float, focusX: Float, focusY: Float) {
        val scaleFactor = targetScale / currentScale
        imageMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
        currentScale = targetScale
        constrainMatrix()
        setImageMatrix(imageMatrix)
    }
    
    private fun constrainMatrix() {
        if (imageWidth == 0 || imageHeight == 0 || viewWidth == 0 || viewHeight == 0) return
        
        imageMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]
        
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        val cropLeft = (viewWidth - cropSize) / 2f
        val cropTop = (viewHeight - cropSize) / 2f
        
        var deltaX = 0f
        var deltaY = 0f

        if (scaledImageWidth <= cropSize) {
            deltaX = cropLeft + (cropSize - scaledImageWidth) / 2f - transX
        } else {
            val maxTransX = cropLeft
            val minTransX = cropLeft + cropSize - scaledImageWidth
            
            if (transX > maxTransX) deltaX = maxTransX - transX
            else if (transX < minTransX) deltaX = minTransX - transX
        }

        if (scaledImageHeight <= cropSize) {
            deltaY = cropTop + (cropSize - scaledImageHeight) / 2f - transY
        } else {
            val maxTransY = cropTop
            val minTransY = cropTop + cropSize - scaledImageHeight
            
            if (transY > maxTransY) deltaY = maxTransY - transY
            else if (transY < minTransY) deltaY = minTransY - transY
        }
        
        if (deltaX != 0f || deltaY != 0f) {
            imageMatrix.postTranslate(deltaX, deltaY)
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        cropSize = minOf(w, h).toFloat()
        calculateScaleLimits()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        bm?.let {
            imageWidth = it.width
            imageHeight = it.height
            calculateScaleLimits()
        }
    }

    fun setOriginalDimensions(originalWidth: Int, originalHeight: Int, sampleSize: Int) {
        originalSampleSize = sampleSize
        hasOriginalDimensions = true
        calculateScaleLimits()
    }

    private fun calculateScaleLimits() {
        if (imageWidth == 0 || imageHeight == 0 || cropSize == 0f) return

        val scaleToFitWidth = cropSize / imageWidth
        val scaleToFitHeight = cropSize / imageHeight

        minScale = maxOf(scaleToFitWidth, scaleToFitHeight)

        if (hasOriginalDimensions) {
            val apiTargetSize = 500f
            val computedMaxScale = cropSize * originalSampleSize / apiTargetSize
            maxScale = maxOf(computedMaxScale, minScale * 2f)
        }

        if (currentScale < minScale) {
            currentScale = minScale
            val scaleFactor = minScale / getCurrentScale()
            if (scaleFactor != 1f) {
                imageMatrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
                constrainMatrix()
                setImageMatrix(imageMatrix)
            }
        }

        if (currentScale > maxScale) {
            val scaleFactor = maxScale / getCurrentScale()
            imageMatrix.postScale(scaleFactor, scaleFactor, viewWidth / 2f, viewHeight / 2f)
            currentScale = maxScale
            constrainMatrix()
            setImageMatrix(imageMatrix)
        }
    }
    
    private fun getConstrainedTranslationX(dx: Float): Float {
        imageMatrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val scale = matrixValues[Matrix.MSCALE_X]
        val scaledImageWidth = imageWidth * scale
        
        val newTransX = transX + dx
        val cropLeft = (viewWidth - cropSize) / 2f
        
        return when {
            scaledImageWidth <= cropSize -> {
                val targetX = cropLeft + (cropSize - scaledImageWidth) / 2f
                targetX - transX
            }
            else -> {
                val maxTransX = cropLeft
                val minTransX = cropLeft + cropSize - scaledImageWidth
                
                when {
                    newTransX > maxTransX -> maxTransX - transX
                    newTransX < minTransX -> minTransX - transX
                    else -> dx
                }
            }
        }
    }
    
    private fun getConstrainedTranslationY(dy: Float): Float {
        imageMatrix.getValues(matrixValues)
        val transY = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_Y]
        val scaledImageHeight = imageHeight * scale
        
        val newTransY = transY + dy
        val cropTop = (viewHeight - cropSize) / 2f
        
        return when {
            scaledImageHeight <= cropSize -> {
                val targetY = cropTop + (cropSize - scaledImageHeight) / 2f
                targetY - transY
            }
            else -> {
                val maxTransY = cropTop
                val minTransY = cropTop + cropSize - scaledImageHeight
                
                when {
                    newTransY > maxTransY -> maxTransY - transY
                    newTransY < minTransY -> minTransY - transY
                    else -> dy
                }
            }
        }
    }
    
    fun setInitialMatrix(matrix: Matrix) {
        imageMatrix.set(matrix)
        setImageMatrix(imageMatrix)
        imageMatrix.getValues(matrixValues)
        currentScale = matrixValues[Matrix.MSCALE_X]
    }
    
    fun getCurrentMatrix(): Matrix {
        return Matrix(imageMatrix)
    }
    
    fun getCurrentScale(): Float {
        imageMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }
}