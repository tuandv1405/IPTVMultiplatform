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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.PlaylistWithChannelCount
import tss.t.tsiptv.core.database.entity.toPlaylist
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.permission.Permission
import tss.t.tsiptv.core.permission.PermissionCheckerFactory
import tss.t.tsiptv.core.permission.PermissionExample
import tss.t.tsiptv.core.repository.IHistoryRepository
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.ui.screens.history.HistoryScreen
import tss.t.tsiptv.ui.screens.home.homeiptvlist.HomeChangeIPTVSourceBottomSheet
import tss.t.tsiptv.ui.screens.home.homeiptvlist.HomeFeedScreen
import tss.t.tsiptv.ui.screens.home.homeiptvlist.HomeSettingOptionsBottomSheet
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.player.PlayerUIState
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.screens.profile.ProfileScreen

/**
 * Navigation host for the Home screen sections.
 * This handles navigation between different tabs in the bottom navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNavHost(
    navController: NavHostController,
    parentNavController: NavHostController,
    modifier: Modifier = Modifier,
    totalPlaylist: List<PlaylistWithChannelCount>,
    hazeState: HazeState,
    contentPadding: PaddingValues,
    homeUiState: HomeUiState,
    playerUIState: PlayerUIState,
    authState: AuthUiState,
    onLoginEvent: (LoginEvents) -> Unit = {},
    onHomeEvent: (HomeEvent) -> Unit = {},
) {
    val viewStoreOwner = LocalViewModelStoreOwner.current!!
    val authRepository = koinInject<AuthRepository>()
    val authViewModel: AuthViewModel = viewModel(viewModelStoreOwner = viewStoreOwner) {
        AuthViewModel(
            authRepository = authRepository,
        )
    }
    val iptvDatabase = koinInject<IPTVDatabase>()
    val historyTracker = koinInject<ChannelHistoryTracker>()
    val mediaPlayer = koinInject<MediaPlayer>()
    val playerViewModel = viewModel<PlayerViewModel>(viewStoreOwner) {
        PlayerViewModel(
            _mediaPlayer = mediaPlayer,
            _iptvDatabase = iptvDatabase,
            historyTracker = historyTracker
        )
    }
    val authState by authViewModel.uiState.collectAsState()

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
            // Dialog-based bottom sheet state - BEST PRACTICE for dialog behavior
            var showBottomSheet by remember { mutableStateOf(false) }
            var showChangeBottomSheet by remember { mutableStateOf(false) }

            HomeFeedScreen(
                navController = navController,
                parentNavController = parentNavController,
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
                    parentNavController = parentNavController,
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

        composable(route = NavRoutes.HomeScreens.SETTINGS) {
            PermissionExample(
                permissionChecker = remember {
                    PermissionCheckerFactory.create()
                },
                permission = remember {
                    Permission.CAMERA
                }
            )
        }

        composable(route = NavRoutes.HomeScreens.HISTORY) {
            val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()

            HistoryScreen(
                hazeState = hazeState,
                homeUiState = homeUiState,
                navController = navController,
                parentNavController = parentNavController,
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
            ProfileScreen(
                authState = authState,
                hazeState = hazeState
            ) {
                authViewModel.onEvent(it)
            }
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
