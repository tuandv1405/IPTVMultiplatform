package tss.t.tsiptv.ui.screens.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.player_no_options
import tsiptv.composeapp.generated.resources.player_options_title
import tsiptv.composeapp.generated.resources.player_stream
import tsiptv.composeapp.generated.resources.player_subtitles
import tsiptv.composeapp.generated.resources.player_subtitles_off
import tsiptv.composeapp.generated.resources.source_stream_default
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.core.tsiptv.TsiptvChannelStreams
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.TextTrackOption
import tss.t.tsiptv.ui.screens.addons.AddonDialog
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvMenuItem
import tss.t.tsiptv.ui.tv.requestFocusAfterLayout

/**
 * The player's options (phone settings button, TV ▶ / MENU): the streams of a source channel with
 * several (spec §8.5, the viewer can pick another) and the subtitle tracks, Off first (AC-T21).
 * Every row is a D-pad focusable item; focus starts on the current choice.
 */
@Composable
fun PlayerOptionsDialog(
    player: MediaPlayer,
    streams: ChannelStreamChoice?,
    onPickStream: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiLanguage = LocalAppLocale.current
    var tracks by remember { mutableStateOf(player.textTracks()) }
    // Side-loaded tracks appear once the item is prepared: refresh while the menu is open.
    LaunchedEffect(player) {
        while (true) {
            delay(1_000)
            tracks = player.textTracks()
        }
    }
    val labels by androidx.compose.runtime.produceState(emptyList<String>(), streams, uiLanguage) {
        value = streams?.let { choice ->
            val fallback = (1..10).associateWith { getString(Res.string.source_stream_default, it) }
            TsiptvChannelStreams.labels(choice.channel, uiLanguage) { fallback[it] ?: "$it" }
        }.orEmpty()
    }
    val first = remember { FocusRequester() }
    AddonDialog(title = stringResource(Res.string.player_options_title), onDismissRequest = onDismiss) {
        var firstUsed = false
        fun focusFirst(selected: Boolean): Modifier =
            if (selected && !firstUsed) { firstUsed = true; Modifier.focusRequester(first) } else Modifier
        if (streams != null && labels.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.player_stream))
            labels.forEachIndexed { i, label ->
                val selected = i == streams.current
                TvMenuItem(
                    title = label,
                    selected = selected,
                    modifier = focusFirst(selected),
                    onClick = { onPickStream(i); onDismiss() },
                    trailing = { if (selected) CheckIcon() },
                )
            }
        }
        if (tracks.isNotEmpty()) {
            SectionTitle(stringResource(Res.string.player_subtitles))
            val none = tracks.none(TextTrackOption::selected)
            TvMenuItem(
                title = stringResource(Res.string.player_subtitles_off),
                selected = none,
                modifier = focusFirst(none),
                onClick = { player.selectTextTrack(null); onDismiss() },
                trailing = { if (none) CheckIcon() },
            )
            tracks.forEach { track ->
                TvMenuItem(
                    title = track.label,
                    description = track.language?.takeIf { it != track.label },
                    selected = track.selected,
                    modifier = focusFirst(track.selected),
                    onClick = { player.selectTextTrack(track.id); onDismiss() },
                    trailing = { if (track.selected) CheckIcon() },
                )
            }
        }
        if ((streams == null || labels.isEmpty()) && tracks.isEmpty()) {
            Text(stringResource(Res.string.player_no_options), color = TSColors.TextSecondaryLight, fontSize = 15.sp)
        }
    }
    LaunchedEffect(labels.size, tracks.size) { first.requestFocusAfterLayout() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TSColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CheckIcon() {
    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = TSColors.AccentGreen)
}
