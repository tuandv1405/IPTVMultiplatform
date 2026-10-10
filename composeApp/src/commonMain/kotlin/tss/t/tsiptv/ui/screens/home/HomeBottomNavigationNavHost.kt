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
import androidx.compose.foundation.layout.statusBarsPadding
import tss.t.tsiptv.core.stremio.MediaHistoryRepository
import tss.t.tsiptv.ui.screens.discover.DiscoverContent
import tss.t.tsiptv.ui.screens.discover.DiscoverNav
import tss.t.tsiptv.ui.screens.discover.DiscoverViewModel
import tss.t.tsiptv.ui.screens.history.mediaHistorySection
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.saveable.rememberSaveable
import tss.t.tsiptv.ui.screens.source.SourceHomeActions
import tss.t.tsiptv.ui.screens.source.SourceHomeContent
import tss.t.tsiptv.ui.screens.source.SourceHomeSwitch
import tss.t.tsiptv.ui.screens.source.SourceHomeViewModel
import tss.t.tsiptv.ui.screens.source.toColors

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

            val channelList = @Composable {
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
            }
            // F3: a TS IPTV Source shows its Home; a switch leads to the plain channel list.
            val sourcePlaylistId = homeUiState.playListId?.takeIf { homeUiState.isSourcePlaylist }
            if (sourcePlaylistId == null) {
                channelList()
            } else {
                val sourceViewModel = koinViewModel<SourceHomeViewModel>(viewModelStoreOwner = viewModelStoreOwner)
                val sourceState by sourceViewModel.state.collectAsStateWithLifecycle()
                var showChannels by rememberSaveable(sourcePlaylistId) { mutableStateOf(false) }
                if (showChannels && sourceState.hasChannels) {
                    Column(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            tss.t.tsiptv.ui.screens.addons.LocalAddonAccent provides sourceState.effective.toColors().accent,
                        ) {
                            SourceHomeSwitch(true, onChange = { showChannels = it }, modifier = Modifier.statusBarsPadding())
                        }
                        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) { channelList() }
                    }
                } else {
                    SourceHomeContent(
                        modifier = Modifier.statusBarsPadding(),
                        playlistId = sourcePlaylistId,
                        viewModel = sourceViewModel,
                        actions = SourceHomeActions(
                            onPlayChannel = { channel, zap -> onHomeEvent(HomeEvent.OnPlaySourceChannel(channel, zap)) },
                            onNavigate = { rootNavController.navigate(it) },
                            onOpenAbout = { rootNavController.navigate(NavRoutes.SourceAbout(sourcePlaylistId)) },
                            onRefresh = { onHomeEvent(HomeEvent.RefreshIPTVSource) },
                            onOpenSettings = { showBottomSheet = true },
                        ),
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        // Always emitted (QC F3 r2 #1): an item that appears after the first load would
                        // sit above the viewport, the list staying anchored on the next item.
                        header = {
                            if (sourceState.hasChannels) SourceHomeSwitch(false, onChange = { showChannels = it })
                        },
                    )
                }
            }

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

        // F2: Discover tab (shown only while an addon is enabled, see HomeBottomNavigationScreen).
        composable(route = NavRoutes.HomeScreens.DISCOVER) {
            val discoverViewModel = koinViewModel<DiscoverViewModel>(viewModelStoreOwner = viewModelStoreOwner)
            DiscoverContent(
                viewModel = discoverViewModel,
                onNavigate = { rootNavController.navigate(it) },
                modifier = Modifier.statusBarsPadding(),
                contentPadding = contentPadding,
            )
        }

        composable(route = NavRoutes.HomeScreens.HISTORY) {
            val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()
            val mediaHistory: MediaHistoryRepository = koinInject()
            val mediaRecords by mediaHistory.history.collectAsStateWithLifecycle(emptyList())
            val historyScope = rememberCoroutineScope()

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
                },
                hasMediaHistory = mediaRecords.isNotEmpty(),
                mediaSection = {
                    mediaHistorySection(
                        records = mediaRecords,
                        onOpen = { rootNavController.navigate(DiscoverNav.continueWatching(it)) },
                        onRemove = { historyScope.launch { mediaHistory.remove(it) } },
                        onClear = { historyScope.launch { mediaHistory.clear() } },
                    )
                },
            )
        }

        composable(
            route = NavRoutes.HomeScreens.PROFILE,
        ) {
            val appRating: AppRatingController = koinInject()
            val scope = rememberCoroutineScope()
            var showOpenFailed by remember { mutableStateOf(false) }
            // UMP: "Privacy options" only where the consent rules require it (docs/prd-admob.md §5).
            val adsPlatform: tss.t.tsiptv.core.ads.AdsPlatform = koinInject()
            val privacyOptionsRequired by adsPlatform.privacyOptionsRequired.collectAsStateWithLifecycle()

            ProfileScreen(
                authState = authState,
                hazeState = hazeState,
                showRateApp = appRating.canOpenStoreListing,
                showPrivacyOptions = privacyOptionsRequired,
            ) { event ->
                val action = (event as? LoginEvents.OnProfileActionEvent)?.action
                when (action) {
                    ProfileScreenActions.RateApp -> scope.launch {
                        if (!appRating.openStoreListing()) showOpenFailed = true
                    }

                    ProfileScreenActions.Addons -> rootNavController.navigate(NavRoutes.Addons)

                    // docs/prd-subscriptions.md §3.2: the plans screen replaces the "coming soon" dialog.
                    ProfileScreenActions.Subscription -> rootNavController.navigate(NavRoutes.Plans)

                    ProfileScreenActions.PrivacyOptions -> adsPlatform.showPrivacyOptions()

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
