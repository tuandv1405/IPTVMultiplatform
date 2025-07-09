package tss.t.tsiptv.core.permission

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop implementation of PermissionChecker.
 * Desktop applications typically don't require runtime permissions,
 * so this implementation simply returns that all permissions are granted.
 */
class DesktopPermissionChecker : PermissionChecker {
    override fun isPermissionGranted(permission: Permission): Boolean {
        // Desktop applications typically don't require runtime permissions
        return true
    }

    override fun requestPermission(permission: Permission): Flow<PermissionResult> {
        // Since permissions are implicitly granted, we just return GRANTED
        return flowOf(PermissionResult.GRANTED)
    }

    override fun requestPermissions(permissions: List<Permission>): Flow<Map<Permission, PermissionResult>> {
        // Since permissions are implicitly granted, we just return GRANTED for all
        return flowOf(permissions.associateWith { PermissionResult.GRANTED })
    }
}

/**
 * Actual implementation of PermissionCheckerFactory for Desktop.
 */
actual object PermissionCheckerFactory {
    /**
     * Creates a Desktop-specific PermissionChecker.
     * @return A PermissionChecker instance for Desktop.
     */
    actual fun create(): PermissionChecker {
        return DesktopPermissionChecker()
    }
}