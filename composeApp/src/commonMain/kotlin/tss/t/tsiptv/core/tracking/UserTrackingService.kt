package tss.t.tsiptv.core.tracking

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import kotlinx.coroutines.flow.first
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.core.permission.Permission
import tss.t.tsiptv.core.permission.PermissionChecker
import tss.t.tsiptv.core.permission.PermissionResult

/**
 * Service responsible for managing user tracking permissions and Firebase Analytics user ID.
 * Handles App Tracking Transparency (ATT) permission requests and sets user email as
 * Firebase Analytics user ID only when tracking permission is granted.
 */
interface UserTrackingService {
    /**
     * Requests App Tracking Transparency permission and sets up user tracking based on the result.
     * @param user Current Firebase user, or null if no user is authenticated
     */
    suspend fun requestTrackingPermissionAndSetupAnalytics(user: FirebaseUser?)
    
    /**
     * Updates Firebase Analytics user ID based on current tracking permission status.
     * @param user Current Firebase user, or null if no user is authenticated
     */
    suspend fun updateAnalyticsUserIdIfAllowed(user: FirebaseUser?)
    
    /**
     * Checks if tracking permission is granted.
     * @return true if App Tracking Transparency permission is granted, false otherwise
     */
    fun isTrackingAllowed(): Boolean
}

/**
 * Default implementation of UserTrackingService.
 */
class DefaultUserTrackingService(
    private val permissionChecker: PermissionChecker
) : UserTrackingService {
    
    override suspend fun requestTrackingPermissionAndSetupAnalytics(user: FirebaseUser?) {
        try {
            // Request App Tracking Transparency permission
            val result = permissionChecker.requestPermission(Permission.APP_TRACKING_TRANSPARENCY).first()
            
            // Set up analytics based on permission result
            when (result) {
                PermissionResult.GRANTED -> {
                    // Permission granted - set user email as Firebase Analytics user ID
                    user?.email?.let { email ->
                        Firebase.analytics.setUserId(email)
                        println("[UserTrackingService] ATT permission granted - set user ID: $email")
                    }
                }
                PermissionResult.DENIED, PermissionResult.PERMANENTLY_DENIED -> {
                    // Permission denied - clear any existing user ID
                    Firebase.analytics.setUserId(null)
                    println("[UserTrackingService] ATT permission denied - cleared user ID")
                }
            }
        } catch (e: Exception) {
            println("[UserTrackingService] Error requesting tracking permission: ${e.message}")
            // On error, don't set user ID to be safe
            Firebase.analytics.setUserId(null)
        }
    }
    
    override suspend fun updateAnalyticsUserIdIfAllowed(user: FirebaseUser?) {
        if (isTrackingAllowed()) {
            // Only set user ID if tracking is allowed
            user?.email?.let { email ->
                Firebase.analytics.setUserId(email)
                println("[UserTrackingService] Tracking allowed - updated user ID: $email")
            }
        } else {
            // Clear user ID if tracking is not allowed
            Firebase.analytics.setUserId(null)
            println("[UserTrackingService] Tracking not allowed - cleared user ID")
        }
    }
    
    override fun isTrackingAllowed(): Boolean {
        return permissionChecker.isPermissionGranted(Permission.APP_TRACKING_TRANSPARENCY)
    }
}