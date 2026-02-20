package no.artsdatabanken.artsorakel.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.artsdatabanken.artsorakel.BuildConfig
import no.artsdatabanken.artsorakel.core.Constants
import no.artsdatabanken.artsorakel.databinding.ActivityImageCropperBinding
import no.artsdatabanken.artsorakel.extensions.setupStatusBar
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Image cropper activity that uses a single view with an overlay
 * for clean and efficient square cropping.
 *
 * Loads a downsampled bitmap for the interactive display (max ~2048px) to avoid OOM,
 * then uses BitmapRegionDecoder to crop from the original file at full resolution.
 */
class ImageCropperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageCropperBinding
    private var imageUri: Uri? = null
    private var originalImageUri: Uri? = null
    private var displayBitmap: Bitmap? = null
    private var isRecropping = false
    private var oldCroppedUri: String? = null
    private var locationLat: Double = Double.NaN
    private var locationLon: Double = Double.NaN
    private var locationAlt: Double = Double.NaN

    // Original image dimensions (raw file, before any downsampling or rotation)
    private var rawWidth = 0
    private var rawHeight = 0
    private var sampleSize = 1
    private var exifOrientation = ExifInterface.ORIENTATION_NORMAL
    // Actual decoded dimensions (after inSampleSize, before EXIF rotation)
    private var downsampledWidth = 0
    private var downsampledHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageCropperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStatusBar()

        imageUri = intent.data
        isRecropping = intent.getBooleanExtra("recrop", false)
        oldCroppedUri = intent.getStringExtra("old_cropped_uri")

        // Extract location data from intent
        locationLat = intent.getDoubleExtra("location_lat", Double.NaN)
        locationLon = intent.getDoubleExtra("location_lon", Double.NaN)
        locationAlt = intent.getDoubleExtra("location_alt", Double.NaN)

        if (BuildConfig.DEBUG) android.util.Log.d("ImageCropperActivity", "Received location: lat=$locationLat, lon=$locationLon, alt=$locationAlt")

        if (imageUri == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setupViews()
        loadImage()
    }

    private fun setupViews() {
        binding.imageViewDelete.visibility = if (isRecropping) View.VISIBLE else View.GONE

        binding.imageViewContinue.setOnClickListener {
            cropAndSaveImage()
        }

        binding.imageViewCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.imageViewDelete.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("delete_image", true)
                putExtra(Constants.IntentExtras.EXTRA_ORIGINAL_IMAGE_URI, originalImageUri.toString())
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadImage() {
        val sourceUri = imageUri ?: return

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapWithOrientation(sourceUri)
                }

                if (bitmap != null && !isFinishing && !isDestroyed) {
                    displayBitmap = bitmap
                    originalImageUri = sourceUri
                    binding.imageViewMain.setImageBitmap(bitmap)
                    binding.imageViewMain.setOriginalDimensions(rawWidth, rawHeight, sampleSize)

                    binding.imageViewMain.post {
                        val viewWidth = binding.imageViewMain.width.toFloat()
                        val viewHeight = binding.imageViewMain.height.toFloat()
                        val imageWidth = bitmap.width.toFloat()
                        val imageHeight = bitmap.height.toFloat()

                        if (viewWidth > 0 && viewHeight > 0) {
                            val cropSize = minOf(viewWidth, viewHeight)
                            val scaleX = cropSize / imageWidth
                            val scaleY = cropSize / imageHeight
                            val scale = maxOf(scaleX, scaleY)

                            val matrix = Matrix()
                            matrix.setScale(scale, scale)
                            val scaledWidth = imageWidth * scale
                            val scaledHeight = imageHeight * scale
                            matrix.postTranslate(
                                (viewWidth - scaledWidth) / 2f,
                                (viewHeight - scaledHeight) / 2f
                            )
                            binding.imageViewMain.setInitialMatrix(matrix)
                        }
                    }
                } else if (bitmap == null && !isFinishing && !isDestroyed) {
                    Toast.makeText(this@ImageCropperActivity, "Could not load image", Toast.LENGTH_LONG).show()
                    setResult(RESULT_CANCELED)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private suspend fun loadBitmapWithOrientation(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Read raw dimensions without decoding
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            val rw = boundsOpts.outWidth
            val rh = boundsOpts.outHeight
            if (rw <= 0 || rh <= 0) return@withContext null

            // 2. Calculate inSampleSize to cap display at ~2048px longest side
            val maxDisplaySize = 2048
            var sample = 1
            var w = rw
            var h = rh
            while (w / 2 >= maxDisplaySize || h / 2 >= maxDisplaySize) {
                sample *= 2
                w /= 2
                h /= 2
            }

            // 3. Decode with downsampling
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            // 4. Store dimensions
            rawWidth = rw
            rawHeight = rh
            sampleSize = sample
            downsampledWidth = bitmap.width
            downsampledHeight = bitmap.height

            // 5. Get EXIF orientation and rotate for display
            val orientation = getImageOrientation(uri)
            exifOrientation = orientation

            if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                rotateBitmap(bitmap, orientation)
            } else {
                bitmap
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getImageOrientation(uri: Uri): Int {
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path
                    if (path != null) {
                        val exif = ExifInterface(path)
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } else {
                        ExifInterface.ORIENTATION_NORMAL
                    }
                }
                "content" -> {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val exif = ExifInterface(inputStream)
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } ?: ExifInterface.ORIENTATION_NORMAL
                }
                else -> ExifInterface.ORIENTATION_NORMAL
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropAndSaveImage() {
        val uri = imageUri ?: run {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        if (displayBitmap == null || rawWidth <= 0 || rawHeight <= 0) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val croppedUri = withContext(Dispatchers.IO) {
                    // 1. Get crop rect in view coordinates
                    val viewCropRect = binding.cropOverlay.getCropRect()

                    // 2. Inverse-map through imageMatrix → display-bitmap coordinates
                    val currentMatrix = binding.imageViewMain.getCurrentMatrix()
                    val inverseMatrix = Matrix()
                    currentMatrix.invert(inverseMatrix)
                    val displayRect = RectF()
                    inverseMatrix.mapRect(displayRect, viewCropRect)

                    // 3. Map display-bitmap coords → raw-original coords
                    val rawRect = mapDisplayToRawRect(displayRect)

                    // 4. Clamp to raw image bounds
                    val decoderRect = Rect(
                        max(0, floor(rawRect.left.toDouble()).toInt()),
                        max(0, floor(rawRect.top.toDouble()).toInt()),
                        min(rawWidth, ceil(rawRect.right.toDouble()).toInt()),
                        min(rawHeight, ceil(rawRect.bottom.toDouble()).toInt())
                    )

                    if (decoderRect.width() <= 0 || decoderRect.height() <= 0) {
                        return@withContext null
                    }

                    // 5. Decode just the crop region using BitmapRegionDecoder
                    val regionOpts = BitmapFactory.Options()
                    val regionMax = max(decoderRect.width(), decoderRect.height())
                    var regionSample = 1
                    while (regionMax / regionSample > 4096) {
                        regionSample *= 2
                    }
                    regionOpts.inSampleSize = regionSample

                    val regionBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                        @Suppress("DEPRECATION")
                        val decoder = BitmapRegionDecoder.newInstance(stream, false)
                        decoder?.decodeRegion(decoderRect, regionOpts)
                    } ?: return@withContext null

                    // 6. Apply EXIF rotation to the decoded region
                    val rotatedBitmap = if (exifOrientation != ExifInterface.ORIENTATION_NORMAL) {
                        val rotated = rotateBitmap(regionBitmap, exifOrientation)
                        if (rotated !== regionBitmap) regionBitmap.recycle()
                        rotated
                    } else {
                        regionBitmap
                    }

                    // 7. Save as JPEG
                    val resultUri = saveBitmapToFile(rotatedBitmap)
                    rotatedBitmap.recycle()
                    resultUri
                }

                if (croppedUri == null) {
                    Toast.makeText(this@ImageCropperActivity, "Could not crop image", Toast.LENGTH_LONG).show()
                    setResult(RESULT_CANCELED)
                    finish()
                    return@launch
                }

                val resultIntent = Intent().apply {
                    data = croppedUri
                    putExtra(Constants.IntentExtras.EXTRA_ORIGINAL_IMAGE_URI, originalImageUri.toString())
                    putExtra("recrop", isRecropping)
                    oldCroppedUri?.let { putExtra("old_cropped_uri", it) }

                    // Pass location data back to MainActivity
                    if (!locationLat.isNaN()) putExtra("location_lat", locationLat)
                    if (!locationLon.isNaN()) putExtra("location_lon", locationLon)
                    if (!locationAlt.isNaN()) putExtra("location_alt", locationAlt)
                }
                setResult(RESULT_OK, resultIntent)
                finish()

            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageCropperActivity, "Image too large to crop", Toast.LENGTH_LONG).show()
                }
                setResult(RESULT_CANCELED)
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    /**
     * Maps a rect from display-bitmap coordinates (rotated, downsampled)
     * to raw-original file coordinates (un-rotated, full resolution).
     *
     * The display bitmap was created by decoding the raw file with inSampleSize
     * (producing downsampledWidth × downsampledHeight) and then applying EXIF rotation.
     * BitmapRegionDecoder operates on the raw file, so we must reverse both transforms.
     */
    private fun mapDisplayToRawRect(displayRect: RectF): RectF {
        val dW = downsampledWidth.toFloat()
        val dH = downsampledHeight.toFloat()

        // Build forward transform: downsampled-raw-coords → display-coords
        // This mirrors what rotateBitmap + Bitmap.createBitmap does
        val forward = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                forward.postRotate(90f)
                forward.postTranslate(dH, 0f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                forward.postRotate(180f)
                forward.postTranslate(dW, dH)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                forward.postRotate(270f)
                forward.postTranslate(0f, dW)
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                forward.postScale(-1f, 1f)
                forward.postTranslate(dW, 0f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                forward.postScale(1f, -1f)
                forward.postTranslate(0f, dH)
            }
            // NORMAL and others: identity
        }

        // Inverse: display → downsampled-raw
        val inverse = Matrix()
        if (!forward.invert(inverse)) {
            inverse.reset()
        }

        // Scale from downsampled to raw-original coordinates
        inverse.postScale(rawWidth.toFloat() / dW, rawHeight.toFloat() / dH)

        val rawRect = RectF()
        inverse.mapRect(rawRect, displayRect)
        return rawRect
    }

    private fun saveBitmapToFile(bitmap: Bitmap): Uri {
        val imagesDir = File(cacheDir, "images")
        imagesDir.mkdirs()
        val imageFile = File(imagesDir, "cropped_${System.currentTimeMillis()}.jpg")

        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        // Write location to the cropped image if available (full precision)
        if (!locationLat.isNaN() && !locationLon.isNaN()) {
            try {
                val exif = ExifInterface(imageFile.absolutePath)
                exif.setLatLong(locationLat, locationLon)
                if (!locationAlt.isNaN()) {
                    exif.setAltitude(locationAlt)
                }
                exif.saveAttributes()
                if (BuildConfig.DEBUG) android.util.Log.d("ImageCropperActivity", "Wrote full precision location to cropped image: lat=$locationLat, lon=$locationLon")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("ImageCropperActivity", "Failed to write location to cropped image", e)
            }
        }

        return Uri.fromFile(imageFile)
    }

    override fun onDestroy() {
        super.onDestroy()
        displayBitmap?.recycle()
    }
}
