package tss.t.tsiptv.ui.screens.addiptv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import tss.t.tsiptv.ui.themes.TSColors

// Main Palette
val TextSecondary = Color(0xFFA0AEC0)

// Accent Colors
val PrimaryBlue = Color(0xFF3B82F6)
val GlowBlue = Color(0xFF3B82F6)

enum class ChannelStatus(val title: String, val color: Color) {
    ACTIVE("Active", TSColors.ActiveGreen),
    LOADING("Loading", TSColors.LoadingYellow)
}

data class Channel(
    val id: Int,
    val name: String,
    val category: String,
    val country: String,
    val icon: ImageVector,
    val iconBackgroundColor: Color,
    val status: ChannelStatus,
)

val sampleChannels = listOf(
    Channel(
        id = 1,
        name = "BBC One HD",
        category = "Entertainment",
        country = "UK",
        icon = Icons.Default.Tv,
        iconBackgroundColor = PrimaryBlue,
        status = ChannelStatus.ACTIVE
    ),
    Channel(
        id = 2, name = "HBO Max",
        category = "Movies",
        country = "Premium",
        icon = Icons.Default.Movie,
        iconBackgroundColor = TSColors.ErrorRed, status = ChannelStatus.ACTIVE
    ),
    Channel(
        id = 3,
        name = "ESPN Sports",
        category = "Sports",
        country = "Live",
        icon = Icons.Default.SportsSoccer,
        iconBackgroundColor = Color(0xFFF59E0B),
        status = ChannelStatus.LOADING
    ),
)