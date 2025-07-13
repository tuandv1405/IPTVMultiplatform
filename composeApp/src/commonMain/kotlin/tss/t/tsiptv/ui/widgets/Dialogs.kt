package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.ui.themes.StreamVaultTheme
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.utils.customShadow

@Immutable
enum class DialogButtonFlowLayout {
    Row,
    Column
}

private val contentRow: @Composable RowScope.(
    positiveButtonText: String?,
    negativeButtonText: String?,
    onPositiveClick: () -> Unit,
    onNegativeClick: () -> Unit,
) -> Unit = @Composable {
        positiveButtonText,
        negativeButtonText,
        onPositiveClick,
        onNegativeClick,
    ->
    if (negativeButtonText != null) {
        GrayButton(
            text = negativeButtonText,
            onClick = onNegativeClick,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
    }

    if (positiveButtonText != null) {
        GradientButton2(
            text = positiveButtonText,
            onClick = onPositiveClick,
            modifier = Modifier.weight(1f)
        )
    }
}

private val contentColumn: @Composable ColumnScope.(
    positiveButtonText: String?,
    negativeButtonText: String?,
    onPositiveClick: () -> Unit,
    onNegativeClick: () -> Unit,
) -> Unit = @Composable {
        positiveButtonText,
        negativeButtonText,
        onPositiveClick,
        onNegativeClick,
    ->
    if (positiveButtonText != null) {
        GradientButton2(
            text = positiveButtonText,
            onClick = onPositiveClick,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (negativeButtonText != null) {
        GrayButton(
            text = negativeButtonText,
            onClick = onNegativeClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TSDialog(
    title: String = "Title",
    message: String = "Message",
    icon: @Composable (() -> Unit)? = null,
    buttonFlowLayout: DialogButtonFlowLayout = DialogButtonFlowLayout.Row,
    positiveButtonText: String? = null,
    negativeButtonText: String? = null,
    onPositiveClick: () -> Unit = {},
    onNegativeClick: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                modifier = Modifier.customShadow(
                    borderRadius = 20.dp,
                    blurRadius = 50.dp,
                    color = Color.Black.copy(0.25f)
                )
                    .clip(TSShapes.roundedShape20)
                    .background(Color.White, TSShapes.roundedShape20)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                icon?.let {
                    it()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = title,
                    color = TSColors.TextTitlePrimaryDart,
                    style = MaterialTheme.typography.titleMedium
                        .copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            lineHeight = 28.sp
                        ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    color = TSColors.TextBodyPrimaryDart,
                    style = MaterialTheme.typography.bodyMedium
                        .copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 23.sp
                        ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (buttonFlowLayout == DialogButtonFlowLayout.Row) {
                    Row {
                        contentRow(
                            positiveButtonText,
                            negativeButtonText,
                            onPositiveClick,
                            onNegativeClick,
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        contentColumn(
                            positiveButtonText,
                            negativeButtonText,
                            onPositiveClick,
                            onNegativeClick,
                        )
                    }
                }

            }
        }
    )
}

@Composable
@Preview
fun DialogPreview_C() {
    StreamVaultTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TSDialog(
                title = "Title",
                message = "Unlock premium features to get the most out of your experience with advanced tools and insights.",
                icon = null,
                buttonFlowLayout = DialogButtonFlowLayout.Column,
                positiveButtonText = "Positive",
                negativeButtonText = "Negative",
                onPositiveClick = {},
                onNegativeClick = {},
            )
        }
    }
}

@Composable
@Preview
fun DialogPreview_R() {
    StreamVaultTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TSDialog(
                title = "Title",
                message = "Unlock premium features to get the most out of your experience with advanced tools and insights.",
                icon = null,
                buttonFlowLayout = DialogButtonFlowLayout.Row,
                positiveButtonText = "Positive",
                negativeButtonText = "Negative",
                onPositiveClick = {},
                onNegativeClick = {},
            )
        }
    }
}

@Composable
@Preview
fun DialogPreview_S() {
    StreamVaultTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TSDialog(
                title = "Title",
                message = "Unlock premium features to get the most out of your experience with advanced tools and insights.",
                positiveButtonText = "Positive",
                onPositiveClick = {},
            )
        }
    }
}