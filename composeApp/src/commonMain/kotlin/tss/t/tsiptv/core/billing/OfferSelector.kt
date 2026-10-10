package tss.t.tsiptv.core.billing

/** One pricing phase of a store offer (Play `PricingPhase`). */
data class StorePhase(
    val priceAmountMicros: Long,
    val formattedPrice: String,
    val priceCurrencyCode: String,
    /** ISO 8601, e.g. `P1M`. */
    val billingPeriod: String,
    val billingCycleCount: Int,
    /** True for the phase that repeats until cancelled (Play `RecurrenceMode.INFINITE_RECURRING`). */
    val infiniteRecurring: Boolean,
)

/** One subscription offer as the store returns it (Play `SubscriptionOfferDetails`). */
data class StoreOffer(
    val basePlanId: String,
    /** null for the base plan's own offer. */
    val offerId: String?,
    val offerToken: String,
    val phases: List<StorePhase>,
)

/**
 * Turns the store's offers into one [PlanOffer] per base plan (docs/prd-subscriptions.md §3.1):
 * the store only returns offers the user is eligible for, so a free-trial offer, when present, is
 * preferred; otherwise the base plan. Pure, so the Play mapping stays thin.
 */
object OfferSelector {

    fun select(productId: String, plan: Plan, offers: List<StoreOffer>): List<PlanOffer> =
        offers.groupBy { it.basePlanId }.mapNotNull { (basePlanId, group) ->
            val candidates = group.mapNotNull { toPlanOffer(productId, plan, basePlanId, it) }
            candidates.firstOrNull { it.freeTrialPeriod != null } ?: candidates.firstOrNull { it.offerId == null } ?: candidates.firstOrNull()
        }.sortedWith(compareBy({ it.plan.rank }, { it.billingPeriod.approxDays }))

    private fun toPlanOffer(productId: String, plan: Plan, basePlanId: String, offer: StoreOffer): PlanOffer? {
        val recurring = offer.phases.lastOrNull { it.infiniteRecurring } ?: offer.phases.lastOrNull() ?: return null
        val period = BillingPeriod.parse(recurring.billingPeriod) ?: return null
        if (recurring.priceAmountMicros <= 0) return null
        val trial = offer.phases.firstOrNull { it.priceAmountMicros == 0L && !it.infiniteRecurring }
            ?.let { phase -> BillingPeriod.parse(phase.billingPeriod)?.times(phase.billingCycleCount.coerceAtLeast(1)) }
        return PlanOffer(
            productId = productId,
            plan = plan,
            basePlanId = basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            formattedPrice = recurring.formattedPrice,
            priceAmountMicros = recurring.priceAmountMicros,
            priceCurrencyCode = recurring.priceCurrencyCode,
            billingPeriod = period,
            freeTrialPeriod = trial,
        )
    }

    private fun BillingPeriod.times(n: Int) = BillingPeriod(years * n, months * n, weeks * n, days * n)
}
