package tss.t.tsiptv.core.firebase.models

import kotlinx.serialization.Serializable

/**
 * Data class representing a user account deactivation request.
 *
 * @property userId The user's unique ID who requested deactivation
 * @property timestamp The timestamp when the request was created (milliseconds since epoch)
 * @property reason Optional reason provided by the user for deactivation
 * @property status The current status of the deactivation request
 * @property email The user's email for reference
 */
@Serializable
data class DeactivationRequest(
    val userId: String,
    val timestamp: Long,
    val reason: String? = null,
    val status: DeactivationStatus = DeactivationStatus.PENDING,
    val email: String? = null
)

/**
 * Enum representing the status of a deactivation request.
 */
@Serializable
enum class DeactivationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    PROCESSED
}