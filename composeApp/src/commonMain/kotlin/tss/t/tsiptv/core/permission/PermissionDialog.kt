package tss.t.tsiptv.core.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A composable function that displays a permission dialog.
 * This dialog can be used to explain why a permission is needed and to guide the user
 * to grant the permission either through the system dialog or through the app settings.
 *
 * @param permissionInfo The information about the permission being requested
 * @param isPermanentlyDenied Whether the permission has been permanently denied
 * @param onDismiss Callback for when the dialog is dismissed
 * @param onOkClick Callback for when the user clicks the OK button
 * @param onGoToSettingsClick Callback for when the user clicks the "Go to Settings" button
 */
@Composable
fun PermissionDialog(
    permissionInfo: PermissionInfo,
    isPermanentlyDenied: Boolean,
    onDismiss: () -> Unit,
    onOkClick: () -> Unit,
    onGoToSettingsClick: () -> Unit
) {
    val buttonText = if (isPermanentlyDenied) "Go to Settings" else "OK"
    val buttonAction = if (isPermanentlyDenied) onGoToSettingsClick else onOkClick

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = permissionInfo.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (isPermanentlyDenied) permissionInfo.settingsDescription else permissionInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
            }
        },
        confirmButton = {
            Button(onClick = buttonAction) {
                Text(buttonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A composable function that displays a permission dialog for multiple permissions.
 * This dialog can be used to explain why multiple permissions are needed and to guide the user
 * to grant the permissions either through the system dialog or through the app settings.
 *
 * @param permissions The list of permissions being requested
 * @param permissionResults The results of the permission requests
 * @param onDismiss Callback for when the dialog is dismissed
 * @param onOkClick Callback for when the user clicks the OK button
 * @param onGoToSettingsClick Callback for when the user clicks the "Go to Settings" button
 */
@Composable
fun MultiPermissionDialog(
    permissions: List<Permission>,
    permissionResults: Map<Permission, PermissionResult>,
    permissionInfoMap: Map<Permission, PermissionInfo>,
    onDismiss: () -> Unit,
    onOkClick: () -> Unit,
    onGoToSettingsClick: () -> Unit
) {
    // Check if any permission is permanently denied
    val hasPermanentlyDenied = permissionResults.values.any { it == PermissionResult.PERMANENTLY_DENIED }
    val buttonText = if (hasPermanentlyDenied) "Go to Settings" else "OK"
    val buttonAction = if (hasPermanentlyDenied) onGoToSettingsClick else onOkClick

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "The following permissions are required for this feature:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                permissions.forEach { permission ->
                    val permissionInfo = permissionInfoMap[permission] ?: PermissionInfo(
                        permission = permission,
                        title = permission.name,
                        description = "This permission is required.",
                        settingsDescription = "Please enable this permission in settings."
                    )

                    val result = permissionResults[permission] ?: PermissionResult.DENIED
                    val isPermanentlyDenied = result == PermissionResult.PERMANENTLY_DENIED

                    Column(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = permissionInfo.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (isPermanentlyDenied) permissionInfo.settingsDescription else permissionInfo.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = buttonAction) {
                Text(buttonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
