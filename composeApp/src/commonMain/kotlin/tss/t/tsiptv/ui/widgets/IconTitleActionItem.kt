package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tss.t.tsiptv.ui.themes.TSColors


@Composable
fun IconTitleActionItem(
    iconRes: DrawableResource,
    title: String,
    description: String? = null,
    colors: IconTitleActionItemDefaults.DefColors = IconTitleActionItemDefaults.colors(),
    onClick: () -> Unit = {},
    paddingValues: PaddingValues = IconTitleActionItemDefaults.paddingValues,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(paddingValues),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = colors.iconTinColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Companion.Medium,
                color = colors.titleTextColor
            )

            description?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = colors.descriptionTextColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun IconTitleActionItem(
    imageVector: ImageVector,
    title: String,
    description: String? = null,
    colors: IconTitleActionItemDefaults.DefColors = IconTitleActionItemDefaults.colors(),
    onClick: () -> Unit = {},
    paddingValues: PaddingValues = IconTitleActionItemDefaults.paddingValues,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(paddingValues),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = colors.iconTinColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Companion.Medium,
                color = colors.titleTextColor
            )
            description?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = colors.descriptionTextColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

object IconTitleActionItemDefaults {

    val paddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    @Immutable
    data class DefColors(
        val titleTextColor: Color,
        val descriptionTextColor: Color,
        val bgColor: Color = Color.Unspecified,
        val iconTinColor: Color = TSColors.White,
    )

    private val _def = DefColors(
        titleTextColor = TSColors.TextPrimary,
        descriptionTextColor = TSColors.SecondaryBackgroundColor,
    )

    @Composable
    fun colors(
        titleTextColor: Color = TSColors.TextPrimary,
        descriptionTextColor: Color = TSColors.SecondaryBackgroundColor,
        bgColor: Color = Color.Unspecified,
        iconTinColor: Color = TSColors.White,
    ) = _def.copy(
        titleTextColor = titleTextColor,
        descriptionTextColor = descriptionTextColor,
        bgColor = bgColor,
        iconTinColor = iconTinColor
    )
}
