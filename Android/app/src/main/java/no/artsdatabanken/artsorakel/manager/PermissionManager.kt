package no.artsdatabanken.artsorakel.manager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject

/**
 * Represents the current status of photo/media permissions.
 */
enum class PhotoPermissionStatus {
    /** Full access to all photos (READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE granted) */
    FULL_ACCESS,
    /** Limited access to user-selected photos only (Android 14+ with READ_MEDIA_VISUAL_USER_SELECTED) */
    LIMITED_ACCESS,
    /** No permission granted */
    NO_ACCESS
}

/**
 * Enhanced permission manager that handles all app permissions with lifecycle awareness.
 * Provides a centralized way to manage permission requests and callbacks.
 */
class PermissionManager @Inject constructor() : DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var requestMorePhotosLauncher: ActivityResultLauncher<Array<String>>? = null

    private var pendingAction: (() -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Initialize the PermissionManager with activity context and launcher.
     * Must be called before using any permission methods.
     */
    fun initialize(
        activity: AppCompatActivity,
        requestPermissionLauncher: ActivityResultLauncher<String>,
        requestMorePhotosLauncher: ActivityResultLauncher<Array<String>>? = null
    ) {
        this.activity = activity
        this.requestPermissionLauncher = requestPermissionLauncher
        this.requestMorePhotosLauncher = requestMorePhotosLauncher
        activity.lifecycle.addObserver(this)
    }

    /**
     * Gets the current photo/media permission status.
     * On Android 14+, distinguishes between full access and limited (user-selected) access.
     */
    fun getPhotoPermissionStatus(): PhotoPermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: Check for both full and limited access
            val hasFullAccess = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED

            val hasLimitedAccess = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED

            when {
                hasFullAccess -> PhotoPermissionStatus.FULL_ACCESS
                hasLimitedAccess -> PhotoPermissionStatus.LIMITED_ACCESS
                else -> PhotoPermissionStatus.NO_ACCESS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13: Only full or no access
            val hasAccess = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            if (hasAccess) PhotoPermissionStatus.FULL_ACCESS else PhotoPermissionStatus.NO_ACCESS
        } else {
            // Android 12 and below
            val hasAccess = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (hasAccess) PhotoPermissionStatus.FULL_ACCESS else PhotoPermissionStatus.NO_ACCESS
        }
    }

    /**
     * Checks if camera permission is granted.
     */
    fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if any location permission is granted.
     */
    fun isLocationPermissionGranted(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasFine || hasCoarse
    }

    /**
     * Opens the app's settings page where the user can manage permissions.
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    /**
     * Re-requests photo permission. On Android 14+, this allows the user to select more photos
     * or grant full access. On older versions, it opens app settings.
     */
    fun requestMorePhotoAccess(onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: Re-request permission to show photo picker again
            pendingAction = onComplete
            requestMorePhotosLauncher?.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            ) ?: run {
                // Fallback to single permission request
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Older versions: Open settings
            openAppSettings()
            onComplete?.invoke()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        clearCallbacks()
    }

    /**
     * Checks camera permission and executes action if granted
     */
    fun checkCameraPermissionAndExecute(action: () -> Unit) {
        handlePermission(
            Manifest.permission.CAMERA,
            "Camera permission is needed to take pictures",
            action
        )
    }

    /**
     * Checks location permission and executes action if granted
     * Accepts both coarse and fine location permissions
     */
    fun checkLocationPermissionAndExecute(action: () -> Unit) {
        // First check if we already have either permission
        val hasFineLocation = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            // We have at least one location permission, proceed
            action()
        } else {
            // Request coarse location (which allows user to choose approximate)
            handlePermission(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "Location permission is needed to geotag your photos",
                action
            )
        }
    }

    /**
     * Checks storage permission and executes action if granted
     * Handles different permissions based on Android version
     */
    fun checkStoragePermissionAndExecute(action: () -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        handlePermission(
            permission,
            "Storage permission is needed to select images",
            action
        )
    }

    /**
     * Checks write storage permission and executes action if granted
     * Only needed for Android 9 and below (API 28 and below)
     */
    fun checkWriteStoragePermissionAndExecute(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // For Android 9 and below, we need WRITE_EXTERNAL_STORAGE permission
            handlePermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "Storage permission is needed to save images to gallery",
                action
            )
        } else {
            // For Android 10+, we use MediaStore API which doesn't require WRITE_EXTERNAL_STORAGE
            action()
        }
    }

    /**
     * Checks ACCESS_MEDIA_LOCATION permission for Android 10+ to read location from images
     */
    fun checkMediaLocationPermissionAndExecute(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            handlePermission(
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                "Permission is needed to read location data from images",
                action
            )
        } else {
            // For Android 9 and below, no special permission needed
            action()
        }
    }



    /**
     * Handles the result of a permission request
     * Should be called from the activity's permission result callback
     */
    fun handlePermissionResult(isGranted: Boolean) {
        val action = pendingAction
        clearCallbacks()
        if (isGranted) {
            handler.post {
                action?.invoke()
            }
        } else {
            onPermissionDenied?.invoke()
            showPermissionDeniedMessage()
        }
    }

    private fun handlePermission(permission: String, rationale: String, grantedAction: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
                grantedAction()
            }
            activity.shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(activity, rationale, Toast.LENGTH_LONG).show()
                pendingAction = grantedAction
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                pendingAction = grantedAction
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(activity, "Permission denied.", Toast.LENGTH_SHORT).show()
    }

    private fun clearCallbacks() {
        pendingAction = null
        onPermissionDenied = null
    }


} 