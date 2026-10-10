package tss.t.tsiptv.core.billing

/**
 * The subscription plans (docs/prd-subscriptions.md §2), in order: every gate asks "at least
 * [NO_ADS]?" ([Entitlement.noAds]) or "[UNLIMITED]?" ([Entitlement.unlimited]), never a product id.
 */
enum class Plan(val rank: Int, val wireName: String) {
    FREE(0, "free"),
    NO_ADS(1, "no_ads"),
    UNLIMITED(2, "unlimited");

    companion object {
        fun fromWire(value: String?): Plan = entries.firstOrNull { it.wireName == value } ?: FREE
    }
}

/** Where the current plan comes from (PRD §2.1). */
enum class EntitlementSource {
    /** Nothing known yet / free. */
    NONE,

    /** The last confirmed plan, kept locally for at most [EntitlementResolver.CACHE_MAX_AGE_MS]. */
    CACHE,

    /** This device's Google Play account (`queryPurchasesAsync`), not verified by our server. */
    PLAY,

    /** `users/{uid}/entitlements/current`, written by the billing server. The Firestore rules know it. */
    SERVER,
}

/** Google Play's subscription states as far as the app shows them (PRD §3.4). */
enum class SubscriptionStatus {
    NONE, ACTIVE, CANCELED, IN_GRACE_PERIOD, ON_HOLD, PAUSED, EXPIRED, PENDING;

    companion object {
        /** The server's `state` field (Play's `SUBSCRIPTION_STATE_*` without the prefix). */
        fun fromWire(value: String?): SubscriptionStatus {
            val name = value?.removePrefix("SUBSCRIPTION_STATE_")?.uppercase() ?: return NONE
            return entries.firstOrNull { it.name == name } ?: when (name) {
                "PENDING_PURCHASE_CANCELED", "REVOKED" -> EXPIRED
                else -> NONE
            }
        }
    }
}

/**
 * What the user may use right now. Built by [EntitlementResolver]; read by the ads gate, the quota
 * policy, the rewarded tasks and the plans screen.
 */
data class Entitlement(
    val plan: Plan = Plan.FREE,
    val source: EntitlementSource = EntitlementSource.NONE,
    val status: SubscriptionStatus = SubscriptionStatus.NONE,
    val productId: String? = null,
    val basePlanId: String? = null,
    /** Access end (server only; Play's local purchases carry no expiry). */
    val expiresAtMs: Long? = null,
    val autoRenewing: Boolean? = null,
) {
    /** No ads of any kind (AdMob and the Shopee fallback). */
    val noAds: Boolean get() = plan.rank >= Plan.NO_ADS.rank

    /** No daily caps on send-to-TV and sync; rewarded tasks hidden. */
    val unlimited: Boolean get() = plan == Plan.UNLIMITED

    /** The billing server confirmed it, so the Firestore rules apply the plan's caps too. */
    val verified: Boolean get() = source == EntitlementSource.SERVER && plan != Plan.FREE

    companion object {
        val FREE = Entitlement()
    }
}
