package tss.t.tsiptv.core.permission

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaType
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIViewController

/**
 * iOS implementation of PermissionChecker.
 * Uses iOS's permission system to check and request permissions.
 */
class IosPermissionChecker(
    private val viewController: UIViewController? = null
) : PermissionChecker {

    // Map of Permission enum to PermissionInfo
    private val permissionInfoMap = mapOf(
        Permission.ACCESS_NETWORK_STATE to PermissionInfo(
            permission = Permission.ACCESS_NETWORK_STATE,
            title = "Network State Access",
            description = "This permission is needed to check your network connectivity status.",
            settingsDescription = "Please enable Network State Access in settings to allow the app to check your network connectivity."
        ),
        Permission.INTERNET to PermissionInfo(
            permission = Permission.INTERNET,
            title = "Internet Access",
            description = "This permission is needed to connect to the internet.",
            settingsDescription = "Please enable Internet Access in settings to allow the app to connect to the internet."
        ),
        Permission.CAMERA to PermissionInfo(
            permission = Permission.CAMERA,
            title = "Camera Access",
            description = "This permission is needed to use your device's camera.",
            settingsDescription = "Please enable Camera Access in settings to allow the app to use your device's camera."
        ),
        Permission.RECORD_AUDIO to PermissionInfo(
            permission = Permission.RECORD_AUDIO,
            title = "Microphone Access",
            description = "This permission is needed to record audio using your device's microphone.",
            settingsDescription = "Please enable Microphone Access in settings to allow the app to record audio."
        ),
        Permission.ACCESS_FINE_LOCATION to PermissionInfo(
            permission = Permission.ACCESS_FINE_LOCATION,
            title = "Precise Location Access",
            description = "This permission is needed to access your precise location.",
            settingsDescription = "Please enable Precise Location Access in settings to allow the app to access your location."
        ),
        Permission.ACCESS_COARSE_LOCATION to PermissionInfo(
            permission = Permission.ACCESS_COARSE_LOCATION,
            title = "Approximate Location Access",
            description = "This permission is needed to access your approximate location.",
            settingsDescription = "Please enable Approximate Location Access in settings to allow the app to access your location."
        ),
        Permission.PHOTO_LIBRARY to PermissionInfo(
            permission = Permission.PHOTO_LIBRARY,
            title = "Photo Library Access",
            description = "This permission is needed to access your photo library.",
            settingsDescription = "Please enable Photo Library Access in settings to allow the app to access your photos."
        ),
        Permission.PHOTO_LIBRARY_ADD_ONLY to PermissionInfo(
            permission = Permission.PHOTO_LIBRARY_ADD_ONLY,
            title = "Photo Library Add Access",
            description = "This permission is needed to save photos to your photo library.",
            settingsDescription = "Please enable Photo Library Add Access in settings to allow the app to save photos to your library."
        ),
        Permission.FACE_ID to PermissionInfo(
            permission = Permission.FACE_ID,
            title = "Face ID Access",
            description = "This permission is needed to use Face ID for authentication.",
            settingsDescription = "Please enable Face ID Access in settings to allow the app to use Face ID for authentication."
        )
    )

    override fun isPermissionGranted(permission: Permission): Boolean {
        return when (permission) {
            // Network permissions are implicitly granted if included in Info.plist
            Permission.ACCESS_NETWORK_STATE, Permission.INTERNET -> true

            // Camera permission
            Permission.CAMERA -> {
                val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
                status == AVAuthorizationStatusAuthorized
            }

            // Microphone permission
            Permission.RECORD_AUDIO -> {
                val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
                status == AVAuthorizationStatusAuthorized
            }

            // Location permissions
            Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION -> {
                val status = CLLocationManager.authorizationStatus()
                status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse
            }

            // Photo Library permissions
            Permission.PHOTO_LIBRARY, Permission.PHOTO_LIBRARY_ADD_ONLY -> {
                val status = PHPhotoLibrary.authorizationStatus()
                status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
            }

            // For other permissions, we need to check if they're declared in Info.plist
            // For now, we'll assume they're not granted
            else -> false
        }
    }

    override fun requestPermission(permission: Permission): Flow<PermissionResult> {
        return when (permission) {
            // Network permissions are implicitly granted if included in Info.plist
            Permission.ACCESS_NETWORK_STATE, Permission.INTERNET -> {
                flowOf(PermissionResult.GRANTED)
            }

            // Camera permission
            Permission.CAMERA -> {
                requestCameraPermission()
            }

            // Microphone permission
            Permission.RECORD_AUDIO -> {
                requestMicrophonePermission()
            }

            // Location permissions
            Permission.ACCESS_FINE_LOCATION, Permission.ACCESS_COARSE_LOCATION -> {
                requestLocationPermission()
            }

            // Photo Library permissions
            Permission.PHOTO_LIBRARY, Permission.PHOTO_LIBRARY_ADD_ONLY -> {
                requestPhotoLibraryPermission()
            }

            // For other permissions, we need to check if they're declared in Info.plist
            // For now, we'll assume they're not granted
            else -> {
                flowOf(PermissionResult.DENIED)
            }
        }
    }

    override fun requestPermissions(permissions: List<Permission>): Flow<Map<Permission, PermissionResult>> {
        // For iOS, we'll request permissions one by one
        // This is because iOS doesn't have a built-in way to request multiple permissions at once
        val resultMap = mutableMapOf<Permission, PermissionResult>()

        // First, check which permissions are already granted
        permissions.forEach { permission ->
            if (isPermissionGranted(permission)) {
                resultMap[permission] = PermissionResult.GRANTED
            }
        }

        // If all permissions are already granted, return immediately
        if (resultMap.size == permissions.size) {
            return flowOf(resultMap)
        }

        // Otherwise, request the remaining permissions one by one
        // For simplicity, we'll just return a map with all permissions set to GRANTED or DENIED
        // In a real implementation, you would want to request each permission and update the map accordingly
        return flowOf(permissions.associateWith { 
            if (isPermissionGranted(it)) PermissionResult.GRANTED else PermissionResult.DENIED 
        })
    }

    override fun showPermissionRationaleDialog(
        permission: Permission,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    ) {
        if (viewController == null) {
            onNegativeClick()
            return
        }

        val permissionInfo = permissionInfoMap[permission] ?: PermissionInfo(
            permission = permission,
            title = "Permission Required",
            description = "This permission is required for the app to function properly.",
            settingsDescription = "Please enable this permission in settings to allow the app to function properly."
        )

        val alertController = UIAlertController.alertControllerWithTitle(
            title = permissionInfo.title,
            message = permissionInfo.settingsDescription,
            preferredStyle = UIAlertControllerStyleAlert
        )

        val settingsAction = UIAlertAction.actionWithTitle(
            title = "Go to Settings",
            style = UIAlertActionStyleDefault
        ) { _ ->
            onPositiveClick()
        }

        val cancelAction = UIAlertAction.actionWithTitle(
            title = "Cancel",
            style = UIAlertActionStyleCancel
        ) { _ ->
            onNegativeClick()
        }

        alertController.addAction(settingsAction)
        alertController.addAction(cancelAction)

        viewController.presentViewController(alertController, animated = true, completion = null)
    }

    override fun openAppSettings(): Boolean {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return false
        return UIApplication.sharedApplication.openURL(settingsUrl)
    }

    private fun requestCameraPermission(): Flow<PermissionResult> {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)

        return when (status) {
            AVAuthorizationStatusAuthorized -> {
                flowOf(PermissionResult.GRANTED)
            }
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                flowOf(PermissionResult.PERMANENTLY_DENIED)
            }
            AVAuthorizationStatusNotDetermined -> {
                callbackFlow {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        if (granted) {
                            trySend(PermissionResult.GRANTED)
                        } else {
                            trySend(PermissionResult.DENIED)
                        }
                        close()
                    }

                    awaitClose { }
                }
            }
            else -> {
                flowOf(PermissionResult.DENIED)
            }
        }
    }

    private fun requestMicrophonePermission(): Flow<PermissionResult> {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)

        return when (status) {
            AVAuthorizationStatusAuthorized -> {
                flowOf(PermissionResult.GRANTED)
            }
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                flowOf(PermissionResult.PERMANENTLY_DENIED)
            }
            AVAuthorizationStatusNotDetermined -> {
                callbackFlow {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
                        if (granted) {
                            trySend(PermissionResult.GRANTED)
                        } else {
                            trySend(PermissionResult.DENIED)
                        }
                        close()
                    }

                    awaitClose { }
                }
            }
            else -> {
                flowOf(PermissionResult.DENIED)
            }
        }
    }

    private fun requestLocationPermission(): Flow<PermissionResult> {
        // For location permissions, we need to use CLLocationManager
        // This is more complex and would require a delegate to handle the callbacks
        // For simplicity, we'll just return DENIED for now
        return flowOf(PermissionResult.DENIED)
    }

    private fun requestPhotoLibraryPermission(): Flow<PermissionResult> {
        val status = PHPhotoLibrary.authorizationStatus()

        return when (status) {
            PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> {
                flowOf(PermissionResult.GRANTED)
            }
            PHAuthorizationStatusDenied, PHAuthorizationStatusRestricted -> {
                flowOf(PermissionResult.PERMANENTLY_DENIED)
            }
            PHAuthorizationStatusNotDetermined -> {
                callbackFlow {
                    PHPhotoLibrary.requestAuthorization { newStatus ->
                        if (newStatus == PHAuthorizationStatusAuthorized || newStatus == PHAuthorizationStatusLimited) {
                            trySend(PermissionResult.GRANTED)
                        } else {
                            trySend(PermissionResult.DENIED)
                        }
                        close()
                    }

                    awaitClose { }
                }
            }
            else -> {
                flowOf(PermissionResult.DENIED)
            }
        }
    }
}

/**
 * Actual implementation of PermissionCheckerFactory for iOS.
 */
actual object PermissionCheckerFactory {
    private var viewController: UIViewController? = null

    /**
     * Initializes the factory with a UIViewController.
     * This is needed for showing permission dialogs.
     */
    fun initialize(viewController: UIViewController?) {
        this.viewController = viewController
    }

    /**
     * Creates an iOS-specific PermissionChecker.
     * @return A PermissionChecker instance for iOS.
     */
    actual fun create(): PermissionChecker {
        return IosPermissionChecker(viewController)
    }
}
