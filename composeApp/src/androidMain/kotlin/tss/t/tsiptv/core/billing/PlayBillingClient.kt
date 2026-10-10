package tss.t.tsiptv.core.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tss.t.tsiptv.R
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Google Play Billing (Billing Library 8) for the two subscriptions (docs/prd-subscriptions.md §3, §5).
 *
 * - Connects lazily. Reconnection follows [BillingConnectionPolicy]: exponential backoff (1 s up to
 *   5 min, reset on success), no queries while disconnected, and no attempts at all after "billing
 *   unavailable" (no Play Store or account) until the next start or Restore / a purchase.
 * - `queryPurchasesAsync(SUBS)` when the app starts and on activity resume (renewals,
 *   cancellations, purchases made elsewhere, pending → purchased), only while connected and at most
 *   once a minute.
 * - Debug-only logs, one line per connection state change.
 * - Acknowledges every PURCHASED purchase that is not yet acknowledged (Play refunds those after
 *   3 days); pending purchases are reported but never entitle.
 * - Product ids come from resources (`TSIPTV_BILLING_*_ID` at build time). Nothing is logged that
 *   identifies a purchase (no tokens, no order ids).
 */
class PlayBillingClient(context: Context) : BillingGateway {
    private val app = context.applicationContext as Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val debuggable = (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override val catalog = ProductCatalog(
        noAdsId = app.getString(R.string.billing_product_noads),
        unlimitedId = app.getString(R.string.billing_product_unlimited),
    )

    private val _purchases = MutableStateFlow<PlayPurchases>(PlayPurchases.Unknown)
    override val purchases: StateFlow<PlayPurchases> = _purchases.asStateFlow()

    private val _offers = MutableStateFlow<List<PlanOffer>>(emptyList())
    override val offers: StateFlow<List<PlanOffer>> = _offers.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<BillingEvent> = _events.asSharedFlow()

    /** False once Play said billing / subscriptions are not available on this device. */
    @Volatile
    private var available = true
    override val isSupported: Boolean get() = available

    private var details: Map<String, ProductDetails> = emptyMap()
    private var activityRef: WeakReference<Activity>? = null
    private val policy = BillingConnectionPolicy()
    private var loggedState: BillingConnectionPolicy.State? = null

    private val listener = PurchasesUpdatedListener { result, list -> scope.launch { onPurchasesUpdated(result, list) } }

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // No library auto-reconnection: it retries on every call; BillingConnectionPolicy backs off.
        .build()

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activityRef = WeakReference(activity)
                // Only while connected, at most once a minute (a disconnected client reconnects on
                // its own schedule).
                if (policy.shouldQueryOnResume(now())) refresh()
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (activityRef?.get() === activity) activityRef = null
            }
        })
    }

    override fun refresh() {
        scope.launch {
            if (!connect()) return@launch
            queryPurchases()
            if (details.isEmpty()) queryProducts()
        }
    }

    override suspend fun restore(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!connect(userAction = true)) return@withContext false
        val ok = queryPurchases()
        if (details.isEmpty()) queryProducts()
        if (ok) {
            val owned = (purchases.value as? PlayPurchases.Loaded)?.purchases.orEmpty()
            _events.tryEmit(if (owned.any { it.state == OwnedPurchaseState.PURCHASED }) BillingEvent.RESTORED else BillingEvent.NOTHING_TO_RESTORE)
        }
        ok
    }

    private fun now() = SystemClock.elapsedRealtime()

    /**
     * Connects if the policy allows it now; true when ready. Marks billing unavailable when Play says
     * so. [userAction] (Restore, a purchase) skips the backoff and an earlier "unavailable".
     */
    private suspend fun connect(userAction: Boolean = false): Boolean = mutex.withLock {
        if (userAction) policy.onUserAction()
        if (client.isReady && available) {
            if (policy.state != BillingConnectionPolicy.State.CONNECTED) {
                policy.onConnected()
                logState()
            }
            return@withLock true
        }
        if (!policy.mayConnect(now())) return@withLock false
        policy.onConnecting()
        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result.responseCode)
                    }

                    override fun onBillingServiceDisconnected() {
                        scope.launch { onServiceLost(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) }
                    }
                })
            }
        }
        when (result) {
            BillingClient.BillingResponseCode.OK -> {
                val subs = client.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode
                if (subs == BillingClient.BillingResponseCode.OK) {
                    available = true
                    policy.onConnected()
                    logState()
                    true
                } else {
                    markUnavailable(subs)
                    false
                }
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                markUnavailable(result)
                false
            }
            else -> {
                if (_purchases.value !is PlayPurchases.Loaded) _purchases.value = PlayPurchases.Failed
                scheduleReconnect(result ?: TIMEOUT_CODE)
                false
            }
        }
    }

    private fun markUnavailable(code: Int) {
        available = false
        _purchases.value = PlayPurchases.Unavailable
        policy.onUnavailable()
        logState("code $code; no retry until restart or Restore")
    }

    /** Waits the backoff delay, then connects and refreshes (unless something else did meanwhile). */
    private fun scheduleReconnect(code: Int) {
        val delayMs = policy.onFailure(now())
        logState("code $code; retry in ${delayMs / 1000} s")
        scope.launch {
            delay(delayMs)
            if (policy.state != BillingConnectionPolicy.State.WAITING) return@launch
            if (connect()) {
                queryPurchases()
                if (details.isEmpty()) queryProducts()
            }
        }
    }

    /** The service dropped, or a call answered "disconnected": back off from a connected state only. */
    private suspend fun onServiceLost(code: Int) = mutex.withLock {
        if (policy.state == BillingConnectionPolicy.State.CONNECTED) scheduleReconnect(code)
    }

    /** A call's response code that means the connection, not the request, failed. */
    private suspend fun handleCallFailure(code: Int): Boolean = when (code) {
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
            onServiceLost(code)
            true
        }
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
            mutex.withLock { if (policy.state != BillingConnectionPolicy.State.UNAVAILABLE) markUnavailable(code) }
            true
        }
        else -> false
    }

    /** One debug line per connection state change. */
    private fun logState(detail: String? = null) {
        val state = policy.state
        if (state == loggedState) return
        loggedState = state
        log("connection: $state" + (detail?.let { " ($it)" } ?: ""))
    }

    /** Main thread. true when Play answered. */
    private suspend fun queryPurchases(): Boolean {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val (result, list) = suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { r, p -> if (cont.isActive) cont.resume(r to p) }
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (!handleCallFailure(result.responseCode)) log("queryPurchases: ${result.responseCode}")
            if (_purchases.value !is PlayPurchases.Loaded && _purchases.value !is PlayPurchases.Unavailable) {
                _purchases.value = PlayPurchases.Failed
            }
            return false
        }
        handlePurchases(list)
        return true
    }

    /** Acknowledges what needs it, then publishes the owned subscriptions. */
    private suspend fun handlePurchases(list: List<Purchase>) {
        val owned = list.mapNotNull { purchase ->
            val productId = purchase.products.firstOrNull { it in catalog.productIds } ?: return@mapNotNull null
            var acknowledged = purchase.isAcknowledged
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !acknowledged) {
                acknowledged = acknowledge(purchase.purchaseToken)
            }
            OwnedPurchase(
                productId = productId,
                purchaseToken = purchase.purchaseToken,
                state = when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> OwnedPurchaseState.PURCHASED
                    Purchase.PurchaseState.PENDING -> OwnedPurchaseState.PENDING
                    else -> OwnedPurchaseState.UNSPECIFIED
                },
                acknowledged = acknowledged,
                autoRenewing = purchase.isAutoRenewing,
                accountHash = purchase.accountIdentifiers?.obfuscatedAccountId,
                purchaseTimeMs = purchase.purchaseTime,
            )
        }
        _purchases.value = PlayPurchases.Loaded(owned)
    }

    private suspend fun acknowledge(token: String): Boolean {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        val code = suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { r -> if (cont.isActive) cont.resume(r.responseCode) }
        }
        if (code != BillingClient.BillingResponseCode.OK) log("acknowledge: $code")
        return code == BillingClient.BillingResponseCode.OK
    }

    private suspend fun queryProducts() {
        val products = catalog.productIds.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val (result, list) = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { r, details -> if (cont.isActive) cont.resume(r to details.productDetailsList) }
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (!handleCallFailure(result.responseCode)) log("queryProductDetails: ${result.responseCode}")
            return
        }
        details = list.associateBy { it.productId }
        _offers.value = list.flatMap { pd ->
            OfferSelector.select(pd.productId, catalog.planOf(pd.productId), pd.subscriptionOfferDetails.orEmpty().map { it.toStoreOffer() })
        }
    }

    private fun ProductDetails.SubscriptionOfferDetails.toStoreOffer() = StoreOffer(
        basePlanId = basePlanId,
        offerId = offerId,
        offerToken = offerToken,
        phases = pricingPhases.pricingPhaseList.map {
            StorePhase(
                priceAmountMicros = it.priceAmountMicros,
                formattedPrice = it.formattedPrice,
                priceCurrencyCode = it.priceCurrencyCode,
                billingPeriod = it.billingPeriod,
                billingCycleCount = it.billingCycleCount,
                infiniteRecurring = it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING,
            )
        },
    )

    override suspend fun launchPurchase(offer: PlanOffer, accountHash: String?, replace: ReplaceFrom?): PurchaseLaunch =
        withContext(Dispatchers.Main.immediate) {
            if (!connect(userAction = true)) return@withContext if (available) PurchaseLaunch.FAILED else PurchaseLaunch.UNAVAILABLE
            if (details.isEmpty()) queryProducts()
            val pd = details[offer.productId] ?: return@withContext PurchaseLaunch.FAILED
            val activity = activityRef?.get()?.takeIf { !it.isFinishing } ?: return@withContext PurchaseLaunch.FAILED
            val product = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(pd)
                .setOfferToken(offer.offerToken)
                .build()
            val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product))
            accountHash?.let { builder.setObfuscatedAccountId(it) }
            replace?.let {
                builder.setSubscriptionUpdateParams(
                    BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                        .setOldPurchaseToken(it.purchaseToken)
                        .setSubscriptionReplacementMode(it.mode.toPlay())
                        .build()
                )
            }
            val code = client.launchBillingFlow(activity, builder.build()).responseCode
            when (code) {
                BillingClient.BillingResponseCode.OK -> PurchaseLaunch.STARTED
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    queryPurchases()
                    _events.tryEmit(BillingEvent.ALREADY_OWNED)
                    PurchaseLaunch.STARTED
                }
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> PurchaseLaunch.UNAVAILABLE
                else -> {
                    log("launchBillingFlow: $code")
                    PurchaseLaunch.FAILED
                }
            }
        }

    private suspend fun onPurchasesUpdated(result: BillingResult, list: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val ours = list.orEmpty().filter { p -> p.products.any { it in catalog.productIds } }
                // The listener only carries the changed purchases; re-read the full list after handling them.
                val pending = ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                val bought = ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                for (p in ours) {
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) acknowledge(p.purchaseToken)
                }
                if (!queryPurchases()) handlePurchases(ours)
                _events.tryEmit(
                    when {
                        bought -> BillingEvent.PURCHASED
                        pending -> BillingEvent.PENDING
                        else -> BillingEvent.PURCHASED
                    }
                )
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(BillingEvent.CANCELED)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryPurchases()
                _events.tryEmit(BillingEvent.ALREADY_OWNED)
            }
            else -> {
                log("purchase result: ${result.responseCode}")
                _events.tryEmit(BillingEvent.FAILED)
            }
        }
    }

    private fun ReplacementMode.toPlay(): Int = when (this) {
        ReplacementMode.WITH_TIME_PRORATION -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
        ReplacementMode.CHARGE_PRORATED_PRICE -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
        ReplacementMode.CHARGE_FULL_PRICE -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE
        ReplacementMode.WITHOUT_PRORATION -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITHOUT_PRORATION
        ReplacementMode.DEFERRED -> BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
    }

    private fun log(message: String) {
        if (debuggable) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "TSBilling"
        const val CONNECT_TIMEOUT_MS = 15_000L
        /** Not a Play code: the connection attempt timed out. */
        const val TIMEOUT_CODE = -100
    }
}
