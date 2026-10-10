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
    single<BillingGateway> { PlayBillingClient(get()) }
    single<PurchaseVerifier> {
        val url = get<Context>().getString(R.string.billing_verify_url).trim()
        if (url.isEmpty()) DisabledPurchaseVerifier
        else HttpPurchaseVerifier(url, get()) { Firebase.auth.currentUser?.getIdToken(false) }
    }
}
