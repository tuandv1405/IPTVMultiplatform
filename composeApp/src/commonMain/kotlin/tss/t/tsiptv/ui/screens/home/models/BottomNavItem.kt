package tss.t.tsiptv.ui.screens.home.models

import androidx.compose.ui.graphics.vector.ImageVector
/**
 * Data class representing a bottom navigation item.
 */
data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val iconUrl: String? = null,
    val labelRes: String? = null,
)