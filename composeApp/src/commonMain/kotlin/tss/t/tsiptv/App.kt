package tss.t.tsiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.error_occurred
import tsiptv.composeapp.generated.resources.iptv_import_success_msg
import tsiptv.composeapp.generated.resources.iptv_import_success_title
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.try_again
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.core.language.AppLocaleProvider
import tss.t.tsiptv.core.tracking.UserTrackingService
import tss.t.tsiptv.core.uimode.LocalIsTvMode
import tss.t.tsiptv.core.uimode.LocalUiMode
import tss.t.tsiptv.core.uimode.UiMode
import tss.t.tsiptv.core.uimode.UiModeRepository
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.navigation.isOnAuthEntryScreen
import tss.t.tsiptv.navigation.navigateAndRemoveFromBackStack
import tss.t.tsiptv.navigation.navigateHomeClearingBackStack
import tss.t.tsiptv.navigation.navtype.ChannelWithProgramCountNavType
import tss.t.tsiptv.ui.screens.addiptv.ImportIPTVScreen
import tss.t.tsiptv.ui.screens.home.HomeBottomNavigationScreen
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeViewModel
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.login.LoginScreenDesktop2
import tss.t.tsiptv.ui.screens.login.LoginScreenPhone
import tss.t.tsiptv.ui.screens.login.SignUpScreen
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.player.PlayerEvent
import tss.t.tsiptv.ui.screens.player.PlayerScreen
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.screens.programs.details.ProgramForChannelScreen
import tss.t.tsiptv.ui.screens.settings.LanguageSettingsScreen
import tss.t.tsiptv.ui.screens.splash.SplashScreen
import tss.t.tsiptv.ui.screens.webview.WebViewInApp
import tss.t.tsiptv.ui.themes.StreamVaultTheme
import tss.t.tsiptv.ui.tv.TvHomeScreen
import tss.t.tsiptv.ui.tv.TvPlayerScreen
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner
import tss.t.tsiptv.utils.PlatformUtils
import tss.t.tsiptv.utils.getScreenOrientationUtils
import kotlin.reflect.typeOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun App() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    val userTrackingService: UserTrackingService = koinInject()
    val authState by authViewModel.uiState.collectAsState()
    val uiModeRepository: UiModeRepository = koinInject()
    val uiMode by remember { uiModeRepository.observeUiMode() }
        .collectAsState(initial = UiMode.AUTO)
    val isTvMode = remember(uiMode) { uiMode.resolveIsTv(PlatformUtils.platform.isTv) }

    // The TV layout is landscape-only; on a phone that opted into it, hold the
    // screen sideways. A real TV is landscape already and is left alone.
    if (!PlatformUtils.platform.isTv) {
        DisposableEffect(isTvMode) {
            if (isTvMode) getScreenOrientationUtils().lockLandscape(true)
            onDispose {
                if (isTvMode) getScreenOrientationUtils().lockLandscape(false)
            }
        }
    }

    LaunchedEffect(
        authState.isAuthenticated,
        authState.isLoading,
        isTvMode,
    ) {
        if (authState.isLoading) {
            return@LaunchedEffect
        }
        when {
            authState.isAuthenticated -> {
                // Request App Tracking Transparency permission and set up analytics
                userTrackingService.requestTrackingPermissionAndSetupAnalytics(authState.user)
                // Only leave the sign-in screens; a layout switch while already
                // inside the app must not rebuild Home.
                if (navController.isOnAuthEntryScreen()) {
                    navController.navigateHomeClearingBackStack()
                }
            }

            // The TV layout works signed out: playlists and playback are local.
            // Leave the splash for Home, but stay put on Login (opened from
            // Settings, where a failed attempt must not bounce the user) and on
            // Home after logging out.
            isTvMode -> {
                if (navController.currentBackStackEntry?.destination
                        ?.hasRoute<NavRoutes.Splash>() != false
                ) {
                    navController.navigateHomeClearingBackStack()
                }
            }

            authState.isNetworkAvailable -> {
                navController.navigateAndRemoveFromBackStack(NavRoutes.Login)
            }
        }
    }

    AppLocaleProvider {
        val appViewModelStore = LocalAppViewModelStoreOwner.current!!

        CompositionLocalProvider(
            LocalUiMode provides uiMode,
            LocalIsTvMode provides isTvMode,
        ) {
            StreamVaultTheme {
                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.Splash,
                    modifier = Modifier,
                    builder = {
                        composable<NavRoutes.Login> {
                            val isDesktop = remember(PlatformUtils.platform) {
                                PlatformUtils.platform.isDesktop
                            }

                            AnimatedVisibility(authState.isLoading) {
                                BasicAlertDialog(
                                    modifier = Modifier.size(36.dp),
                                    onDismissRequest = { },
                                    content = {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(36.dp),
                                            color = ProgressIndicatorDefaults.circularColor
                                        )
                                    },
                                )
                            }

                            if (remember(
                                    authState.error,
                                    authState.isAuthenticated
                                ) {
                                    !authState.isAuthenticated && !authState.error.isNullOrEmpty()
                                }
                            ) {
                                TSDialog(
                                    onDismissRequest = {
                                        authViewModel.onEvent(LoginEvents.OnDismissErrorDialog)
                                    },
                                    title = stringResource(Res.string.error_occurred),
                                    message = authState.error ?: "",
                                    positiveButtonText = stringResource(Res.string.try_again),
                                    onPositiveClick = {
                                        authViewModel.onEvent(LoginEvents.OnDismissErrorDialog)
                                    }
                                )
                            }

                            // Both screens emit the same events; only the layout differs.
                            val onLoginEvent: (LoginEvents) -> Unit = { event ->
                                if (event is LoginEvents.OnSignUpPressed) {
                                    navController.navigate(NavRoutes.SignUp)
                                } else {
                                    authViewModel.onEvent(event)
                                }
                            }

                            // TV keeps the phone login: it has sign-up and password
                            // reset, and not the desktop layout's Google/Apple buttons,
                            // whose Android implementation is still a placeholder.
                            if (isDesktop) {
                                // Called without a handler until now, so every tap on
                                // the desktop login screen went to the default no-op
                                // and signing in was impossible.
                                LoginScreenDesktop2(onEvent = onLoginEvent)
                            } else {
                                LoginScreenPhone(navController, authState, onLoginEvent)
                            }
                        }

                        composable<NavRoutes.SignUp> {
                            AnimatedVisibility(authState.isLoading) {
                                BasicAlertDialog(
                                    modifier = Modifier.size(36.dp),
                                    onDismissRequest = { },
                                    content = {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(36.dp),
                                            color = ProgressIndicatorDefaults.circularColor
                                        )
                                    },
                                )
                            }

                            LaunchedEffect(authState.isAuthenticated) {
                                if (authState.isAuthenticated) {
                                    navController.navigateHomeClearingBackStack()
                                }
                            }

                            SignUpScreen(
                                authState = authState,
                                onEvent = { event ->
                                    if (event is LoginEvents.ShowLoginScreen) {
                                        navController.popBackStack()
                                    } else {
                                        authViewModel.onEvent(event)
                                    }
                                }
                            )
                        }

                        composable<NavRoutes.Splash>() {
                            SplashScreen(
                                navController = navController,
                                authState = authState,
                            )
                        }

                        composable<NavRoutes.Home>() {
                            val hazeState = rememberHazeState()
                            val homeViewModel = koinViewModel<HomeViewModel>(
                                viewModelStoreOwner = appViewModelStore
                            )
                            val playerViewModel = koinViewModel<PlayerViewModel>(
                                viewModelStoreOwner = appViewModelStore
                            )

                            val homeUIState by homeViewModel.uiState.collectAsState()
                            val totalPlaylist by homeViewModel.totalChannelList.collectAsState()
                            val playerUIState by playerViewModel.playerUIState.collectAsState()

                            val onHomeEvent: (HomeEvent) -> Unit = { homeEvent ->
                                if (homeEvent is HomeEvent.OnOpenVideoPlayer) {
                                    homeViewModel.getRelatedChannels(homeEvent.channel)
                                    homeViewModel.loadProgramForChannel(homeEvent.channel)
                                    playerViewModel.playIptv(homeEvent.channel)
                                    navController.navigate(NavRoutes.Player(homeEvent.channel.id))
                                    homeViewModel.onEmitEvent(HomeEvent.LoadHistory)
                                }


                                when (homeEvent) {
                                    is HomeEvent.OnPlayNowPlaying -> {
                                        homeViewModel.onEmitEvent(homeEvent)
                                        if (playerViewModel.mediaItemState.value.id != homeEvent.channel.id) {
                                            playerViewModel.playIptv(homeEvent.channel)
                                        } else {
                                            playerViewModel.onHandleEvent(PlayerEvent.Play)
                                        }
                                    }

                                    is HomeEvent.OnPauseNowPlaying -> {
                                        playerViewModel.onHandleEvent(PlayerEvent.Pause)
                                    }

                                    is HomeEvent.OnResumeMediaItem -> {
                                        navController.navigate(NavRoutes.Player(""))
                                        playerViewModel.resumeMediaItem(homeEvent.mediaItem)
                                        homeViewModel.onEmitEvent(homeEvent)
                                    }

                                    else -> homeViewModel.onEmitEvent(homeEvent)
                                }
                            }

                            if (isTvMode) {
                                val mediaItem by playerViewModel.mediaItemState.collectAsState()
                                LifecycleResumeEffect(Unit) {
                                    onHomeEvent(HomeEvent.RefreshEpgIfNeed)
                                    onPauseOrDispose { }
                                }
                                TvHomeScreen(
                                    homeUiState = homeUIState,
                                    totalPlaylist = totalPlaylist,
                                    playingChannelId = mediaItem.id.ifEmpty { null },
                                    onHomeEvent = onHomeEvent,
                                    onImportPlaylist = { navController.navigate(NavRoutes.ImportIptv) },
                                    onOpenLanguageSettings = {
                                        navController.navigate(NavRoutes.LanguageSettings)
                                    },
                                    signedInEmail = authState.user?.email
                                        .takeIf { authState.isAuthenticated },
                                    onLogin = { navController.navigate(NavRoutes.Login) },
                                    onLogout = { authViewModel.onEvent(LoginEvents.OnLogoutPressed) },
                                )
                            } else {
                                HomeBottomNavigationScreen(
                                    hazeState = hazeState,
                                    parentNavController = navController,
                                    totalPlaylist = totalPlaylist,
                                    homeUiState = homeUIState,
                                    playerUIState = playerUIState,
                                    onHomeEvent = onHomeEvent,
                                )
                            }
                        }

                        composable<NavRoutes.Player> {
                            val homeViewModel = koinViewModel<HomeViewModel>(
                                viewModelStoreOwner = appViewModelStore
                            )
                            val playerViewModel = koinViewModel<PlayerViewModel>(
                                viewModelStoreOwner = appViewModelStore
                            )

                            val channelId = it.toRoute<NavRoutes.Player>().mediaItemId

                            val mediaItem by playerViewModel.mediaItemState.collectAsStateWithLifecycle()
                            val homeUIState by homeViewModel.uiState.collectAsStateWithLifecycle()
                            val playerUIState by playerViewModel.playerUIState.collectAsStateWithLifecycle()
                            LaunchedEffect(channelId) {
                                playerViewModel.verifyPlayingMediaItem(channelId)
                            }

                            val onPlayerEvent: (PlayerEvent) -> Unit = { event ->
                                if (event is PlayerEvent.PlayIptv) {
                                    homeViewModel.getRelatedChannels(event.iptvChannel)
                                    homeViewModel.loadProgramForChannel(event.iptvChannel)
                                }

                                if (event is PlayerEvent.PlayIptv ||
                                    event is PlayerEvent.PlayMedia ||
                                    event == PlayerEvent.Play
                                ) {
                                    homeViewModel.onEmitEvent(HomeEvent.LoadHistory)
                                }


                                when (event) {
                                    PlayerEvent.OnVerticalPlayerBack -> navController.popBackStack()
                                    else -> playerViewModel.onHandleEvent(event)
                                }
                            }

                            if (isTvMode) {
                                TvPlayerScreen(
                                    mediaItem = mediaItem,
                                    homeUIState = homeUIState,
                                    mediaPlayer = playerViewModel.player,
                                    onEvent = onPlayerEvent,
                                )
                            } else {
                                PlayerScreen(
                                    mediaItem = mediaItem,
                                    homeUIState = homeUIState,
                                    mediaPlayer = playerViewModel.player,
                                    playerControlState = playerUIState,
                                    onEvent = onPlayerEvent,
                                )
                            }
                        }

                        composable<NavRoutes.ImportIptv> {
                            val homeViewModel = koinViewModel<HomeViewModel>(
                                viewModelStoreOwner = appViewModelStore
                            )
                            val homeUIState by homeViewModel.uiState.collectAsStateWithLifecycle()
                            val coroutineScope = rememberCoroutineScope()
                            var showPopupSuccess by remember { mutableStateOf(false) }

                            if (showPopupSuccess) {
                                TSDialog(
                                    onDismissRequest = {
                                        showPopupSuccess = false
                                        navController.popBackStack()
                                    },
                                    title = stringResource(Res.string.iptv_import_success_title),
                                    message = stringResource(
                                        Res.string.iptv_import_success_msg,
                                        homeUIState.listChannels.size
                                    ),
                                    positiveButtonText = stringResource(Res.string.ok),
                                    onPositiveClick = {
                                        showPopupSuccess = false
                                        navController.popBackStack()
                                    }
                                )
                            }

                            LifecycleResumeEffect(Unit) {
                                val job = coroutineScope.launch {
                                    homeViewModel.homeUIEvent.collect {
                                        when (it) {
                                            HomeEvent.OnParseIPTVSourceSuccess -> {
                                                showPopupSuccess = true
                                            }

                                            else -> {}
                                        }
                                    }
                                }
                                onPauseOrDispose {
                                    job.cancel()
                                }
                            }

                            ImportIPTVScreen(
                                hazeState = rememberHazeState(),
                                homeUiState = homeUIState,
                                onEvent = {
                                    when (it) {
                                        HomeEvent.OnBackPressed -> {
                                            navController.popBackStack()
                                        }

                                        is HomeEvent.OnParseIPTVSource -> {
                                            homeViewModel.parseIptvSource(it.name, it.url)
                                        }

                                        else -> {
                                            homeViewModel.onEmitEvent(it)
                                        }
                                    }
                                },
                            )
                        }

                        composable<NavRoutes.LanguageSettings>() {
                            LanguageSettingsScreen(
                                onBackPressed = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable<NavRoutes.WebView> { backStackEntry ->
                            val webView = backStackEntry.toRoute<NavRoutes.WebView>()
                            WebViewInApp(
                                pageUrl = webView.url,
                                navController = navController
                            )
                        }

                        composable<NavRoutes.ProgramDetail>(
                            typeMap = mapOf(
                                typeOf<ChannelWithProgramCount>() to ChannelWithProgramCountNavType
                            )
                        ) { backStack ->
                            val item = backStack.toRoute<NavRoutes.ProgramDetail>()
                            ProgramForChannelScreen(item.program)
                        }
                    }
                )
            }
        }
    }
}
