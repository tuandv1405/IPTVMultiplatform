package tss.t.tsiptv.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import tsiptv.composeapp.generated.resources.Res
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.home.models.BottomNavItem
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.HeaderWithAvatar
import tss.t.tsiptv.utils.customShadow

internal val defNavItems = listOf(
    BottomNavItem(
        route = NavRoutes.HomeScreens.HOME_FEED,
        icon = Icons.Default.Home,
        label = "Home"
    ),
    BottomNavItem(
        route = NavRoutes.HomeScreens.FAVORITES,
        icon = Icons.Default.Favorite,
        label = "Favorites"
    ),
    BottomNavItem(
        route = NavRoutes.HomeScreens.SETTINGS,
        icon = Icons.Default.Settings,
        label = "Settings"
    ),
    BottomNavItem(
        route = NavRoutes.HomeScreens.PROFILE,
        icon = Icons.Default.Person,
        label = "Profile",
    )
)

/**
 * Home screen of the application with bottom navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    hazeState: HazeState,
    parentNavController: NavHostController,
    authState: AuthUiState,
) {
    // Create a nav controller for the bottom navigation
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Define the bottom navigation items
    val bottomNavItems = remember {
        defNavItems
    }

    // Track the current selected item
    var selectedItemIndex by remember { mutableStateOf(0) }

    LaunchedEffect(navBackStackEntry?.destination?.route) {
        selectedItemIndex = bottomNavItems.indexOfFirst {
            it.route == navBackStackEntry?.destination?.route
        }
    }

    Scaffold(
        bottomBar = {
            Box(Modifier.height(56.dp))
        }
    ) { paddingValues ->
        AsyncImage(
            model = Res.getUri("drawable/background.png"),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        HomeNavHost(
            navController = navController,
            parentNavController = parentNavController,
            modifier = Modifier
                .fillMaxSize(),
            hazeState = hazeState,
            paddingValues = paddingValues
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .customShadow(
                    borderRadius = 5.dp,
                    blurRadius = 30.dp,
                    offsetX = 0.dp,
                    offsetY = (-2).dp,
                    color = Color(0xFF00F5FF).copy(alpha = 0.1f)
                )
                .clip(shape = TSShapes.roundShapeTop32)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = TSShapes.roundShapeTop32
                )
                .align(Alignment.BottomCenter)
                .hazeEffect(
                    state = hazeState,
                    style = remember {
                        HazeDefaults.style(
                            backgroundColor = TSColors.SecondaryBackgroundColor,
                            tint = HazeTint(
                                color = TSColors.SecondaryBackgroundColor.copy(alpha = 0.1f)
                            ),
                        )
                    }
                ),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bottom navigation items
                    bottomNavItems.forEachIndexed { index, item ->
                        val isSelected = selectedItemIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedItemIndex == index) return@IconButton
                                        selectedItemIndex = index
                                        val inBackStack = navController.currentBackStack
                                            .value.find {
                                                it.destination.route == item.route
                                            }?.destination?.route

                                        if (inBackStack != null) {
                                            navController.popBackStack(
                                                route = inBackStack,
                                                inclusive = false
                                            )
                                        } else {
                                            navController.navigate(item.route)
                                        }
                                    },
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Icon(
                                        item.icon,
                                        contentDescription = item.label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme
                                            .onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme
                                        .onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

