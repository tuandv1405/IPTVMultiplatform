package tss.t.tsiptv.ui.screens.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.plans_current
import tsiptv.composeapp.generated.resources.plans_disclosure
import tsiptv.composeapp.generated.resources.plans_feature_no_ads
import tsiptv.composeapp.generated.resources.plans_feature_no_tasks
import tsiptv.composeapp.generated.resources.plans_feature_rewarded_kept
import tsiptv.composeapp.generated.resources.plans_feature_unlimited_send
import tsiptv.composeapp.generated.resources.plans_feature_unlimited_sync
import tsiptv.composeapp.generated.resources.plans_free_name
import tsiptv.composeapp.generated.resources.plans_loading_prices
import tsiptv.composeapp.generated.resources.plans_manage
import tsiptv.composeapp.generated.resources.plans_msg_already_owned
import tsiptv.composeapp.generated.resources.plans_msg_deferred
import tsiptv.composeapp.generated.resources.plans_msg_failed
import tsiptv.composeapp.generated.resources.plans_msg_nothing_to_restore
import tsiptv.composeapp.generated.resources.plans_msg_pending
import tsiptv.composeapp.generated.resources.plans_msg_purchased
import tsiptv.composeapp.generated.resources.plans_msg_restored
import tsiptv.composeapp.generated.resources.plans_noads_name
import tsiptv.composeapp.generated.resources.plans_not_available
import tsiptv.composeapp.generated.resources.plans_price_month
import tsiptv.composeapp.generated.resources.plans_price_other
import tsiptv.composeapp.generated.resources.plans_price_year
import tsiptv.composeapp.generated.resources.plans_prices_unavailable
import tsiptv.composeapp.generated.resources.plans_privacy
import tsiptv.composeapp.generated.resources.plans_restore
import tsiptv.composeapp.generated.resources.plans_sign_in_to_subscribe
import tsiptv.composeapp.generated.resources.plans_status_auto_renew_off
import tsiptv.composeapp.generated.resources.plans_status_current
import tsiptv.composeapp.generated.resources.plans_status_device_only
import tsiptv.composeapp.generated.resources.plans_status_ends
import tsiptv.composeapp.generated.resources.plans_status_paused
import tsiptv.composeapp.generated.resources.plans_status_payment_issue
import tsiptv.composeapp.generated.resources.plans_status_renews
import tsiptv.composeapp.generated.resources.plans_subscribe
import tsiptv.composeapp.generated.resources.plans_switch
import tsiptv.composeapp.generated.resources.plans_terms
import tsiptv.composeapp.generated.resources.plans_title
import tsiptv.composeapp.generated.resources.plans_trial
import tsiptv.composeapp.generated.resources.plans_unlimited_name
import tsiptv.composeapp.generated.resources.plans_unlimited_needs_account
import tsiptv.composeapp.generated.resources.plans_upgrade
import tss.t.tsiptv.core.billing.Entitlement
import tss.t.tsiptv.core.billing.EntitlementSource
import tss.t.tsiptv.core.billing.Plan
import tss.t.tsiptv.core.billing.PlanAction
import tss.t.tsiptv.core.billing.PlanOffer
import tss.t.tsiptv.core.billing.SubscriptionStatus
import tss.t.tsiptv.ui.screens.connect.ConnectBody
import tss.t.tsiptv.ui.screens.connect.ConnectButton
import tss.t.tsiptv.ui.screens.connect.ConnectSectionTitle
import tss.t.tsiptv.ui.screens.connect.MessageDialog
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import tss.t.tsiptv.utils.AppLinks
import tss.t.tsiptv.utils.TimeStampFormat
import tss.t.tsiptv.utils.formatDynamic
import tss.t.tsiptv.utils.getUrlOpener
import kotlin.math.roundToInt

/**
 * "Gói đăng ký" (docs/prd-subscriptions.md §3.1): current plan, the two plans with Play's prices, the
 * auto-renewal disclosure, Terms / Privacy, the Play subscription centre and Restore. Every control is
 * a focusable surface, so the TV layout works with the D-pad.
 */
@Composable
fun PlansScreen(
    isTvLayout: Boolean,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: PlansViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val open: (String) -> Unit = { url -> scope.launch { getUrlOpener().openUrl(url) } }
    LaunchedEffect(Unit) { if (isTvLayout) firstFocus.requestFocusAfterLayout() }

    Column(Modifier.fillMaxSize().background(TSColors.BackgroundColor).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (isTvLayout) 40.dp else 8.dp, vertical = 8.dp)) {
            TvMenuItem(
                title = stringResource(Res.string.plans_title),
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onBack,
                modifier = Modifier.widthIn(max = 420.dp).focusRequester(firstFocus),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (isTvLayout) 48.dp else 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("status") { StatusCard(state.entitlement) }

            if (!state.supported) {
                item("not_available") { ConnectBody(stringResource(Res.string.plans_not_available), color = TSColors.TextPrimary) }
            } else if (state.loadingOffers) {
                item("loading") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TSColors.AccentCyan)
                        ConnectBody(stringResource(Res.string.plans_loading_prices))
                    }
                }
            } else if (state.noAdsOffers.isEmpty() && state.unlimitedOffers.isEmpty()) {
                item("no_prices") { ConnectBody(stringResource(Res.string.plans_prices_unavailable)) }
            }

            item("noads") {
                PlanCard(
                    name = stringResource(Res.string.plans_noads_name),
                    features = listOf(Res.string.plans_feature_no_ads, Res.string.plans_feature_rewarded_kept),
                    highlighted = state.entitlement.plan == Plan.NO_ADS,
                ) {
                    state.noAdsOffers.forEach { row -> OfferButton(row, state.busy) { viewModel.buy(row.offer) } }
                }
            }
            item("unlimited") {
                PlanCard(
                    name = stringResource(Res.string.plans_unlimited_name),
                    features = listOf(
                        Res.string.plans_feature_no_ads,
                        Res.string.plans_feature_unlimited_send,
                        Res.string.plans_feature_unlimited_sync,
                        Res.string.plans_feature_no_tasks,
                    ),
                    highlighted = state.entitlement.plan == Plan.UNLIMITED,
                ) {
                    if (state.supported && !state.signedIn && state.unlimitedOffers.isNotEmpty()) {
                        ConnectBody(stringResource(Res.string.plans_unlimited_needs_account))
                        ConnectButton(stringResource(Res.string.plans_sign_in_to_subscribe), onSignIn, Modifier.fillMaxWidth(), primary = true)
                    } else {
                        state.unlimitedOffers.forEach { row -> OfferButton(row, state.busy) { viewModel.buy(row.offer) } }
                    }
                }
            }

            // Shown before any purchase button is reached, never hidden in fine print (Play policy).
            item("disclosure") { ConnectBody(stringResource(Res.string.plans_disclosure), color = TSColors.TextPrimary) }

            item("links") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (state.supported) {
                        TvMenuItem(title = stringResource(Res.string.plans_manage), icon = Icons.Rounded.Settings, onClick = { open(state.manageUrl) })
                        TvMenuItem(
                            title = stringResource(Res.string.plans_restore),
                            icon = Icons.Rounded.Restore,
                            onClick = viewModel::restore,
                            trailing = { if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = TSColors.AccentCyan) },
                        )
                    }
                    TvMenuItem(title = stringResource(Res.string.plans_terms), icon = Icons.Rounded.Description, onClick = { open(AppLinks.TERMS_URL) })
                    TvMenuItem(title = stringResource(Res.string.plans_privacy), icon = Icons.Rounded.Lock, onClick = { open(AppLinks.PRIVACY_URL) })
                }
            }
            item("bottom") { Spacer(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) }
        }
    }

    state.message?.let { message ->
        val text = when (message) {
            PlansMessage.PURCHASED -> Res.string.plans_msg_purchased
            PlansMessage.PENDING -> Res.string.plans_msg_pending
            PlansMessage.FAILED, PlansMessage.UNAVAILABLE -> if (message == PlansMessage.UNAVAILABLE) Res.string.plans_not_available else Res.string.plans_msg_failed
            PlansMessage.ALREADY_OWNED -> Res.string.plans_msg_already_owned
            PlansMessage.RESTORED -> Res.string.plans_msg_restored
            PlansMessage.NOTHING_TO_RESTORE -> Res.string.plans_msg_nothing_to_restore
            PlansMessage.SIGN_IN_REQUIRED -> Res.string.plans_unlimited_needs_account
            PlansMessage.DEFERRED -> Res.string.plans_msg_deferred
        }
        MessageDialog(title = stringResource(Res.string.plans_title), text = stringResource(text), onDismiss = viewModel::clearMessage)
    }
}

@Composable
private fun StatusCard(entitlement: Entitlement) {
    val planName = when (entitlement.plan) {
        Plan.FREE -> stringResource(Res.string.plans_free_name)
        Plan.NO_ADS -> stringResource(Res.string.plans_noads_name)
        Plan.UNLIMITED -> stringResource(Res.string.plans_unlimited_name)
    }
    Column(
        Modifier.fillMaxWidth().background(TSColors.SecondaryBackgroundColor, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.plans_status_current, planName), color = TSColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        val expiry = entitlement.expiresAtMs?.formatDynamic(TimeStampFormat.yyyyMMdd.formatStr)
        val line = when {
            entitlement.plan == Plan.FREE -> null
            entitlement.status == SubscriptionStatus.IN_GRACE_PERIOD || entitlement.status == SubscriptionStatus.ON_HOLD ->
                stringResource(Res.string.plans_status_payment_issue)
            entitlement.status == SubscriptionStatus.PAUSED -> stringResource(Res.string.plans_status_paused)
            entitlement.autoRenewing == false && expiry != null -> stringResource(Res.string.plans_status_ends, expiry)
            entitlement.autoRenewing == false -> stringResource(Res.string.plans_status_auto_renew_off)
            expiry != null -> stringResource(Res.string.plans_status_renews, expiry)
            else -> null
        }
        line?.let { ConnectBody(it) }
        if (entitlement.plan != Plan.FREE && entitlement.source == EntitlementSource.PLAY) {
            ConnectBody(stringResource(Res.string.plans_status_device_only))
        }
    }
}

@Composable
private fun PlanCard(
    name: String,
    features: List<StringResource>,
    highlighted: Boolean,
    actions: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(TSColors.SecondaryBackgroundColor, RoundedCornerShape(14.dp))
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) TSColors.AccentCyan else TSColors.TextSecondary.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ConnectSectionTitle(name)
        features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = TSColors.AccentGreen, modifier = Modifier.size(18.dp))
                ConnectBody(stringResource(feature), color = TSColors.TextPrimary)
            }
        }
        actions()
    }
}

/** One base plan: price and period from Play, the trial when offered, and the action. */
@Composable
private fun OfferButton(row: OfferRow, busy: Boolean, onClick: () -> Unit) {
    val offer = row.offer
    val price = priceText(offer)
    offer.freeTrialPeriod?.let { trial ->
        ConnectBody(stringResource(Res.string.plans_trial, trial.approxDays.roundToInt(), price), color = TSColors.AccentCyan)
    }
    val action = stringResource(
        when (row.action) {
            PlanAction.CURRENT -> Res.string.plans_current
            PlanAction.SUBSCRIBE -> Res.string.plans_subscribe
            PlanAction.UPGRADE -> Res.string.plans_upgrade
            PlanAction.CHANGE, PlanAction.DOWNGRADE -> Res.string.plans_switch
        }
    )
    ConnectButton(
        text = "$action · $price",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        primary = row.action != PlanAction.CURRENT,
        enabled = row.action != PlanAction.CURRENT && !busy,
    )
}

@Composable
private fun priceText(offer: PlanOffer): String = when {
    offer.billingPeriod.isMonthly -> stringResource(Res.string.plans_price_month, offer.formattedPrice)
    offer.billingPeriod.isYearly -> stringResource(Res.string.plans_price_year, offer.formattedPrice)
    else -> stringResource(Res.string.plans_price_other, offer.formattedPrice)
}
