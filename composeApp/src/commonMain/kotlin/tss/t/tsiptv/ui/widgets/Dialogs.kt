package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.room.util.TableInfo
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.utils.customShadow

@Composable
@Preview
fun TSDialog() {
    Dialog(
        onDismissRequest = {

        },
        content = {
            Box {
                Column(
                    modifier = Modifier
                        .customShadow(
                            borderRadius = 20.dp,
                            blurRadius = 50.dp,
                            offsetY = 25.dp,
                            color = Color.Black.copy(0.25f)
                        )
                        .clip(TSShapes.roundedShape20)
                        .background(Color.White, TSShapes.roundedShape20)
                        .border(
                            width = 1.dp,
                            color = TSColors.strokeWhite,
                            shape = TSShapes.roundedShape20
                        )
                        .padding(20.dp)
                ) {

                }
            }
        }
    )
}
