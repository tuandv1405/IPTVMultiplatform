package tss.t.tsiptv.ui.screens.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.lan_cancel
import tsiptv.composeapp.generated.resources.lan_tv_accept
import tsiptv.composeapp.generated.resources.lan_tv_cast_from
import tsiptv.composeapp.generated.resources.lan_tv_decline
import tsiptv.composeapp.generated.resources.lan_tv_offer_message
import tsiptv.composeapp.generated.resources.lan_tv_offer_title
import tsiptv.composeapp.generated.resources.lan_tv_pair_message
import tsiptv.composeapp.generated.resources.lan_tv_pair_title
import tsiptv.composeapp.generated.resources.lan_tv_paired
import tss.t.tsiptv.feature.lan.CastStream
import tss.t.tsiptv.feature.lan.LanReceiverController
import tss.t.tsiptv.feature.lan.LanReceiverEvent
import tss.t.tsiptv.feature.lan.SharedPlaylist
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * TV: runs the LAN receiver while the app is in the foreground and shows its UI: the pairing code,
 * the "receive this playlist?" confirmation (one at a time, D-pad) and short notices
 * (docs/prd-tv-cast-and-sync.md §2.6, §3.1).
 */
@OptIn(ExperimentalTime::class)
@Composable
fun LanReceiverHost(
    onCast: (stream: CastStream) -> Unit,
    onAcceptPlaylist: (SharedPlaylist) -> Unit,
    /** False while the TV cannot take a playlist now (e.g. an import is running). */
    canTakePlaylists: Boolean = true,
) {
    val controller: LanReceiverController = koinInject()
    val scope = rememberCoroutineScope()
    val prompt by controller.pairingPrompt.collectAsStateWithLifecycle()
    val offers = remember { mutableStateListOf<LanReceiverEvent.PlaylistOffered>() }
    var notice by remember { mutableStateOf<String?>(null) }

    // Only while resumed: the server stops in onPause (PRD §2.4). The controller applies these in
    // order, so a fast pause/resume cannot leave it in the wrong state.
    LifecycleResumeEffect(controller) {
        controller.setForeground(true)
        onPauseOrDispose { controller.setForeground(false) }
    }

    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is LanReceiverEvent.Paired -> notice = getString(Res.string.lan_tv_paired, event.senderName)
                is LanReceiverEvent.CastReceived -> {
                    notice = getString(Res.string.lan_tv_cast_from, event.senderName)
                    onCast(event.stream)
                }
                is LanReceiverEvent.PlaylistOffered -> if (offers.size < MAX_QUEUED_OFFERS) offers.add(event)
            }
        }
    }

    // Offers are refused with NOT_ACCEPTING ("TV is busy") while the TV cannot take one.
    LaunchedEffect(canTakePlaylists, offers.size) {
        controller.engine.acceptingOffers = canTakePlaylists && offers.size < MAX_QUEUED_OFFERS
    }

    // Offers wait at most 2 minutes for an answer (PRD §7).
    val offer = offers.firstOrNull()
    LaunchedEffect(offer) {
        val now = Clock.System.now().toEpochMilliseconds()
        offers.removeAll { now - it.receivedAt > OFFER_TTL_MS }
    }

    prompt?.let { p ->
        val cancel = remember { FocusRequester() }
        ConnectDialog(
            title = stringResource(Res.string.lan_tv_pair_title),
            onDismissRequest = { scope.launch { controller.engine.cancelPairing() } },
            dismissOnOutside = false,
        ) {
            Text(
                text = p.code.chunked(3).joinToString(" "),
                color = TSColors.TextPrimary,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            ConnectBody(stringResource(Res.string.lan_tv_pair_message, p.senderName))
            ConnectButtonRow {
                ConnectButton(
                    stringResource(Res.string.lan_cancel),
                    { scope.launch { controller.engine.cancelPairing() } },
                    Modifier.focusRequester(cancel),
                )
            }
        }
        LaunchedEffect(p) { cancel.requestFocusAfterLayout() }
    }

    if (prompt == null && offer != null) {
        val accept = remember(offer) { FocusRequester() }
        ConnectDialog(
            title = stringResource(Res.string.lan_tv_offer_title),
            onDismissRequest = { offers.remove(offer) },
        ) {
            ConnectBody(stringResource(Res.string.lan_tv_offer_message, offer.playlist.name, offer.senderName), color = TSColors.TextPrimary)
            ConnectButtonRow {
                ConnectButton(stringResource(Res.string.lan_tv_decline), { offers.remove(offer) })
                ConnectButton(
                    stringResource(Res.string.lan_tv_accept),
                    {
                        offers.remove(offer)
                        onAcceptPlaylist(offer.playlist)
                    },
                    Modifier.focusRequester(accept),
                    primary = true,
                )
            }
        }
        LaunchedEffect(offer) { accept.requestFocusAfterLayout() }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4_000)
            notice = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = notice != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = notice.orEmpty(),
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

private const val MAX_QUEUED_OFFERS = 5
private const val OFFER_TTL_MS = 120_000L
