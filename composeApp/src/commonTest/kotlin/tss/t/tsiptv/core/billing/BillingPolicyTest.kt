package tss.t.tsiptv.core.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Offers, replacement modes, the manage link and the account hash (docs/prd-subscriptions.md §3, §5). */
class BillingPolicyTest {
    private val catalog = ProductCatalog()

    private fun phase(micros: Long, period: String, infinite: Boolean = true, cycles: Int = 0, price: String = "$micros") =
        StorePhase(micros, price, "VND", period, cycles, infinite)

    private fun offer(plan: Plan, basePlan: String, micros: Long, period: String) = PlanOffer(
        productId = catalog.productOf(plan)!!,
        plan = plan,
        basePlanId = basePlan,
        offerId = null,
        offerToken = "t-$basePlan",
        formattedPrice = "$micros",
        priceAmountMicros = micros,
        priceCurrencyCode = "VND",
        billingPeriod = BillingPeriod.parse(period)!!,
    )

    @Test
    fun periodsParse() {
        assertTrue(BillingPeriod.parse("P1M")!!.isMonthly)
        assertTrue(BillingPeriod.parse("P1Y")!!.isYearly)
        assertTrue(BillingPeriod.parse("P12M")!!.isYearly)
        assertEquals(7.0, BillingPeriod.parse("P1W")!!.approxDays)
        assertEquals(3.0, BillingPeriod.parse("P3D")!!.approxDays)
        assertNull(BillingPeriod.parse("1M"))
        assertNull(BillingPeriod.parse("P"))
        assertNull(BillingPeriod.parse(null))
    }

    @Test
    fun offerSelectorPrefersAnEligibleTrialAndReadsTheRecurringPrice() {
        val offers = listOf(
            StoreOffer("monthly", null, "base-m", listOf(phase(49_000_000_000, "P1M", price = "49.000 ₫"))),
            StoreOffer(
                "monthly", "trial", "trial-m",
                listOf(phase(0, "P1W", infinite = false, cycles = 1), phase(49_000_000_000, "P1M", price = "49.000 ₫")),
            ),
            StoreOffer("yearly", null, "base-y", listOf(phase(399_000_000_000, "P1Y", price = "399.000 ₫"))),
        )
        val selected = OfferSelector.select(catalog.unlimitedId, Plan.UNLIMITED, offers)
        assertEquals(listOf("monthly", "yearly"), selected.map { it.basePlanId })
        val monthly = selected[0]
        assertEquals("trial-m", monthly.offerToken)
        assertEquals("49.000 ₫", monthly.formattedPrice)
        assertEquals(7.0, monthly.freeTrialPeriod!!.approxDays)
        assertNull(selected[1].freeTrialPeriod)
        // A trial of 3 cycles of a day.
        val days = OfferSelector.select(
            catalog.noAdsId, Plan.NO_ADS,
            listOf(StoreOffer("monthly", "t", "x", listOf(phase(0, "P1D", infinite = false, cycles = 3), phase(1, "P1M")))),
        )
        assertEquals(3.0, days.single().freeTrialPeriod!!.approxDays)
        // Broken offers are skipped.
        assertTrue(OfferSelector.select(catalog.noAdsId, Plan.NO_ADS, listOf(StoreOffer("m", null, "x", emptyList()))).isEmpty())
    }

    @Test
    fun replacementModes() {
        val noAdsMonthly = offer(Plan.NO_ADS, "monthly", 25_000_000_000, "P1M")
        val noAdsYearly = offer(Plan.NO_ADS, "yearly", 199_000_000_000, "P1Y")
        val unlimitedMonthly = offer(Plan.UNLIMITED, "monthly", 49_000_000_000, "P1M")
        val cheapUnlimitedYearly = offer(Plan.UNLIMITED, "yearly", 100_000_000_000, "P1Y")

        // Nothing owned: a new purchase.
        assertNull(ReplacementPolicy.modeFor(null, Plan.FREE, null, unlimitedMonthly))
        // Upgrade to a plan that costs more per day: prorated charge, immediate access.
        assertEquals(ReplacementMode.CHARGE_PRORATED_PRICE, ReplacementPolicy.modeFor(catalog.noAdsId, Plan.NO_ADS, noAdsMonthly, unlimitedMonthly))
        // Upgrade to a plan that costs less per day (monthly -> cheap yearly): time proration.
        assertEquals(ReplacementMode.WITH_TIME_PRORATION, ReplacementPolicy.modeFor(catalog.noAdsId, Plan.NO_ADS, noAdsMonthly, cheapUnlimitedYearly))
        // Upgrade with the current base plan unknown: always-valid time proration.
        assertEquals(ReplacementMode.WITH_TIME_PRORATION, ReplacementPolicy.modeFor(catalog.noAdsId, Plan.NO_ADS, null, unlimitedMonthly))
        // Downgrade: deferred to the renewal.
        assertEquals(ReplacementMode.DEFERRED, ReplacementPolicy.modeFor(catalog.unlimitedId, Plan.UNLIMITED, null, noAdsMonthly))
        // Same product: longer period now, shorter one at renewal.
        assertEquals(ReplacementMode.WITH_TIME_PRORATION, ReplacementPolicy.modeFor(catalog.noAdsId, Plan.NO_ADS, noAdsMonthly, noAdsYearly))
        assertEquals(ReplacementMode.DEFERRED, ReplacementPolicy.modeFor(catalog.noAdsId, Plan.NO_ADS, noAdsYearly, noAdsMonthly))
    }

    @Test
    fun planActions() {
        val free = Entitlement.FREE
        val noAds = Entitlement(plan = Plan.NO_ADS, source = EntitlementSource.PLAY)
        assertEquals(PlanAction.SUBSCRIBE, ReplacementPolicy.actionFor(free, Plan.UNLIMITED))
        assertEquals(PlanAction.UPGRADE, ReplacementPolicy.actionFor(noAds, Plan.UNLIMITED))
        assertEquals(PlanAction.CURRENT, ReplacementPolicy.actionFor(noAds, Plan.NO_ADS))
        assertEquals(PlanAction.DOWNGRADE, ReplacementPolicy.actionFor(Entitlement(plan = Plan.UNLIMITED), Plan.NO_ADS))
    }

    @Test
    fun manageLinkAndAccountHash() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=tsiptv_unlimited&package=tss.t.tsiptv",
            ReplacementPolicy.manageUrl("tss.t.tsiptv", "tsiptv_unlimited"),
        )
        assertEquals("https://play.google.com/store/account/subscriptions?package=tss.t.tsiptv", ReplacementPolicy.manageUrl("tss.t.tsiptv", null))
        val hash = ReplacementPolicy.accountHash("uid-123")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
        assertFalse("uid-123" in hash)
        // Same as the server (sha256 of the uid's UTF-8 bytes, lowercase hex).
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ReplacementPolicy.accountHash("abc"))
    }

    @Test
    fun catalogMapsProducts() {
        val custom = ProductCatalog(noAdsId = "a", unlimitedId = "b")
        assertEquals(Plan.NO_ADS, custom.planOf("a"))
        assertEquals(Plan.UNLIMITED, custom.planOf("b"))
        assertEquals(Plan.FREE, custom.planOf("tsiptv_noads"))
        assertEquals("b", custom.productOf(Plan.UNLIMITED))
    }

    @Test
    fun verifierAcceptsOnlyAnEntitlementAnswer() {
        assertTrue(HttpPurchaseVerifier.accepted("""{"entitlement":{"plan":"unlimited","active":true}}"""))
        assertFalse(HttpPurchaseVerifier.accepted("""{"error":"linked_to_other_account"}"""))
        assertFalse(HttpPurchaseVerifier.accepted("""<html>502</html>"""))
        assertFalse(HttpPurchaseVerifier.accepted(""))
    }
}
