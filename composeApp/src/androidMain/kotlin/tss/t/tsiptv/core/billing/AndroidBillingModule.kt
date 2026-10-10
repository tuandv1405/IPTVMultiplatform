package tss.t.tsiptv.core.billing

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.koin.dsl.module
import tss.t.tsiptv.R

/**
 * Google Play Billing and the billing-server verifier (docs/prd-subscriptions.md). Loaded after the
 * common `billingModule`, so these bindings override its "not available" defaults.
 */
val androidBillingModule = module {
    single<BillingGateway> {
        val context = get<Context>()
        val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // QA (debug only): sample prices, nothing purchasable. Release defines the flag as false.
        if (debuggable && context.resources.getBoolean(R.bool.debug_demo_billing)) DemoBillingGateway(
            ProductCatalog(context.getString(R.string.billing_product_noads), context.getString(R.string.billing_product_unlimited))
        ) else PlayBillingClient(context)
    }
    single<PurchaseVerifier> {
        val url = get<Context>().getString(R.string.billing_verify_url).trim()
        if (url.isEmpty()) DisabledPurchaseVerifier
        else HttpPurchaseVerifier(url, get()) { Firebase.auth.currentUser?.getIdToken(false) }
    }
}
