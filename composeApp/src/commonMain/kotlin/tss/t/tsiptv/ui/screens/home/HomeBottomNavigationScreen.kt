package tss.t.tsiptv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.home_nav_history
import tsiptv.composeapp.generated.resources.discover_title
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel
import tss.t.tsiptv.ui.screens.discover.DiscoverViewModel
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner
import tsiptv.composeapp.generated.resources.home_nav_main
import tsiptv.composeapp.generated.resources.home_nav_profile
import tsiptv.composeapp.generated.resources.home_nav_programs
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.addiptv.importErrorMessage
import tss.t.tsiptv.ui.screens.home.models.BottomNavItem
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.themes.TSColors
import tss.t.tsiptv.ui.themes.TSShapes
import tss.t.tsiptv.ui.widgets.ErrorDialog
import tss.t.tsiptv.utils.customShadow
import kotlin.time.ExperimentalTime

internal val defNavItems = listOf(
    BottomNavItem(
        route = NavRoutes.HomeScreens.HOME_FEED,
        icon = Icons.Rounded.Home,
        labelRes = Res.string.home_nav_main
    ),
    BottomNavItem(
        route = NavRoutes.HomeScreens.HISTORY,
        icon = Icons.Rounded.History,
        labelRes = Res.string.home_nav_history
    ),
    BottomNavItem(
        route = NavRoutes.HomeScreens.PROGRAM,
        icon = Icons.Rounded.Schedule,
        labelRes = Res.string.home_nav_programs
    ),
    BottomNavItem(
        route = NavRoutes.HomeScreens.PROFILE,
        icon = Icons.Rounded.Person,
        labelRes = Res.string.home_nav_profile
    )
)

internal val discoverNavItem = BottomNavItem(
    route = NavRoutes.HomeScreens.DISCOVER,
    icon = Icons.Rounded.Explore,
    labelRes = Res.string.discover_title
)

/**
 * Home screen of the application with bottom navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun HomeBottomNavigationScreen(
    hazeState: HazeState,
    parentNavController: NavHostController,
    totalPlaylist: List<PlaylistWithChannelCount>,
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    onHomeEvent: (HomeEvent) -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // F2: "Discover" (between Home and History) exists only while an addon is enabled.
    val discoverViewModel = koinViewModel<DiscoverViewModel>(viewModelStoreOwner = LocalAppViewModelStoreOwner.current!!)
    val hasAddons by discoverViewModel.hasActiveAddons.collectAsState()
    val backStack by navController.currentBackStack.collectAsState()
    val discoverInStack = backStack.any { it.destination.route == NavRoutes.HomeScreens.DISCOVER }
    val showDiscoverTab = hasAddons == true || (hasAddons == null && discoverInStack)
    val bottomNavItems = remember(showDiscoverTab) {
        // Unknown (null): the tab stays only for a restored Discover screen (no flicker for users
        // without addons, no orphan for users with them).
        if (showDiscoverTab) defNavItems.take(1) + discoverNavItem + defNavItems.drop(1) else defNavItems
    }
    LaunchedEffect(hasAddons) {
        // Discover disappeared (last addon removed/disabled): no Discover entry may stay anywhere in
        // the tab back stack, or Back would lead to an orphan screen.
        // Only on a known "no active addon", never on the unknown initial state.
        if (hasAddons == false && navController.currentBackStack.value.any { it.destination.route == NavRoutes.HomeScreens.DISCOVER }) {
            navController.navigate(NavRoutes.HomeScreens.HOME_FEED) {
                popUpTo(NavRoutes.HomeScreens.HOME_FEED) { inclusive = false }
                launchSingleTop = true
            }
            // And no saved Discover state that restoreState could bring back later.
            runCatching { navController.clearBackStack(NavRoutes.HomeScreens.DISCOVER) }
        }
    }

    // Track the current selected item
    var selectedItemIndex by remember { mutableStateOf(0) }

    LaunchedEffect(navBackStackEntry?.destination?.route, bottomNavItems) {
        selectedItemIndex = bottomNavItems.indexOfFirst {
            it.route == navBackStackEntry?.destination?.route
        }
    }

    // A tapped notification that links to Home also selects the Home tab.
    val pushManager: tss.t.tsiptv.feature.push.PushManager = org.koin.compose.koinInject()
    val homeTabRequested by pushManager.homeTabRequested.collectAsState()
    LaunchedEffect(homeTabRequested) {
        if (!homeTabRequested) return@LaunchedEffect
        pushManager.homeTabShown()
        if (navController.currentDestination?.route != NavRoutes.HomeScreens.HOME_FEED) {
            navController.navigate(NavRoutes.HomeScreens.HOME_FEED) {
                popUpTo(NavRoutes.HomeScreens.HOME_FEED) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        onHomeEvent(HomeEvent.RefreshEpgIfNeed)
        onPauseOrDispose {
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

        HomeBottomNavigationNavHost(
            navController = navController,
            rootNavController = parentNavController,
            modifier = Modifier
                .fillMaxSize(),
            totalPlaylist = totalPlaylist,
            hazeState = hazeState,
            contentPadding = paddingValues,
            homeUiState = homeUiState,
            playerUIState = playerUIState,
            onHomeEvent = onHomeEvent,
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        BottomAppBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
            bottomNavItems = bottomNavItems,
            selectedItemIndex = selectedItemIndex,
            navController = navController
        ) {
            selectedItemIndex = it
        }
    }

    // Error Dialog for HomeViewModel Errors
    homeUiState.error?.let { error ->
        ErrorDialog(
            errorMessage = importErrorMessage(error),
            onDismiss = {
                // Clear the error from HomeViewModel
                onHomeEvent(HomeEvent.OnDismissErrorDialog)
            }
        )
    }
}

@Composable
private fun BoxScope.BottomAppBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    bottomNavItems: List<BottomNavItem>,
    selectedItemIndex: Int,
    navController: NavHostController,
    onItemChanged: (Int) -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth()
            .customShadow(
                borderRadius = 5.dp,
                blurRadius = 30.dp,
                offsetX = 0.dp,
                offsetY = (-2).dp,
                color = remember {
                    Color(0xFF00F5FF).copy(alpha = 0.1f)
                }
            )
            .clip(shape = TSShapes.roundShapeTop32)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = TSShapes.roundShapeTop32
            )
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
                                    onItemChanged(index)
                                    // Standard bottom-nav behaviour: one entry per tab above the start
                                    // destination, so tabs never pile up (or leave an orphan Discover).
                                    navController.navigate(item.route) {
                                        popUpTo(NavRoutes.HomeScreens.HOME_FEED) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
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
                            BasicText(
                                item.labelRes?.let {
                                    stringResource(it)
                                } ?: item.label ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme
                                            .onSurface.copy(alpha = 0.6f)
                                    }
                                ),
                                autoSize = remember {
                                    TextAutoSize.StepBased(
                                        minFontSize = 8.sp,
                                        maxFontSize = 12.sp,
                                        stepSize = 0.5.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.navigationBarsPadding())
        }
    }
}
