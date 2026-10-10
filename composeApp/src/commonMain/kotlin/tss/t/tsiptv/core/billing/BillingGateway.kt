package tss.t.tsiptv.core.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Replace an owned subscription (upgrade / downgrade, docs/prd-subscriptions.md §3.3). */
data class ReplaceFrom(val purchaseToken: String, val mode: ReplacementMode)

/**
 * The platform store. Android: Google Play Billing (`PlayBillingClient`). iOS and desktop:
 * [UnavailableBillingGateway] until StoreKit 2 (PRD §9).
 */
interface BillingGateway {
    /** The store can sell here (false: iOS, desktop, no Play Store, billing unavailable). */
    val isSupported: Boolean

    val catalog: ProductCatalog

    /** Owned subscriptions of this device's store account. */
    val purchases: StateFlow<PlayPurchases>

    /** Purchasable base plans with the store's prices (empty until loaded, or when unavailable). */
    val offers: StateFlow<List<PlanOffer>>

    /** One-shot results of the purchase flow. */
    val events: Flow<BillingEvent>

    /** Connects if needed, then re-reads purchases and, if not loaded, the product details. */
    fun refresh()

    /**
     * Google Play is installed but billing answered "unavailable" (no account signed in to Play, Play
     * updating, ...). Usually temporary: purchases stay unsettled so a cached plan keeps working.
     */
    val playBillingUnavailable: StateFlow<Boolean> get() = NeverUnavailable

    /** A user action (opening the plans screen): refresh, ignoring the backoff and "unavailable". */
    fun retry() = refresh()

    /** Re-reads purchases now; true when the store answered. */
    suspend fun restore(): Boolean

    /**
     * Opens the store's purchase sheet for [offer]. [accountHash] = `obfuscatedAccountId` (SHA-256 of
     * the uid) when signed in; [replace] for an upgrade or downgrade of an owned subscription.
     */
    suspend fun launchPurchase(offer: PlanOffer, accountHash: String?, replace: ReplaceFrom?): PurchaseLaunch
}

/** iOS and desktop (and tests): nothing to buy; a server entitlement still applies. */
class UnavailableBillingGateway(override val catalog: ProductCatalog = ProductCatalog()) : BillingGateway {
    override val isSupported: Boolean = false
    override val purchases: StateFlow<PlayPurchases> = MutableStateFlow(PlayPurchases.Unavailable).asStateFlow()
    override val offers: StateFlow<List<PlanOffer>> = MutableStateFlow(emptyList<PlanOffer>()).asStateFlow()
    override val events: Flow<BillingEvent> = emptyFlow()
    override fun refresh() = Unit
    override suspend fun restore(): Boolean = false
    override suspend fun launchPurchase(offer: PlanOffer, accountHash: String?, replace: ReplaceFrom?) = PurchaseLaunch.UNAVAILABLE
}

private val NeverUnavailable: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
