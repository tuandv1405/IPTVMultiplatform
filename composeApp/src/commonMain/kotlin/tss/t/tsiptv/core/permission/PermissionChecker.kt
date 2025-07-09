package tss.t.tsiptv.core.permission

import kotlinx.coroutines.flow.Flow

/**
 * Interface for checking and requesting permissions.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface PermissionChecker {
    /**
     * Checks if a permission is granted.
     *
     * @param permission The permission to check
     * @return true if the permission is granted, false otherwise
     */
    fun isPermissionGranted(permission: Permission): Boolean

    /**
     * Requests a permission.
     *
     * @param permission The permission to request
     * @return A Flow emitting the result of the permission request
     */
    fun requestPermission(permission: Permission): Flow<PermissionResult>

    /**
     * Requests multiple permissions.
     *
     * @param permissions The permissions to request
     * @return A Flow emitting the results of the permission requests
     */
    fun requestPermissions(permissions: List<Permission>): Flow<Map<Permission, PermissionResult>>

    /**
     * Shows a custom dialog before redirecting to settings.
     * This is useful when a permission has been permanently denied and the user needs to
     * manually grant it in the app settings.
     *
     * @param permission The permission that needs to be granted
     * @param onPositiveClick Callback for when the user clicks the positive button (go to settings)
     * @param onNegativeClick Callback for when the user clicks the negative button (cancel)
     */
    fun showPermissionRationaleDialog(
        permission: Permission,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit
    )

    /**
     * Opens the app settings page where the user can manually grant permissions.
     * This is useful when a permission has been permanently denied.
     *
     * @return true if the settings page was opened successfully, false otherwise
     */
    fun openAppSettings(): Boolean
}

/**
 * Enum representing different permissions.
 */
enum class Permission {
    // Network permissions
    ACCESS_NETWORK_STATE,
    INTERNET,

    // Storage permissions
    READ_EXTERNAL_STORAGE,
    WRITE_EXTERNAL_STORAGE,

    // Camera and microphone
    CAMERA,
    RECORD_AUDIO,

    // Location permissions
    ACCESS_FINE_LOCATION,
    ACCESS_COARSE_LOCATION,

    // Calendar permissions
    READ_CALENDAR,
    WRITE_CALENDAR,

    // Contacts permissions
    READ_CONTACTS,
    WRITE_CONTACTS,

    // Phone permissions
    READ_PHONE_STATE,
    CALL_PHONE,

    // SMS permissions
    READ_SMS,
    SEND_SMS,

    // Notifications
    POST_NOTIFICATIONS,

    // Bluetooth permissions
    BLUETOOTH,
    BLUETOOTH_ADMIN,
    BLUETOOTH_CONNECT,
    BLUETOOTH_SCAN,

    // Biometric
    USE_BIOMETRIC,

    // iOS specific permissions
    PHOTO_LIBRARY,
    PHOTO_LIBRARY_ADD_ONLY,
    MEDIA_LIBRARY,
    REMINDERS,
    MOTION,
    HEALTH,
    SPEECH_RECOGNITION,
    SIRI,
    FACE_ID
}

/**
 * Enum representing the result of a permission request.
 */
enum class PermissionResult {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED
}

/**
 * Data class containing information about a permission.
 */
data class PermissionInfo(
    val permission: Permission,
    val title: String,
    val description: String,
    val settingsDescription: String
)

/**
 * Expect declaration for the platform-specific PermissionChecker.
 * Each platform will provide its own implementation.
 */
expect object PermissionCheckerFactory {
    /**
     * Creates a platform-specific PermissionChecker.
     * @return A PermissionChecker instance appropriate for the current platform.
     */
    fun create(): PermissionChecker
}
