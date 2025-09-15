package tss.t.tsiptv.core.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import tss.t.tsiptv.TSAndroidApplication
import java.lang.ref.WeakReference
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Android implementation of PermissionChecker.
 * Uses Android's runtime permission system to check and request permissions.
 */
class AndroidPermissionChecker(
    activity: ComponentActivity? = null,
) : PermissionChecker, DefaultLifecycleObserver {

    @OptIn(ExperimentalAtomicApi::class)
    private val activityRef: AtomicReference<ComponentActivity?> = AtomicReference(activity)

    init {
        activity?.lifecycle?.addObserver(this)
    }

    /**
     * Cleans up resources to prevent memory leaks
     */
    fun cleanup() {
        singlePermissionCallback = null
        multiplePermissionsCallback = null
    }

    fun registerPermissionCallback(
        singlePermissionCallback: ((Boolean) -> Unit)?,
        multiplePermissionsCallback: ((Map<String, Boolean>) -> Unit)?,
    ) {
        this.singlePermissionCallback = singlePermissionCallback
        this.multiplePermissionsCallback = multiplePermissionsCallback
    }

    // Lifecycle methods
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
        owner.lifecycle.removeObserver(this)
    }

    // Pre-register the permission launchers to avoid lifecycle issues
    private var singlePermissionLauncher: ActivityResultLauncher<String>? = null
    private var multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null

    var singlePermissionCallback: ((Boolean) -> Unit)? = null
        private set
    var multiplePermissionsCallback: ((Map<String, Boolean>) -> Unit)? = null
        private set

    @OptIn(ExperimentalAtomicApi::class)
    fun initializeLaunchers(
        activity: ComponentActivity? = null,
        singleLauncher: ActivityResultLauncher<String>? = null,
        multipleLauncher: ActivityResultLauncher<Array<String>>? = null,
    ) {
        activityRef.load()?.let {
            activityRef.compareAndSet(it, activity)
        } ?: run {
            activityRef.compareAndSet(null, activity)
        }
        singlePermissionLauncher = singleLauncher
        multiplePermissionsLauncher = multipleLauncher
    }

    private val permissionMap = mapOf(
        // Network permissions
        Permission.ACCESS_NETWORK_STATE to Manifest.permission.ACCESS_NETWORK_STATE,
        Permission.INTERNET to Manifest.permission.INTERNET,

        // Storage permissions
        Permission.READ_EXTERNAL_STORAGE to Manifest.permission.READ_EXTERNAL_STORAGE,
        Permission.WRITE_EXTERNAL_STORAGE to Manifest.permission.WRITE_EXTERNAL_STORAGE,

        // Camera and microphone
        Permission.CAMERA to Manifest.permission.CAMERA,
        Permission.RECORD_AUDIO to Manifest.permission.RECORD_AUDIO,

        // Location permissions
        Permission.ACCESS_FINE_LOCATION to Manifest.permission.ACCESS_FINE_LOCATION,
        Permission.ACCESS_COARSE_LOCATION to Manifest.permission.ACCESS_COARSE_LOCATION,

        // Calendar permissions
        Permission.READ_CALENDAR to Manifest.permission.READ_CALENDAR,
        Permission.WRITE_CALENDAR to Manifest.permission.WRITE_CALENDAR,

        // Contacts permissions
        Permission.READ_CONTACTS to Manifest.permission.READ_CONTACTS,
        Permission.WRITE_CONTACTS to Manifest.permission.WRITE_CONTACTS,

        // Phone permissions
        Permission.READ_PHONE_STATE to Manifest.permission.READ_PHONE_STATE,
        Permission.CALL_PHONE to Manifest.permission.CALL_PHONE,

        // SMS permissions
        Permission.READ_SMS to Manifest.permission.READ_SMS,
        Permission.SEND_SMS to Manifest.permission.SEND_SMS,

        // Notifications (Android 13+)
        Permission.POST_NOTIFICATIONS to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        },

        // Bluetooth permissions
        Permission.BLUETOOTH to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH
        } else {
            null
        },
        Permission.BLUETOOTH_ADMIN to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_ADMIN
        } else {
            null
        },
        Permission.BLUETOOTH_CONNECT to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            null
        },
        Permission.BLUETOOTH_SCAN to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            null
        },

        // Biometric
        Permission.USE_BIOMETRIC to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Manifest.permission.USE_BIOMETRIC
        } else {
            Manifest.permission.USE_FINGERPRINT
        }
    ).filterValues { it != null }.mapValues { it.value!! }

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
        Permission.READ_EXTERNAL_STORAGE to PermissionInfo(
            permission = Permission.READ_EXTERNAL_STORAGE,
            title = "Storage Access",
            description = "This permission is needed to read files from your device.",
            settingsDescription = "Please enable Storage Access in settings to allow the app to read files from your device."
        ),
        Permission.WRITE_EXTERNAL_STORAGE to PermissionInfo(
            permission = Permission.WRITE_EXTERNAL_STORAGE,
            title = "Storage Write Access",
            description = "This permission is needed to save files to your device.",
            settingsDescription = "Please enable Storage Write Access in settings to allow the app to save files to your device."
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
        Permission.READ_CALENDAR to PermissionInfo(
            permission = Permission.READ_CALENDAR,
            title = "Calendar Access",
            description = "This permission is needed to read your calendar events.",
            settingsDescription = "Please enable Calendar Access in settings to allow the app to read your calendar events."
        ),
        Permission.WRITE_CALENDAR to PermissionInfo(
            permission = Permission.WRITE_CALENDAR,
            title = "Calendar Write Access",
            description = "This permission is needed to create or modify calendar events.",
            settingsDescription = "Please enable Calendar Write Access in settings to allow the app to create or modify calendar events."
        ),
        Permission.READ_CONTACTS to PermissionInfo(
            permission = Permission.READ_CONTACTS,
            title = "Contacts Access",
            description = "This permission is needed to read your contacts.",
            settingsDescription = "Please enable Contacts Access in settings to allow the app to read your contacts."
        ),
        Permission.WRITE_CONTACTS to PermissionInfo(
            permission = Permission.WRITE_CONTACTS,
            title = "Contacts Write Access",
            description = "This permission is needed to create or modify contacts.",
            settingsDescription = "Please enable Contacts Write Access in settings to allow the app to create or modify contacts."
        ),
        Permission.READ_PHONE_STATE to PermissionInfo(
            permission = Permission.READ_PHONE_STATE,
            title = "Phone State Access",
            description = "This permission is needed to access information about your phone state.",
            settingsDescription = "Please enable Phone State Access in settings to allow the app to access information about your phone."
        ),
        Permission.CALL_PHONE to PermissionInfo(
            permission = Permission.CALL_PHONE,
            title = "Phone Call Access",
            description = "This permission is needed to make phone calls directly from the app.",
            settingsDescription = "Please enable Phone Call Access in settings to allow the app to make phone calls."
        ),
        Permission.READ_SMS to PermissionInfo(
            permission = Permission.READ_SMS,
            title = "SMS Access",
            description = "This permission is needed to read your SMS messages.",
            settingsDescription = "Please enable SMS Access in settings to allow the app to read your SMS messages."
        ),
        Permission.SEND_SMS to PermissionInfo(
            permission = Permission.SEND_SMS,
            title = "SMS Send Access",
            description = "This permission is needed to send SMS messages from the app.",
            settingsDescription = "Please enable SMS Send Access in settings to allow the app to send SMS messages."
        ),
        Permission.POST_NOTIFICATIONS to PermissionInfo(
            permission = Permission.POST_NOTIFICATIONS,
            title = "Notification Access",
            description = "This permission is needed to show notifications.",
            settingsDescription = "Please enable Notification Access in settings to allow the app to show notifications."
        ),
        Permission.BLUETOOTH to PermissionInfo(
            permission = Permission.BLUETOOTH,
            title = "Bluetooth Access",
            description = "This permission is needed to use Bluetooth features.",
            settingsDescription = "Please enable Bluetooth Access in settings to allow the app to use Bluetooth features."
        ),
        Permission.BLUETOOTH_ADMIN to PermissionInfo(
            permission = Permission.BLUETOOTH_ADMIN,
            title = "Bluetooth Admin Access",
            description = "This permission is needed to manage Bluetooth settings.",
            settingsDescription = "Please enable Bluetooth Admin Access in settings to allow the app to manage Bluetooth settings."
        ),
        Permission.BLUETOOTH_CONNECT to PermissionInfo(
            permission = Permission.BLUETOOTH_CONNECT,
            title = "Bluetooth Connect Access",
            description = "This permission is needed to connect to Bluetooth devices.",
            settingsDescription = "Please enable Bluetooth Connect Access in settings to allow the app to connect to Bluetooth devices."
        ),
        Permission.BLUETOOTH_SCAN to PermissionInfo(
            permission = Permission.BLUETOOTH_SCAN,
            title = "Bluetooth Scan Access",
            description = "This permission is needed to scan for Bluetooth devices.",
            settingsDescription = "Please enable Bluetooth Scan Access in settings to allow the app to scan for Bluetooth devices."
        ),
        Permission.USE_BIOMETRIC to PermissionInfo(
            permission = Permission.USE_BIOMETRIC,
            title = "Biometric Access",
            description = "This permission is needed to use biometric authentication (fingerprint, face recognition, etc.).",
            settingsDescription = "Please enable Biometric Access in settings to allow the app to use biometric authentication."
        )
    )

    override fun isPermissionGranted(permission: Permission): Boolean {
        val androidPermission = permissionMap[permission] ?: return true
        return ContextCompat.checkSelfPermission(
            /* context = */ TSAndroidApplication.instance,
            /* permission = */ androidPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun requestPermission(permission: Permission): Flow<PermissionResult> {
        val activity = activityRef.load()
        if (activity == null || singlePermissionLauncher == null) {
            return flowOf(
                if (isPermissionGranted(permission)) {
                    PermissionResult.GRANTED
                } else {
                    PermissionResult.DENIED
                }
            )
        }

        val androidPermission = permissionMap[permission]
            ?: return flowOf(PermissionResult.GRANTED)

        // If the permission is already granted, return immediately
        if (isPermissionGranted(permission)) {
            return flowOf(value = PermissionResult.GRANTED)
        }

        return callbackFlow {
            singlePermissionCallback = { isGranted ->
                if (isGranted) {
                    trySend(PermissionResult.GRANTED)
                } else {
                    val isPermanentlyDenied =
                        !activity.shouldShowRequestPermissionRationale(androidPermission)
                    trySend(if (isPermanentlyDenied) PermissionResult.PERMANENTLY_DENIED else PermissionResult.DENIED)
                }
                close()
            }

            // Use safe call to handle potential null
            singlePermissionLauncher?.launch(androidPermission)

            awaitClose {
                singlePermissionCallback = null
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun requestPermissions(permissions: List<Permission>): Flow<Map<Permission, PermissionResult>> {
        // Get the activity from WeakReference
        val activity = activityRef.load()

        // If the activity is not available, we can't request permissions
        if (activity == null || multiplePermissionsLauncher == null) {
            return flowOf(permissions.associateWith {
                if (isPermissionGranted(it)) PermissionResult.GRANTED
                else PermissionResult.DENIED
            })
        }

        // Filter out permissions that are already granted
        val permissionsToRequest = permissions.filter { !isPermissionGranted(it) }

        // If all permissions are already granted, return immediately
        if (permissionsToRequest.isEmpty()) {
            return flowOf(permissions.associateWith { PermissionResult.GRANTED })
        }

        // Map to Android permission strings
        val androidPermissions = permissionsToRequest.mapNotNull {
            permissionMap[it]
        }.toTypedArray()

        // If no valid Android permissions to request, return immediately
        if (androidPermissions.isEmpty()) {
            return flowOf(permissions.associateWith {
                PermissionResult.GRANTED
            })
        }

        return callbackFlow {
            multiplePermissionsCallback = { results ->
                val resultMap = mutableMapOf<Permission, PermissionResult>()

                // Add results for requested permissions
                permissionsToRequest.forEach { permission ->
                    val androidPermission = permissionMap[permission] ?: return@forEach
                    val isGranted = results[androidPermission] ?: false

                    if (isGranted) {
                        resultMap[permission] = PermissionResult.GRANTED
                    } else {
                        // Check if the user selected "Don't ask again"
                        val isPermanentlyDenied =
                            !activity.shouldShowRequestPermissionRationale(androidPermission)
                        resultMap[permission] =
                            if (isPermanentlyDenied) PermissionResult.PERMANENTLY_DENIED else PermissionResult.DENIED
                    }
                }

                // Add already granted permissions
                permissions.filter { !permissionsToRequest.contains(it) }.forEach {
                    resultMap[it] = PermissionResult.GRANTED
                }

                trySend(resultMap)
                close()
            }

            // Use safe call to handle potential null
            multiplePermissionsLauncher?.launch(androidPermissions)

            awaitClose {
                // Clean up
                multiplePermissionsCallback = null
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun showPermissionRationaleDialog(
        permission: Permission,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit,
    ) {
        // Get the activity from WeakReference
        val activity = activityRef.load()

        if (activity == null) {
            onNegativeClick()
            return
        }

        val permissionInfo = permissionInfoMap[permission] ?: PermissionInfo(
            permission = permission,
            title = "Permission Required",
            description = "This permission is required for the app to function properly.",
            settingsDescription = "Please enable this permission in settings to allow the app to function properly."
        )

        AlertDialog.Builder(activity)
            .setTitle(permissionInfo.title)
            .setMessage(permissionInfo.settingsDescription)
            .setPositiveButton("Go to Settings") { _, _ ->
                onPositiveClick()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onNegativeClick()
            }
            .setCancelable(false)
            .show()
    }

    override fun openAppSettings(): Boolean {
        return try {
            val context = TSAndroidApplication.instance.applicationContext
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Actual implementation of PermissionCheckerFactory for Android.
 */
actual object PermissionCheckerFactory : DefaultLifecycleObserver {
    @OptIn(ExperimentalAtomicApi::class)
    private var activityRef: AtomicReference<ComponentActivity?>? = null
    private var permissionChecker: AndroidPermissionChecker? = null

    /**
     * Initializes the factory with the Android application context and optionally an activity.
     * This must be called before using the factory.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun initialize(
        activity: ComponentActivity? = null,
        singlePermissionLauncher: ActivityResultLauncher<String>? = null,
        multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null,
    ) {
        this.activityRef = activity?.let { AtomicReference(it) }

        activity?.lifecycle?.addObserver(this)
        permissionChecker?.initializeLaunchers(
            activity,
            singleLauncher = singlePermissionLauncher,
            multipleLauncher = multiplePermissionsLauncher,
        )
    }

    /**
     * Creates an Android-specific PermissionChecker.
     * @return A PermissionChecker instance for Android.
     */
    @OptIn(ExperimentalAtomicApi::class)
    actual fun create(): PermissionChecker {
        if (permissionChecker == null) {
            permissionChecker = AndroidPermissionChecker(activityRef?.load())
        }

        return permissionChecker!!
    }

    /**
     * Cleans up resources when the activity is destroyed.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
        owner.lifecycle.removeObserver(this)
    }

    /**
     * Cleans up resources to prevent memory leaks.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun cleanup() {
        permissionChecker?.cleanup()
        permissionChecker = null
        activityRef?.load()?.let {
            it.lifecycle.removeObserver(this)
            activityRef?.compareAndSet(it, null)
        }
        activityRef = null
    }

    fun onSinglePermissionResult(result: Boolean) {
        permissionChecker?.singlePermissionCallback?.invoke(result)
    }

    fun onMultiplePermissionsResult(results: Map<String, Boolean>) {
        permissionChecker?.multiplePermissionsCallback?.invoke(results)
    }
}
