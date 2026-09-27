package tss.t.tsiptv.ui.screens.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.error_occurred
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.open_link_failed
import tss.t.tsiptv.core.rating.AppRatingController
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.profile.ProfileScreenActions
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.utils.AppLinks
import tss.t.tsiptv.utils.getUrlOpener
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.core.database.entity.toPlaylist
import tss.t.tsiptv.core.permission.Permission
import tss.t.tsiptv.core.permission.PermissionCheckerFactory
import tss.t.tsiptv.core.permission.PermissionExample
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.ads.AdsViewModel
import tss.t.tsiptv.ui.screens.history.HistoryScreen
import tss.t.tsiptv.ui.screens.home.homeiptvlist.HomeChangeIPTVSourceBottomSheet
import tss.t.tsiptv.ui.screens.home.homeiptvlist.HomeIPTVPlaylistScreen
import tss.t.tsiptv.ui.screens.home.homeiptvlist.HomeSettingOptionsBottomSheet
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.screens.profile.ProfileScreen
import tss.t.tsiptv.ui.screens.programs.ChannelXProgramListScreen
import tss.t.tsiptv.ui.screens.programs.ProgramViewModel
import tss.t.tsiptv.ui.screens.programs.uimodel.ProgramEvent
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner

/**
 * Navigation host for the Home screen sections.
 * This handles navigation between different tabs in the bottom navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBottomNavigationNavHost(
    navController: NavHostController,
    rootNavController: NavHostController,
    modifier: Modifier = Modifier,
    totalPlaylist: List<PlaylistWithChannelCount>,
    hazeState: HazeState,
    contentPadding: PaddingValues,
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    onHomeEvent: (HomeEvent) -> Unit = {},
) {
    val viewModelStoreOwner = LocalAppViewModelStoreOwner.current!!
    val authViewModel: AuthViewModel = koinViewModel(viewModelStoreOwner = viewModelStoreOwner)
    val playerViewModel = koinViewModel<PlayerViewModel>(viewModelStoreOwner = viewModelStoreOwner)
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val adsViewModel = koinViewModel<AdsViewModel>()
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HomeScreens.HOME_FEED,
        modifier = modifier,
        enterTransition = { defaultEnterTransition },
        exitTransition = { defaultExitTransition },
        popExitTransition = { defaultExitTransition },
        popEnterTransition = { defaultEnterTransition }
    ) {
        composable(route = NavRoutes.HomeScreens.HOME_FEED) {
            var showBottomSheet by remember { mutableStateOf(false) }
            var showChangeBottomSheet by remember { mutableStateOf(false) }

            HomeIPTVPlaylistScreen(
                navController = navController,
                parentNavController = rootNavController,
                hazeState = hazeState,
                homeUiState = homeUiState,
                onHomeEvent = {
                    when (it) {
                        HomeEvent.OnHomeFeedSettingPressed -> {
                            showBottomSheet = true
                        }

                        HomeEvent.OnHomeFeedNotificationPressed -> {
                            showBottomSheet = true
                        }

                        else -> onHomeEvent(it)
                    }
                },
                contentPadding = contentPadding,
                playerUIState = playerUIState
            )

            if (showBottomSheet) {
                HomeSettingOptionsBottomSheet(
                    parentNavController = rootNavController,
                    onDismissRequest = {
                        showBottomSheet = false
                    },
                    onHomeEvent = {
                        when (it) {
                            is HomeEvent.OnChangeIPTVSourcePressed -> {
                                showChangeBottomSheet = true
                            }

                            else -> onHomeEvent(it)
                        }
                    }
                )
            }

            if (showChangeBottomSheet) {
                HomeChangeIPTVSourceBottomSheet(
                    totalPlaylist = totalPlaylist,
                    currentPlaylistId = homeUiState.playListId ?: "",
                    hazeState = hazeState,
                    onChange = {
                        onHomeEvent(HomeEvent.OnRequestChangePlaylist(it.playlist.toPlaylist()))
                    },
                    onDismissRequest = {
                        showChangeBottomSheet = false
                    }
                )
            }
        }

        composable(route = NavRoutes.HomeScreens.HISTORY) {
            val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()

            HistoryScreen(
                hazeState = hazeState,
                homeUiState = homeUiState,
                navController = navController,
                parentNavController = rootNavController,
                playerUIState = playerUIState,
                mediaItem = mediaItem,
                contentPadding = contentPadding,
                onHomeEvent = onHomeEvent,
                onPlay = { channel ->
                    onHomeEvent(HomeEvent.OnPlayNowPlaying(channel))
                },
                onPause = { channel ->
                    onHomeEvent(HomeEvent.OnPauseNowPlaying(channel))
                }
            )
        }

        composable(
            route = NavRoutes.HomeScreens.PROFILE,
        ) {
            val appRating: AppRatingController = koinInject()
            val scope = rememberCoroutineScope()
            var showOpenFailed by remember { mutableStateOf(false) }

            ProfileScreen(
                authState = authState,
                hazeState = hazeState,
                showRateApp = appRating.canOpenStoreListing,
            ) { event ->
                val action = (event as? LoginEvents.OnProfileActionEvent)?.action
                when (action) {
                    ProfileScreenActions.RateApp -> scope.launch {
                        if (!appRating.openStoreListing()) showOpenFailed = true
                    }

                    ProfileScreenActions.BecomeContributor -> scope.launch {
                        if (!getUrlOpener().openUrl(AppLinks.CONTRIBUTOR_URL)) {
                            showOpenFailed = true
                        }
                    }

                    else -> authViewModel.onEvent(event)
                }
            }

            if (showOpenFailed) {
                TSDialog(
                    title = stringResource(Res.string.error_occurred),
                    message = stringResource(Res.string.open_link_failed),
                    positiveButtonText = stringResource(Res.string.ok),
                    onPositiveClick = { showOpenFailed = false },
                    onDismissRequest = { showOpenFailed = false },
                )
            }
        }

        composable(
            route = NavRoutes.HomeScreens.PROGRAM
        ) {
            val programViewModel: ProgramViewModel =
                koinViewModel(viewModelStoreOwner = viewModelStoreOwner)
            val uiState by programViewModel.listProgramUIState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                programViewModel.event.collect {
                    if (it is ProgramEvent.NavigateToDetail) {
                        val program = it.channel
                        rootNavController.navigate(
                            route = NavRoutes.ProgramDetail(program)
                        )
                    }
                }
            }
            ChannelXProgramListScreen(
                uiState = uiState,
                adsViewModel = adsViewModel
            )
        }
    }
}

private val defaultExitTransition: ExitTransition = fadeOut(
    targetAlpha = 0.3f,
    animationSpec = tween(
        durationMillis = 90,
        easing = LinearEasing
    )
)

private val defaultEnterTransition: EnterTransition = fadeIn(
    initialAlpha = 0.3f,
    animationSpec = tween(
        durationMillis = 200,
        easing = FastOutLinearInEasing,
        delayMillis = 0
    )
) + scaleIn(
    initialScale = 0.9f,
    animationSpec = tween(
        durationMillis = 200,
        easing = FastOutLinearInEasing,
        delayMillis = 0
    )
)
