package tss.t.tsiptv.ui.screens.connect

import tsiptv.composeapp.generated.resources.devices_unknown
import tsiptv.composeapp.generated.resources.devices_last_seen
import tss.t.tsiptv.utils.formatDynamic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.tv.TvFocusableSurface

/** A dark panel dialog that works with touch and with the D-pad (every action is focusable). */
@Composable
fun ConnectDialog(
    title: String,
    onDismissRequest: () -> Unit,
    dismissOnOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutside, usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                // Text fields (connect by IP, the pairing code) stay above the keyboard.
                .imePadding()
                .padding(16.dp)
                .widthIn(min = 300.dp, max = 560.dp)
                .heightIn(max = 640.dp)
                .background(TSColors.SecondaryBackgroundColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = TSColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            content()
        }
    }
}

/** A focusable text button for dialogs; [primary] gets the accent colour. */
@Composable
fun ConnectButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    TvFocusableSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.03f,
        color = if (primary) TSColors.AccentCyan.copy(alpha = if (enabled) 0.85f else 0.3f) else Color.White.copy(alpha = 0.08f),
        focusedColor = if (primary) TSColors.AccentCyan else Color.White.copy(alpha = 0.2f),
    ) {
        Text(
            text = text,
            color = if (primary) Color.Black else TSColors.TextPrimary.copy(alpha = if (enabled) 1f else 0.5f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun ConnectButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun ConnectBody(text: String, modifier: Modifier = Modifier, color: Color = TSColors.TextSecondaryLight) {
    Text(text = text, color = color, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier.padding(horizontal = 4.dp))
}

@Composable
fun ConnectSectionTitle(text: String) {
    Spacer(Modifier.padding(top = 4.dp))
    Text(
        text = text,
        color = TSColors.AccentCyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/** A device's name, or "Unknown device" for a slot without details ([RegisteredDevice.ghost]). */
@Composable
fun tss.t.tsiptv.feature.account.RegisteredDevice.displayName(): String =
    if (ghost || name.isBlank()) org.jetbrains.compose.resources.stringResource(tsiptv.composeapp.generated.resources.Res.string.devices_unknown) else name

/** "Last seen …", or empty for a slot without details. */
@Composable
fun tss.t.tsiptv.feature.account.RegisteredDevice.lastSeenText(): String =
    if (ghost || lastSeen <= 0) "" else org.jetbrains.compose.resources.stringResource(
        tsiptv.composeapp.generated.resources.Res.string.devices_last_seen,
        lastSeen.formatDynamic(tss.t.tsiptv.utils.TimeStampFormat.yyyyMMdd_HHmmss.formatStr),
    )
