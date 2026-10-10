package tss.t.tsiptv.core.billing

/**
 * The store product ids of the two plans (PRD §5.1). Configurable at build time on Android
 * (`TSIPTV_BILLING_NOADS_ID`, `TSIPTV_BILLING_UNLIMITED_ID`).
 */
data class ProductCatalog(
    val noAdsId: String = DEFAULT_NO_ADS_ID,
    val unlimitedId: String = DEFAULT_UNLIMITED_ID,
) {
    val productIds: List<String> get() = listOf(noAdsId, unlimitedId)

    fun planOf(productId: String?): Plan = when (productId) {
        null -> Plan.FREE
        unlimitedId -> Plan.UNLIMITED
        noAdsId -> Plan.NO_ADS
        else -> Plan.FREE
    }

    fun productOf(plan: Plan): String? = when (plan) {
        Plan.FREE -> null
        Plan.NO_ADS -> noAdsId
        Plan.UNLIMITED -> unlimitedId
    }

    companion object {
        const val DEFAULT_NO_ADS_ID = "tsiptv_noads"
        const val DEFAULT_UNLIMITED_ID = "tsiptv_unlimited"
    }
}

/**
 * An ISO 8601 billing period as Play reports it (`P1M`, `P1Y`, `P1W`, `P7D`, `P3M`).
 * [days] is an approximation used only to compare prices per day.
 */
data class BillingPeriod(val years: Int = 0, val months: Int = 0, val weeks: Int = 0, val days: Int = 0) {
    val approxDays: Double get() = years * 365.0 + months * 30.4375 + weeks * 7.0 + days

    val isMonthly: Boolean get() = years == 0 && months == 1 && weeks == 0 && days == 0
    val isYearly: Boolean get() = (years == 1 && months == 0 || years == 0 && months == 12) && weeks == 0 && days == 0

    companion object {
        private val ISO = Regex("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?$")

        fun parse(iso: String?): BillingPeriod? {
            val m = ISO.matchEntire(iso?.trim()?.uppercase() ?: return null) ?: return null
            val (y, mo, w, d) = m.destructured
            val p = BillingPeriod(y.toIntOrNull() ?: 0, mo.toIntOrNull() ?: 0, w.toIntOrNull() ?: 0, d.toIntOrNull() ?: 0)
            return p.takeIf { it.approxDays > 0 }
        }
    }
}

/**
 * One purchasable base plan (with its offer) of a product, from Play's `ProductDetails`. Prices are
 * shown from [formattedPrice] only; nothing is hard-coded (PRD AC-SUB2).
 */
data class PlanOffer(
    val productId: String,
    val plan: Plan,
    val basePlanId: String,
    val offerId: String?,
    /** Opaque token passed back to the store when buying this offer. */
    val offerToken: String,
    /** The recurring price, e.g. "49.000 ₫". */
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val billingPeriod: BillingPeriod,
    /** Length of a free trial phase in this offer (null = none, or the user is not eligible). */
    val freeTrialPeriod: BillingPeriod? = null,
) {
    /** Price per day, for choosing the replacement mode (PRD §3.3). */
    val pricePerDayMicros: Double get() = priceAmountMicros / billingPeriod.approxDays
}

/** A subscription purchase the store reports for this device's account. */
data class OwnedPurchase(
    val productId: String,
    val purchaseToken: String,
    val state: OwnedPurchaseState,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
    /** The account hash set at purchase time (`obfuscatedAccountId`), when there was one. */
    val accountHash: String? = null,
    val purchaseTimeMs: Long = 0,
)

enum class OwnedPurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** What the store said about the owned subscriptions. */
sealed interface PlayPurchases {
    /** Not asked yet (start-up) or the store is still connecting. */
    data object Unknown : PlayPurchases

    /** The store cannot be used here (no Play Store, billing unavailable, iOS, desktop). */
    data object Unavailable : PlayPurchases

    /** Asked, but the call failed (offline, service error): keep the cache. */
    data object Failed : PlayPurchases

    data class Loaded(val purchases: List<OwnedPurchase>) : PlayPurchases
}

/** `users/{uid}/entitlements/current` as the billing server writes it (PRD §5.3). */
data class ServerEntitlement(
    val plan: Plan,
    val active: Boolean,
    val status: SubscriptionStatus,
    val productId: String?,
    val basePlanId: String?,
    val expiresAtMs: Long?,
    val autoRenewing: Boolean?,
)

/** The last confirmed plan, kept so a subscriber never sees ads flash at start-up (PRD §2.1). */
data class CachedEntitlement(val plan: Plan, val productId: String?, val confirmedAtMs: Long)

/** Result of starting a purchase. */
enum class PurchaseLaunch {
    /** The store sheet is open; the result arrives as a [BillingEvent]. */
    STARTED,

    /** Unlimited needs an account first. */
    SIGN_IN_REQUIRED,

    /** The store is not available here. */
    UNAVAILABLE,
    FAILED,
}

/** One-shot results of the store flow, for messages on the plans screen. */
enum class BillingEvent {
    PURCHASED,
    PENDING,
    CANCELED,
    ALREADY_OWNED,
    FAILED,
    RESTORED,
    NOTHING_TO_RESTORE,
}
