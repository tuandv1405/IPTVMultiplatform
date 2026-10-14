package tss.t.tsiptv.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.cancel
import tsiptv.composeapp.generated.resources.clip_copied
import tsiptv.composeapp.generated.resources.clip_failed_convert
import tsiptv.composeapp.generated.resources.clip_failed_network
import tsiptv.composeapp.generated.resources.clip_failed_protected
import tsiptv.composeapp.generated.resources.clip_failed_storage
import tsiptv.composeapp.generated.resources.clip_failed_title
import tsiptv.composeapp.generated.resources.clip_failed_too_short
import tsiptv.composeapp.generated.resources.clip_failed_unsupported
import tsiptv.composeapp.generated.resources.clip_length_seconds
import tsiptv.composeapp.generated.resources.clip_live_hint
import tsiptv.composeapp.generated.resources.clip_open
import tsiptv.composeapp.generated.resources.clip_personal
import tsiptv.composeapp.generated.resources.clip_saved_detail
import tsiptv.composeapp.generated.resources.clip_saved_title
import tsiptv.composeapp.generated.resources.clip_share_text
import tsiptv.composeapp.generated.resources.clip_start
import tsiptv.composeapp.generated.resources.clip_vod_hint
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.player_clip
import tsiptv.composeapp.generated.resources.player_share
import tsiptv.composeapp.generated.resources.share_failed
import tss.t.tsiptv.feature.clip.ClipFailure
import tss.t.tsiptv.feature.clip.ClipRecorder
import tss.t.tsiptv.feature.clip.ClipResult
import tss.t.tsiptv.feature.clip.ClipShareOutcome
import tss.t.tsiptv.feature.clip.Clips
import tss.t.tsiptv.feature.share.ShareLinks
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.ui.screens.addons.AddonDialog
import tss.t.tsiptv.ui.screens.addons.PillButton
import tss.t.tsiptv.ui.themes.TSColors

/** Player › Clip, as the player screen shows it ([PlayerViewModel.clip]). */
sealed interface ClipUiState {
    data object Idle : ClipUiState

    /** Recording ([recordedMs] of [lengthMs]), then writing the MP4 once [finishing]. */
    data class Recording(val recordedMs: Long, val lengthMs: Long, val finishing: Boolean) : ClipUiState

    /** @param title what was clipped, for the text sent with the clip */
    data class Done(val result: ClipResult, val title: String) : ClipUiState
}

/**
 * Player › Clip: the length to record, then Start. Playback goes on while the clip records; the
 * Clip button shows the time and stops it early.
 *
 * @param startMs where a VOD clip starts; null for live (from now on)
 */
@Composable
fun ClipSheet(
    mediaItem: MediaItem,
    startMs: Long?,
    onStart: (lengthMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val failure = remember(mediaItem) { Clips.check(mediaItem) }
    var lengthMs by remember { mutableLongStateOf(Clips.LENGTHS_MS[1]) }
    AddonDialog(title = stringResource(Res.string.player_clip), onDismissRequest = onDismiss) {
        if (failure != null) {
            Text(failureText(failure), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
            PillButton(text = stringResource(Res.string.ok), onClick = onDismiss)
            return@AddonDialog
        }
        Text(
            text = if (startMs == null) stringResource(Res.string.clip_live_hint)
            else stringResource(Res.string.clip_vod_hint, formatClipTime(startMs)),
            color = TSColors.TextSecondaryLight,
            fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Clips.LENGTHS_MS.forEach { ms ->
                PillButton(
                    text = stringResource(Res.string.clip_length_seconds, (ms / 1000).toInt()),
                    primary = ms == lengthMs,
                    onClick = { lengthMs = ms },
                )
            }
        }
        Text(stringResource(Res.string.clip_personal), color = TSColors.TextSecondary, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
            PillButton(text = stringResource(Res.string.cancel), primary = false, onClick = onDismiss)
            PillButton(
                text = stringResource(Res.string.clip_start),
                onClick = {
                    onStart(lengthMs)
                    onDismiss()
                },
            )
        }
    }
}

/** The clip just made: share or open it, or why there is none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipResultDialog(state: ClipUiState.Done, recorder: ClipRecorder?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    when (val result = state.result) {
        is ClipResult.Failed -> AddonDialog(title = stringResource(Res.string.clip_failed_title), onDismissRequest = onDismiss) {
            Text(failureText(result.reason), color = TSColors.TextSecondaryLight, fontSize = 14.sp)
            PillButton(text = stringResource(Res.string.ok), onClick = onDismiss)
        }

        is ClipResult.Saved -> AddonDialog(title = stringResource(Res.string.clip_saved_title), onDismissRequest = onDismiss) {
            val clip = result.clip
            Text(clip.name, color = TSColors.TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(Res.string.clip_saved_detail, formatClipTime(clip.durationMs), clip.where),
                color = TSColors.TextSecondary,
                fontSize = 13.sp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recorder?.canShare == true) {
                    PillButton(
                        text = stringResource(Res.string.player_share),
                        onClick = {
                            scope.launch {
                                val text = getString(Res.string.clip_share_text, state.title, ShareLinks.PLAY_STORE)
                                when (recorder.share(clip, text)) {
                                    ClipShareOutcome.OPENED -> onDismiss()
                                    ClipShareOutcome.COPIED -> message = getString(Res.string.clip_copied)
                                    ClipShareOutcome.FAILED -> message = getString(Res.string.share_failed)
                                }
                            }
                        },
                    )
                }
                PillButton(
                    text = stringResource(Res.string.clip_open),
                    primary = recorder?.canShare != true,
                    onClick = { if (recorder?.open(clip) == true) onDismiss() },
                )
                PillButton(text = stringResource(Res.string.ok), primary = false, onClick = onDismiss)
            }
            message?.let { Text(it, color = TSColors.TextSecondaryLight, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun failureText(failure: ClipFailure): String = stringResource(
    when (failure) {
        ClipFailure.PROTECTED -> Res.string.clip_failed_protected
        ClipFailure.UNSUPPORTED -> Res.string.clip_failed_unsupported
        ClipFailure.NETWORK -> Res.string.clip_failed_network
        ClipFailure.CONVERT -> Res.string.clip_failed_convert
        ClipFailure.STORAGE -> Res.string.clip_failed_storage
        ClipFailure.TOO_SHORT -> Res.string.clip_failed_too_short
    }
)

/** `0:07`, `1:00`, `1:02:03`. */
internal fun formatClipTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = (total % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}
