package tss.t.tsiptv.ui.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.ui.themes.TSColors

@Composable
fun HeaderWithAvatar(
    modifier: Modifier = Modifier,
    helloTitle: String,
    name: String,
    notificationCount: Int = 0,
    avatarUrl: String? = null,
    onNotificationClick: () -> Unit = {},
    onSettingClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(avatarUrl, label = "avatar") {
            if (it == null) {
                AppLogoCircle(
                    size = 40.dp,
                    iconSize = 20.dp,
                    blurRadius = 30.dp,
                    modifier = Modifier.clickable(onClick = onAvatarClick)
                )
            } else {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier.size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                helloTitle,
                style = MaterialTheme.typography.titleMedium
                    .copy(
                        color = TSColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
            )
            Spacer(Modifier.width(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium
                    .copy(
                        color = TSColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
            )
        }
//        Spacer(Modifier.width(16.dp))
//        NotificationDot(
//            notificationCount,
//            onClick = onNotificationClick
//        )
        Spacer(Modifier.width(16.dp))
        Image(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Menu",
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onSettingClick)
                .padding(8.dp)
                .size(24.dp),
            colorFilter = ColorFilter.tint(TSColors.TextSecondary)
        )
    }
}

@Composable
fun NotificationDot(
    count: Int,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(24.dp)
    ) {
        Image(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = "Notification",
            modifier = Modifier.size(24.dp)
                .align(Alignment.BottomCenter),
            colorFilter = ColorFilter.tint(TSColors.TextSecondary)
        )
        AnimatedVisibility(count > 0, modifier = Modifier.align(Alignment.TopEnd)) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TSColors.RedNotify)
            )
        }
    }
}

@Composable
@Preview
fun HeaderWithAvatarPreview() {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(TSColors.BackgroundColor)
            .padding(16.dp)
    ) {
        HeaderWithAvatar(
            helloTitle = "Hello",
            name = "tss@gmail.com",
            notificationCount = 10
        )
    }
}