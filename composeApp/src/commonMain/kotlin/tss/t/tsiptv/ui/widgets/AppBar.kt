package tss.t.tsiptv.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.home_nav_history
import tsiptv.composeapp.generated.resources.ic_back_navigation
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSTextStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun TSAppBarXBackIcon(
    modifier: Modifier = Modifier,
    title: String = "Add IPTV Source",
    backIcon: DrawableResource? = Res.drawable.ic_back_navigation,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    onBackClick: () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        expandedHeight = expandedHeight,
        windowInsets = windowInsets,
        navigationIcon = backIcon?.let {
            {
                Image(
                    modifier = Modifier.padding(start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBackClick)
                        .padding(16.dp),
                    contentDescription = "Back button",
                    painter = painterResource(backIcon),
                )
            }
        } ?: {},
        title = {
            Text(
                text = title,
                style = TSTextStyles.primaryToolbarTitle
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = TSColors.BackgroundColor,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TSAppBar(
    modifier: Modifier,
    title: String = stringResource(resource = Res.string.home_nav_history),
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = TSColors.Transparent,
        scrolledContainerColor = TSColors.Transparent
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        colors = colors,
        title = {
            Text(
                text = title,
                style = TSTextStyles.primaryToolbarTitle
            )
        },
        scrollBehavior = scrollBehavior,
        actions = actions
    )
}
