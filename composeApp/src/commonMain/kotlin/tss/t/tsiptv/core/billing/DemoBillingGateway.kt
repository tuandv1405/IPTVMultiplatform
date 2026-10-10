package tss.t.tsiptv.core.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * QA only (debug builds with `-Ptsiptv.debugDemoBilling=true`): shows plan cards with sample prices
 * marked "(demo)" so layouts and D-pad focus can be checked without a Play testing track. Nothing can
 * be bought ([launchPurchase] fails) and it never reports a purchase, so it can't grant anything.
 */
class DemoBillingGateway(override val catalog: ProductCatalog = ProductCatalog()) : BillingGateway {
    override val isSupported: Boolean = true
    override val purchases: StateFlow<PlayPurchases> = MutableStateFlow(PlayPurchases.Loaded(emptyList())).asStateFlow()
    override val offers: StateFlow<List<PlanOffer>> = MutableStateFlow(
        listOf(
            offer(Plan.NO_ADS, "monthly", 25_000_000_000, "25.000 ₫ (demo)", "P1M"),
            offer(Plan.NO_ADS, "yearly", 199_000_000_000, "199.000 ₫ (demo)", "P1Y"),
            offer(Plan.UNLIMITED, "monthly", 49_000_000_000, "49.000 ₫ (demo)", "P1M", trial = "P7D"),
            offer(Plan.UNLIMITED, "yearly", 399_000_000_000, "399.000 ₫ (demo)", "P1Y"),
        )
    ).asStateFlow()
    override val events: Flow<BillingEvent> = emptyFlow()
    override fun refresh() = Unit
    override suspend fun restore(): Boolean = true
    override suspend fun launchPurchase(offer: PlanOffer, accountHash: String?, replace: ReplaceFrom?) = PurchaseLaunch.FAILED

    private fun offer(plan: Plan, basePlan: String, micros: Long, price: String, period: String, trial: String? = null) = PlanOffer(
        productId = catalog.productOf(plan)!!,
        plan = plan,
        basePlanId = basePlan,
        offerId = trial?.let { "trial" },
        offerToken = "demo-$basePlan",
        formattedPrice = price,
        priceAmountMicros = micros,
        priceCurrencyCode = "VND",
        billingPeriod = BillingPeriod.parse(period)!!,
        freeTrialPeriod = trial?.let(BillingPeriod::parse),
    )
}
