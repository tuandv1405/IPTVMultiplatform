package tss.t.tsiptv.ui.screens.addiptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import tss.t.tsiptv.utils.customShadow
import tss.t.tsiptv.utils.blur

// Main Palette
val DarkNavy = Color(0xFF1A202C)
val SlateGray = Color(0xFF2D3748)
val LightSlate = Color(0xFF4A5568)
val TextPrimary = Color.White
val TextSecondary = Color(0xFFA0AEC0)

// Accent Colors
val PrimaryBlue = Color(0xFF3B82F6)
val GlowBlue = Color(0xFF3B82F6)
val ActiveGreen = Color(0xFF34D399)
val LoadingYellow = Color(0xFFFBBF24)
val ErrorRed = Color(0xFFF87171)

// Chip Colors
val ChipBlue = Color(0xFF3B82F6)
val ChipGreen = Color(0xFF10B981)
val ChipPurple = Color(0xFF8B5CF6)

enum class ChannelStatus(val title: String, val color: Color) {
    ACTIVE("Active", ActiveGreen),
    LOADING("Loading", LoadingYellow)
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
        iconBackgroundColor = ErrorRed, status = ChannelStatus.ACTIVE
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportIPTVScreen(
    hazeState: HazeState,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Scaffold(
        topBar = { IPTVAppBar(modifier = Modifier.hazeEffect(hazeState)) },
        containerColor = DarkNavy,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                keyboardController?.hide()
            }
        )
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .hazeSource(hazeState)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { Box(Modifier.height(paddingValues.calculateTopPadding())) }
            item { ImportCard() }
            item { ParsedChannelsCard(channels = sampleChannels) }
            item { Box(Modifier.height(paddingValues.calculateBottomPadding() + 16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IPTVAppBar(modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        title = { Text("IPTV List Parser", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SlateGray.copy(alpha = 0.7f), // Make it slightly transparent for better blur effect
            titleContentColor = TextPrimary,
            actionIconContentColor = TextPrimary,
            navigationIconContentColor = TextPrimary,
            scrolledContainerColor = Color.Transparent
        )
    )
}

@Composable
private fun ImportCard() {
    var url by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("M3U") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Import IPTV List",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste URL or select file...", color = TextSecondary) },
                trailingIcon = {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = TextSecondary
                    )
                },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = DarkNavy,
                    focusedContainerColor = DarkNavy,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = PrimaryBlue,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, LightSlate)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload File", color = TextPrimary)
                }
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, LightSlate)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("From URL", color = TextPrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("M3U", "JSON", "XML").forEach { format ->
                    SelectableChip(
                        text = format,
                        isSelected = selectedFormat == format,
                        onClick = { selectedFormat = format }
                    )
                }
            }

            // Glowing Button using our custom modifier
            Button(
                onClick = { /* Handle Parse */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .customShadow(
                        color = GlowBlue.copy(alpha = 0.5f),
                        blurRadius = 20.dp,
                        offsetY = 4.dp,
                        borderRadius = 12.dp
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(PrimaryBlue, Color(0xFF60A5FA)))),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Parse List", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ParsedChannelsCard(channels: List<Channel>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateGray)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Parsed Channels",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${channels.size} channels found",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            channels.forEach { channel ->
                ChannelListItem(channel)
            }

            TextButton(
                onClick = { /* Handle View All */ },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "View All",
                    tint = TextSecondary
                )
                Text("View All Channels", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChannelListItem(channel: Channel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(channel.iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = channel.icon,
                contentDescription = channel.name,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                "${channel.category} • ${channel.country}",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.width(16.dp))

        StatusIndicator(channel.status)
    }
}

@Composable
private fun StatusIndicator(status: ChannelStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(status.color, CircleShape)
        )
        Text(status.title, color = status.color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}


@Composable
private fun SelectableChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) PrimaryBlue else LightSlate
    val contentColor = if (isSelected) TextPrimary else TextSecondary

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text)
    }
}


@Composable
private fun IPTVBottomNav() {
    var selectedItem by remember { mutableStateOf(1) } // 1 for "Lists"
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Lists" to Icons.Default.List,
        "Favorites" to Icons.Default.Favorite,
        "Settings" to Icons.Default.Settings
    )

    // Apply blur effect to the NavigationBar
    NavigationBar(
        modifier = Modifier.blur(10.dp),
        containerColor = SlateGray.copy(alpha = 0.7f), // Make it slightly transparent for better blur effect
        contentColor = TextSecondary
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(item.second, contentDescription = item.first) },
                label = { Text(item.first) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = SlateGray.copy(alpha = 0.0f) // Transparent indicator for iOS style
                )
            )
        }
    }
}
