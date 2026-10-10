package tss.t.tsiptv.core.billing

import okio.ByteString.Companion.encodeUtf8

/** Play Billing's subscription replacement modes (names match `ReplacementMode`). */
enum class ReplacementMode {
    WITH_TIME_PRORATION,
    CHARGE_PRORATED_PRICE,
    CHARGE_FULL_PRICE,
    WITHOUT_PRORATION,
    DEFERRED,
}

/** What the plans screen offers for a plan card, given the current subscription. */
enum class PlanAction { SUBSCRIBE, UPGRADE, CHANGE, CURRENT, DOWNGRADE }

/** Pure rules for changing plans (docs/prd-subscriptions.md §3.3). */
object ReplacementPolicy {

    /**
     * The replacement mode to move from the current subscription ([currentProductId] of
     * [currentPlan]; [currentOffer] is its base plan when known, Play's local purchase does not say)
     * to [target]. null = a new purchase.
     */
    fun modeFor(currentProductId: String?, currentPlan: Plan, currentOffer: PlanOffer?, target: PlanOffer): ReplacementMode? {
        if (currentProductId == null) return null
        return when {
            // Another product of a lower plan: keep what was paid for until renewal.
            target.plan.rank < currentPlan.rank -> ReplacementMode.DEFERRED
            // Same product: a longer period starts now (unused time credited), a shorter one at renewal.
            currentProductId == target.productId -> {
                val from = currentOffer?.billingPeriod?.approxDays ?: return ReplacementMode.WITH_TIME_PRORATION
                if (target.billingPeriod.approxDays < from) ReplacementMode.DEFERRED else ReplacementMode.WITH_TIME_PRORATION
            }
            // Upgrade: Play allows CHARGE_PRORATED_PRICE only when the new plan costs more per unit of time.
            currentOffer != null && target.pricePerDayMicros > currentOffer.pricePerDayMicros -> ReplacementMode.CHARGE_PRORATED_PRICE
            else -> ReplacementMode.WITH_TIME_PRORATION
        }
    }

    fun actionFor(current: Entitlement, target: Plan): PlanAction = when {
        target == Plan.FREE -> PlanAction.CURRENT
        current.plan == target -> PlanAction.CURRENT
        current.plan == Plan.FREE -> PlanAction.SUBSCRIBE
        target.rank > current.plan.rank -> PlanAction.UPGRADE
        else -> PlanAction.DOWNGRADE
    }

    /**
     * Play's subscription centre (PRD §3.1): the product's page when there is one, else the list.
     * Never a link to another payment method.
     */
    fun manageUrl(packageName: String, productId: String?): String =
        if (productId.isNullOrBlank()) "https://play.google.com/store/account/subscriptions?package=$packageName"
        else "https://play.google.com/store/account/subscriptions?sku=$productId&package=$packageName"

    /**
     * `obfuscatedAccountId` for the purchase (AC-SUB11): SHA-256 of the uid as 64 hex characters,
     * never the email or the uid itself.
     */
    fun accountHash(uid: String): String = uid.encodeUtf8().sha256().hex()

    const val PACKAGE_NAME = "tss.t.tsiptv"
}
