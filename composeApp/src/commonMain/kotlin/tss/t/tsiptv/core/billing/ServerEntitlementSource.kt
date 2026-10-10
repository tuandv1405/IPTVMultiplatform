package tss.t.tsiptv.core.billing

import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * `users/{uid}/entitlements/current`, written only by the billing server (docs/prd-subscriptions.md
 * §5.3) and read-only for the owner (firestore.rules).
 */
interface ServerEntitlementSource {
    /** The document of [uid] (null when it does not exist). The flow fails when it cannot be read. */
    fun observe(uid: String): Flow<ServerEntitlement?>
}

/** No server (tests, builds without Firebase). */
object NoServerEntitlementSource : ServerEntitlementSource {
    override fun observe(uid: String): Flow<ServerEntitlement?> = flowOf(null)
}

@Serializable
internal data class ServerEntitlementDoc(
    val v: Long = 1,
    val plan: String = "free",
    val active: Boolean = false,
    val state: String? = null,
    val productId: String? = null,
    val basePlanId: String? = null,
    val expiresAt: Timestamp? = null,
    val autoRenewing: Boolean? = null,
) {
    fun toModel() = ServerEntitlement(
        plan = Plan.fromWire(plan),
        active = active,
        status = SubscriptionStatus.fromWire(state),
        productId = productId,
        basePlanId = basePlanId,
        expiresAtMs = expiresAt?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 },
        autoRenewing = autoRenewing,
    )
}

class FirestoreServerEntitlementSource(private val firestore: FirebaseFirestore) : ServerEntitlementSource {
    override fun observe(uid: String): Flow<ServerEntitlement?> =
        firestore.collection("users").document(uid).collection("entitlements").document("current")
            .snapshots
            .map { snap -> if (snap.exists) snap.data(ServerEntitlementDoc.serializer()).toModel() else null }
}
