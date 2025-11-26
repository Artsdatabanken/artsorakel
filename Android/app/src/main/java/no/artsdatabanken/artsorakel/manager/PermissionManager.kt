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
 * Enhanced permission manager that handles all app permissions with lifecycle awareness.
 * Provides a centralized way to manage permission requests and callbacks.
 */
class PermissionManager @Inject constructor() : DefaultLifecycleObserver {

    private lateinit var activity: AppCompatActivity
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private var pendingAction: (() -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Initialize the PermissionManager with activity context and launcher.
     * Must be called before using any permission methods.
     */
    fun initialize(
        activity: AppCompatActivity,
        requestPermissionLauncher: ActivityResultLauncher<String>
    ) {
        this.activity = activity
        this.requestPermissionLauncher = requestPermissionLauncher
        activity.lifecycle.addObserver(this)
    }

    fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

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

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
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