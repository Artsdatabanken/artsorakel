package no.artsdatabanken.artsorakel.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import no.artsdatabanken.artsorakel.core.Constants
import no.artsdatabanken.artsorakel.databinding.ActivityImageCropperBinding
import no.artsdatabanken.artsorakel.extensions.setupStatusBar
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

/**
 * Image cropper activity that uses a single view with an overlay
 * for clean and efficient square cropping.
 */
class ImageCropperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageCropperBinding
    private var imageUri: Uri? = null
    private var originalImageUri: Uri? = null
    private var originalBitmap: Bitmap? = null
    private var isRecropping = false
    private var oldCroppedUri: String? = null
    private var locationLat: Double = Double.NaN
    private var locationLon: Double = Double.NaN
    private var locationAlt: Double = Double.NaN

    @RequiresApi(Build.VERSION_CODES.O)
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

        Log.d("ImageCropperActivity", "Received location: lat=$locationLat, lon=$locationLon, alt=$locationAlt")
        
        if (imageUri == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        
        setupViews()
        loadImage()
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
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
                    originalBitmap = bitmap
                    originalImageUri = sourceUri
                    binding.imageViewMain.setImageBitmap(bitmap)
                    
                    binding.imageViewMain.post {
                        val viewWidth = binding.imageViewMain.width.toFloat()
                        val viewHeight = binding.imageViewMain.height.toFloat()
                        val imageWidth = bitmap.width.toFloat()
                        val imageHeight = bitmap.height.toFloat()
                        
                        if (viewWidth > 0 && viewHeight > 0) {
                            val scaleX = viewWidth / imageWidth
                            val scaleY = viewHeight / imageHeight
                            val scale = minOf(scaleX, scaleY)

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
            val orientation = getImageOrientation(uri)

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            }

            if (bitmap != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.P && orientation != ExifInterface.ORIENTATION_NORMAL) {
                rotateBitmap(bitmap, orientation)
            } else {
                bitmap
            }
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
        } catch (e: Exception) {
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
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun cropAndSaveImage() {
        val bitmap = originalBitmap ?: run {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        
        lifecycleScope.launch {
            try {
                val croppedUri = withContext(Dispatchers.IO) {
                    val imageMatrix = binding.imageViewMain.getCurrentMatrix()
                    val cropSquare = binding.cropOverlay.getCropRect()
                    val cropSize = cropSquare.width().toInt()

                    val matrixValues = FloatArray(9)
                    imageMatrix.getValues(matrixValues)

                    val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        bitmap
                    }

                    val outputBitmap = createBitmap(cropSize, cropSize)
                    val canvas = Canvas(outputBitmap)
                    canvas.drawColor(Color.BLACK)

                    val paint = android.graphics.Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }

                    canvas.withTranslation(-cropSquare.left, -cropSquare.top) {
                        concat(imageMatrix)
                        drawBitmap(softwareBitmap, 0f, 0f, paint)
                    }

                    if (softwareBitmap != bitmap) {
                        softwareBitmap.recycle()
                    }

                    val uri = saveBitmapToFile(outputBitmap)
                    outputBitmap.recycle()
                    uri
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

            } catch (e: Exception) {
                e.printStackTrace()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
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
                Log.d("ImageCropperActivity", "Wrote full precision location to cropped image: lat=$locationLat, lon=$locationLon")
            } catch (e: Exception) {
                Log.e("ImageCropperActivity", "Failed to write location to cropped image", e)
            }
        }

        return Uri.fromFile(imageFile)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        originalBitmap?.recycle()
    }
}