package tss.t.tsiptv.core.permission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.firstOrNull

/**
 * Example of how to use the permission system.
 * This composable demonstrates how to check and request permissions,
 * and how to show the custom dialog before redirecting to settings.
 *
 * @param permissionChecker The permission checker to use
 * @param permission The permission to check and request
 */
@Composable
fun PermissionExample(
    permissionChecker: PermissionChecker,
    permission: Permission,
) {
    var permissionResult by remember { mutableStateOf<PermissionResult?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    // Get permission info
    val permissionInfo = remember {
        when (permission) {
            Permission.CAMERA -> PermissionInfo(
                permission = Permission.CAMERA,
                title = "Camera Permission",
                description = "This app needs access to your camera to take photos.",
                settingsDescription = "Please enable camera access in your device settings to use this feature."
            )

            Permission.RECORD_AUDIO -> PermissionInfo(
                permission = Permission.RECORD_AUDIO,
                title = "Microphone Permission",
                description = "This app needs access to your microphone to record audio.",
                settingsDescription = "Please enable microphone access in your device settings to use this feature."
            )

            Permission.ACCESS_FINE_LOCATION -> PermissionInfo(
                permission = Permission.ACCESS_FINE_LOCATION,
                title = "Location Permission",
                description = "This app needs access to your location to show nearby places.",
                settingsDescription = "Please enable location access in your device settings to use this feature."
            )

            else -> PermissionInfo(
                permission = permission,
                title = "${permission.name} Permission",
                description = "This app needs ${permission.name.lowercase()} permission to function properly.",
                settingsDescription = "Please enable ${permission.name.lowercase()} permission in your device settings to use this feature."
            )
        }
    }

    // Check if permission is granted
    val isPermissionGranted = permissionChecker.isPermissionGranted(permission)

    // Request permission when needed
    var shouldRequestPermission by remember { mutableStateOf(false) }

    LaunchedEffect(shouldRequestPermission) {
        println("PermissionExample: shouldRequestPermission = $shouldRequestPermission")
        if (shouldRequestPermission) {
            val result = permissionChecker
                .requestPermission(permission)
                .firstOrNull()
            permissionResult = result
            isPermanentlyDenied = result == PermissionResult.PERMANENTLY_DENIED
            shouldRequestPermission = false
        }
    }

    // Show permission dialog if needed
    if (showPermissionDialog) {
        PermissionDialog(
            permissionInfo = permissionInfo,
            isPermanentlyDenied = isPermanentlyDenied,
            onDismiss = { showPermissionDialog = false },
            onOkClick = {
                showPermissionDialog = false
                shouldRequestPermission = true
            },
            onGoToSettingsClick = {
                showPermissionDialog = false
                permissionChecker.openAppSettings()
            }
        )
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Permission Example",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Permission: ${permission.name}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Status: ${if (isPermissionGranted) "Granted" else "Not Granted"}",
            style = MaterialTheme.typography.bodyMedium
        )

        if (permissionResult != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last request result: $permissionResult",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showPermissionDialog = true }
        ) {
            Text("Request Permission")
        }
    }
}

/**
 * Example of how to use the permission system with multiple permissions.
 * This composable demonstrates how to check and request multiple permissions,
 * and how to show the custom dialog before redirecting to settings.
 *
 * @param permissionChecker The permission checker to use
 * @param permissions The permissions to check and request
 */
@Composable
fun MultiPermissionExample(
    permissionChecker: PermissionChecker,
    permissions: List<Permission>,
) {
    var permissionResults by remember { mutableStateOf<Map<Permission, PermissionResult>>(emptyMap()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Get permission info map
    val permissionInfoMap = remember {
        permissions.associateWith { permission ->
            when (permission) {
                Permission.CAMERA -> PermissionInfo(
                    permission = Permission.CAMERA,
                    title = "Camera Permission",
                    description = "This app needs access to your camera to take photos.",
                    settingsDescription = "Please enable camera access in your device settings to use this feature."
                )

                Permission.RECORD_AUDIO -> PermissionInfo(
                    permission = Permission.RECORD_AUDIO,
                    title = "Microphone Permission",
                    description = "This app needs access to your microphone to record audio.",
                    settingsDescription = "Please enable microphone access in your device settings to use this feature."
                )

                Permission.ACCESS_FINE_LOCATION -> PermissionInfo(
                    permission = Permission.ACCESS_FINE_LOCATION,
                    title = "Location Permission",
                    description = "This app needs access to your location to show nearby places.",
                    settingsDescription = "Please enable location access in your device settings to use this feature."
                )

                else -> PermissionInfo(
                    permission = permission,
                    title = "${permission.name} Permission",
                    description = "This app needs ${permission.name.lowercase()} permission to function properly.",
                    settingsDescription = "Please enable ${permission.name.lowercase()} permission in your device settings to use this feature."
                )
            }
        }
    }

    // Check if permissions are granted
    val permissionStatuses = permissions.associateWith { permissionChecker.isPermissionGranted(it) }
    val allPermissionsGranted = permissionStatuses.values.all { it }

    // Request permissions when needed
    var shouldRequestPermissions by remember { mutableStateOf(false) }

    LaunchedEffect(shouldRequestPermissions) {
        if (shouldRequestPermissions) {
            val results = permissionChecker.requestPermissions(permissions).firstOrNull()
            if (results != null) {
                permissionResults = results
            }
            shouldRequestPermissions = false
        }
    }

    // Show permission dialog if needed
    if (showPermissionDialog) {
        MultiPermissionDialog(
            permissions = permissions,
            permissionResults = permissionResults,
            permissionInfoMap = permissionInfoMap,
            onDismiss = { showPermissionDialog = false },
            onOkClick = {
                showPermissionDialog = false
                shouldRequestPermissions = true
            },
            onGoToSettingsClick = {
                showPermissionDialog = false
                permissionChecker.openAppSettings()
            }
        )
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Multi-Permission Example",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Permissions: ${permissions.joinToString(", ") { it.name }}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Status: ${if (allPermissionsGranted) "All Granted" else "Some Not Granted"}",
            style = MaterialTheme.typography.bodyMedium
        )

        permissions.forEach { permission ->
            Spacer(modifier = Modifier.height(4.dp))

            val status = permissionStatuses[permission] ?: false
            val result = permissionResults[permission]

            Text(
                text = "${permission.name}: ${if (status) "Granted" else "Not Granted"}${if (result != null) " (Last result: $result)" else ""}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showPermissionDialog = true }
        ) {
            Text("Request Permissions")
        }
    }
}
