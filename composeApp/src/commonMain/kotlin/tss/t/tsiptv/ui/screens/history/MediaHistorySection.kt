package tss.t.tsiptv.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tss.t.tsiptv.core.stremio.MediaHistoryRecord
import tss.t.tsiptv.ui.screens.addons.AddonSectionTitle
import tss.t.tsiptv.ui.screens.addons.PosterImage
import tss.t.tsiptv.ui.themes.TSColors
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.history_media_clear
import tsiptv.composeapp.generated.resources.history_media_remove
import tsiptv.composeapp.generated.resources.history_media_section

/**
 * History tab → "Movies & series" (PRD §9): one row per title (its latest video), newest first.
 * Selecting opens the detail with that video's streams; long-press or ✕ removes the title.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.mediaHistorySection(
    records: List<MediaHistoryRecord>,
    onOpen: (MediaHistoryRecord) -> Unit,
    onRemove: (MediaHistoryRecord) -> Unit,
    onClear: () -> Unit,
) {
    val titles = records.sortedByDescending { it.updatedAt }.distinctBy { it.sourceKind to it.itemId }
    if (titles.isEmpty()) return
    item(key = "media_history_title") {
        AddonSectionTitle(
            stringResource(Res.string.history_media_section),
            Modifier.padding(start = 16.dp, end = 4.dp, top = 24.dp),
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(Res.string.history_media_clear), tint = TSColors.TextSecondaryLight)
            }
        }
    }
    items(titles, key = { "media_" + it.id }) { record ->
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TSColors.SecondaryBackgroundColor)
                .combinedClickable(onClick = { onOpen(record) }, onLongClick = { onRemove(record) })
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(record.posterUrl, record.title.orEmpty(), Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.title.orEmpty(), color = TSColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                record.subtitle?.let { Text(it, color = TSColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (record.durationMs > 0 && !record.finished) {
                    LinearProgressIndicator(
                        progress = { (record.positionMs.toFloat() / record.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = TSColors.AccentCyan,
                        trackColor = TSColors.White.copy(alpha = 0.1f),
                    )
                }
            }
            IconButton(onClick = { onRemove(record) }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.history_media_remove), tint = TSColors.TextSecondary)
            }
        }
    }
}
