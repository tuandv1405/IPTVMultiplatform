package tss.t.tsiptv.core.billing.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tss.t.tsiptv.core.ads.AdsGate
import tss.t.tsiptv.core.billing.BillingGateway
import tss.t.tsiptv.core.billing.DisabledPurchaseVerifier
import tss.t.tsiptv.core.billing.EntitlementRepository
import tss.t.tsiptv.core.billing.FirestoreServerEntitlementSource
import tss.t.tsiptv.core.billing.PurchaseVerifier
import tss.t.tsiptv.core.billing.ServerEntitlementSource
import tss.t.tsiptv.core.billing.UnavailableBillingGateway
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.plans.PlansViewModel

/**
 * Subscriptions (docs/prd-subscriptions.md). The defaults are the "not available" ones (iOS,
 * desktop); `androidBillingModule` (androidMain), loaded later, binds Google Play Billing and the
 * server verifier.
 */
val billingModule = module {
    single<BillingGateway> { UnavailableBillingGateway() }
    single<PurchaseVerifier> { DisabledPurchaseVerifier }
    single<ServerEntitlementSource> { FirestoreServerEntitlementSource(get()) }

    single {
        val auth = get<AuthRepository>()
        EntitlementRepository(
            billing = get(),
            server = get(),
            verifier = get(),
            // Nothing until the auth state is known, so a signed-in subscriber is not judged
            // "signed out" for a moment at start-up.
            uid = auth.authState.filter { !it.isLoading }.map { if (it.isAuthenticated) it.user?.uid else null },
            storage = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            nowMs = AdsGate::systemNowMs,
        ).also { it.start() }
    }

    viewModelOf(::PlansViewModel)
}
