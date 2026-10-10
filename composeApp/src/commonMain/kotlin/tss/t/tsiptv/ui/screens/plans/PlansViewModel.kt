package tss.t.tsiptv.ui.screens.plans

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.billing.BillingEvent
import tss.t.tsiptv.core.billing.BillingGateway
import tss.t.tsiptv.core.billing.Entitlement
import tss.t.tsiptv.core.billing.EntitlementRepository
import tss.t.tsiptv.core.billing.OwnedPurchaseState
import tss.t.tsiptv.core.billing.Plan
import tss.t.tsiptv.core.billing.PlanAction
import tss.t.tsiptv.core.billing.PlanOffer
import tss.t.tsiptv.core.billing.PlayPurchases
import tss.t.tsiptv.core.billing.PurchaseLaunch
import tss.t.tsiptv.core.billing.PurchaseVerifier
import tss.t.tsiptv.core.billing.ReplaceFrom
import tss.t.tsiptv.core.billing.ReplacementMode
import tss.t.tsiptv.core.billing.ReplacementPolicy

/** Opens the plans screen (Profile, "Remove ads", quota screens). Provided by the app's root. */
val LocalOpenPlans = staticCompositionLocalOf<(() -> Unit)?> { null }

/** One-shot messages of the plans screen. */
enum class PlansMessage {
    PURCHASED, PENDING, FAILED, ALREADY_OWNED, RESTORED, NOTHING_TO_RESTORE, UNAVAILABLE, SIGN_IN_REQUIRED, DEFERRED,

    /** Restore: Google Play is installed but its billing is unavailable (sign in / update Play). */
    PLAY_UNAVAILABLE,

    /** Restore could not reach Google Play (QC B5). */
    RESTORE_FAILED,
}

/** A plan card's button for one base plan. */
data class OfferRow(val offer: PlanOffer, val action: PlanAction, val current: Boolean)

data class PlansUiState(
    val entitlement: Entitlement = Entitlement.FREE,
    val supported: Boolean = false,
    val loadingOffers: Boolean = true,
    val noAdsOffers: List<OfferRow> = emptyList(),
    val unlimitedOffers: List<OfferRow> = emptyList(),
    /**
     * Unlimited is sold only once the billing server is configured (QC B1): without it the rules
     * cannot lift the caps, so "Unlimited" would be 8 sends / 3 syncs a day.
     */
    val unlimitedForSale: Boolean = false,
    val signedIn: Boolean = false,
    /** Play is installed but its billing is unavailable right now (sign in to Play / update it). */
    val playUnavailable: Boolean = false,
    val busy: Boolean = false,
    val message: PlansMessage? = null,
    /** Play subscription centre for the current product (or the list). */
    val manageUrl: String = ReplacementPolicy.manageUrl(ReplacementPolicy.PACKAGE_NAME, null),
)

/**
 * The plans screen (docs/prd-subscriptions.md §3.1): the current plan, the store's offers with their
 * prices, purchase / upgrade / downgrade with the replacement modes of §3.3, and restore.
 */
class PlansViewModel(
    private val billing: BillingGateway,
    private val entitlements: EntitlementRepository,
    verifier: PurchaseVerifier,
) : ViewModel() {

    private val unlimitedForSale = verifier.enabled

    private val _state = MutableStateFlow(PlansUiState(supported = billing.isSupported, unlimitedForSale = unlimitedForSale))
    val state: StateFlow<PlansUiState> = _state

    /** The purchase in flight is a deferred downgrade: report that, not "your plan is active" (QC B7). */
    private var launchedDeferred = false

    init {
        viewModelScope.launch {
            combine(entitlements.entitlement, billing.offers, billing.purchases, entitlements.signedInUid, billing.playBillingUnavailable) { e, offers, purchases, uid, unavailable ->
                Snapshot(e, offers, purchases, uid != null, unavailable)
            }.collect { s -> _state.update { build(it, s) } }
        }
        viewModelScope.launch {
            billing.events.collect { event ->
                val deferred = launchedDeferred
                launchedDeferred = false
                _state.update {
                    it.copy(
                        busy = false,
                        message = when (event) {
                            BillingEvent.PURCHASED -> if (deferred) PlansMessage.DEFERRED else PlansMessage.PURCHASED
                            BillingEvent.PENDING -> PlansMessage.PENDING
                            BillingEvent.CANCELED -> null
                            BillingEvent.ALREADY_OWNED -> PlansMessage.ALREADY_OWNED
                            BillingEvent.FAILED -> PlansMessage.FAILED
                            BillingEvent.RESTORED -> PlansMessage.RESTORED
                            BillingEvent.NOTHING_TO_RESTORE -> PlansMessage.NOTHING_TO_RESTORE
                        },
                    )
                }
            }
        }
        // Opening the screen is a user action: one fresh attempt even after "unavailable" or a backoff.
        billing.retry()
    }

    private data class Snapshot(
        val entitlement: Entitlement,
        val offers: List<PlanOffer>,
        val purchases: PlayPurchases,
        val signedIn: Boolean,
        val playUnavailable: Boolean,
    )

    private fun build(state: PlansUiState, s: Snapshot): PlansUiState {
        fun rows(plan: Plan) = s.offers.filter { it.plan == plan }.map { offer ->
            val current = s.entitlement.productId == offer.productId &&
                (s.entitlement.basePlanId == null || s.entitlement.basePlanId == offer.basePlanId)
            val action = when {
                current -> PlanAction.CURRENT
                s.entitlement.productId == offer.productId -> PlanAction.CHANGE
                else -> ReplacementPolicy.actionFor(s.entitlement, offer.plan)
            }
            OfferRow(offer, action, current)
        }
        return state.copy(
            entitlement = s.entitlement,
            supported = billing.isSupported,
            // Only while Play has not answered yet; a failed or unavailable store shows "couldn't load prices".
            loadingOffers = billing.isSupported && s.offers.isEmpty() && s.purchases is PlayPurchases.Unknown,
            noAdsOffers = rows(Plan.NO_ADS),
            unlimitedOffers = if (unlimitedForSale) rows(Plan.UNLIMITED) else emptyList(),
            signedIn = s.signedIn,
            playUnavailable = s.playUnavailable,
            manageUrl = ReplacementPolicy.manageUrl(ReplacementPolicy.PACKAGE_NAME, s.entitlement.productId),
        )
    }

    fun buy(offer: PlanOffer) {
        val s = _state.value
        if (s.busy) return
        if (offer.plan == Plan.UNLIMITED && !unlimitedForSale) return
        val uid = entitlements.signedInUid.value
        // Unlimited's features need an account (PRD §2).
        if (offer.plan == Plan.UNLIMITED && uid == null) {
            _state.update { it.copy(message = PlansMessage.SIGN_IN_REQUIRED) }
            return
        }
        val replace = replacementFor(offer)
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            launchedDeferred = replace?.mode == ReplacementMode.DEFERRED
            val result = billing.launchPurchase(offer, uid?.let(ReplacementPolicy::accountHash), replace)
            if (result != PurchaseLaunch.STARTED) launchedDeferred = false
            _state.update {
                when (result) {
                    // The outcome (bought, deferred, pending, cancelled) arrives as a billing event.
                    PurchaseLaunch.STARTED -> it.copy(busy = false)
                    PurchaseLaunch.SIGN_IN_REQUIRED -> it.copy(busy = false, message = PlansMessage.SIGN_IN_REQUIRED)
                    PurchaseLaunch.UNAVAILABLE -> it.copy(busy = false, message = PlansMessage.UNAVAILABLE)
                    PurchaseLaunch.FAILED -> it.copy(busy = false, message = PlansMessage.FAILED)
                }
            }
        }
    }

    /** The owned subscription on this Play account to replace, with the mode of PRD §3.3. */
    private fun replacementFor(target: PlanOffer): ReplaceFrom? {
        val owned = (billing.purchases.value as? PlayPurchases.Loaded)?.purchases
            ?.filter { it.state == OwnedPurchaseState.PURCHASED }
            ?.maxByOrNull { billing.catalog.planOf(it.productId).rank }
            ?: return null
        val ent = _state.value.entitlement
        val currentPlan = billing.catalog.planOf(owned.productId)
        val currentOffer = billing.offers.value.firstOrNull {
            it.productId == owned.productId && ent.basePlanId != null && it.basePlanId == ent.basePlanId
        }
        val mode = ReplacementPolicy.modeFor(owned.productId, currentPlan, currentOffer, target) ?: return null
        return ReplaceFrom(owned.purchaseToken, mode)
    }

    fun restore() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val ok = entitlements.restore()
            _state.update {
                it.copy(
                    busy = false,
                    message = when {
                        ok -> it.message
                        billing.playBillingUnavailable.value -> PlansMessage.PLAY_UNAVAILABLE
                        else -> PlansMessage.RESTORE_FAILED
                    },
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
