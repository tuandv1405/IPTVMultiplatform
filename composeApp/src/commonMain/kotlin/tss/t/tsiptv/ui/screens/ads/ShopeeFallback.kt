package tss.t.tsiptv.ui.screens.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tss.t.tsiptv.ui.widgets.AdsItem

/**
 * The Shopee affiliate offer that takes an ad slot when AdMob has no fill (PRD R6), or null when
 * there is none (the slot then collapses). [slot] null: the rotating banner offer; otherwise the
 * slot's own offer, so neighbouring native slots differ.
 */
@Composable
fun rememberShopeeFallback(adsViewModel: AdsViewModel, slot: Int? = null): (@Composable () -> Unit)? {
    val offers by adsViewModel.ads.collectAsStateWithLifecycle()
    val rotating by adsViewModel.displayAd.collectAsStateWithLifecycle()
    val offer = if (slot == null) rotating else offers.takeIf { it.isNotEmpty() }?.let { it[slot % it.size] }
    return offer?.let { { AdsItem(it) } }
}
