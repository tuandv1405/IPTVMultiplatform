package tss.t.tsiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.error_occurred
import tsiptv.composeapp.generated.resources.iptv_import_success_msg
import tsiptv.composeapp.generated.resources.iptv_import_success_title
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.try_again
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.history.ChannelHistoryTracker
import tss.t.tsiptv.core.language.AppLocaleProvider
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.repository.IHistoryRepository
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.navigation.navigateAndRemoveFromBackStack
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.ui.screens.addiptv.ImportIPTVScreen
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeScreen
import tss.t.tsiptv.ui.screens.home.HomeViewModel
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.login.LoginScreenDesktop2
import tss.t.tsiptv.ui.screens.login.LoginScreenPhone
import tss.t.tsiptv.ui.screens.login.SignUpScreen
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.player.PlayerEvent
import tss.t.tsiptv.ui.screens.player.PlayerScreen
import tss.t.tsiptv.ui.screens.player.PlayerViewModel
import tss.t.tsiptv.ui.screens.settings.LanguageSettingsScreen
import tss.t.tsiptv.ui.screens.splash.SplashScreen
import tss.t.tsiptv.ui.screens.webview.WebViewInApp
import tss.t.tsiptv.ui.themes.StreamVaultTheme
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.utils.PlatformUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun App() {
    val networkClient = koinInject<NetworkClient>()
    val database = koinInject<IPTVDatabase>()
    val viewModelStoreOwner = LocalViewModelStoreOwner.current!!
    val navController = rememberNavController()
    val authRepository = koinInject<AuthRepository>()
    val authViewModel: AuthViewModel =
        viewModel(viewModelStoreOwner = viewModelStoreOwner) {
            AuthViewModel(
                authRepository = authRepository,
            )
        }
    val authState by authViewModel.uiState.collectAsState()
    val mediaPlayer = koinInject<MediaPlayer>()
    val historyTracker = koinInject<ChannelHistoryTracker>()
    val playerViewModel = viewModel<PlayerViewModel>(viewModelStoreOwner) {
        PlayerViewModel(
            _mediaPlayer = mediaPlayer,
            _iptvDatabase = database,
            historyTracker = historyTracker
        )
    }
    val playerUIState by playerViewModel.playerUIState.collectAsState()

    LaunchedEffect(
        key1 = authState.isAuthenticated,
        key2 = authState.isLoading
    ) {
        if (authState.isLoading) {
            return@LaunchedEffect
        }
        if (authState.isAuthenticated) {
            navController.navigateAndRemoveFromBackStack(NavRoutes.Home())
        } else if (!authState.isAuthenticated && authState.isNetworkAvailable) {
            navController.navigateAndRemoveFromBackStack(NavRoutes.Login)
        }
    }

    AppLocaleProvider {
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

                        if (isDesktop) {
                            LoginScreenDesktop2()
                        } else {
                            LoginScreenPhone(
                                navController,
                                authState
                            ) { event ->
                                if (event is LoginEvents.OnSignUpPressed) {
                                    navController.navigate(NavRoutes.SignUp)
                                } else {
                                    authViewModel.onEvent(event)
                                }
                            }
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
                                navController.navigateAndRemoveFromBackStack(NavRoutes.Home())
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
                        val historyRepository = koinInject<IHistoryRepository>()
                        val historyTracker = koinInject<ChannelHistoryTracker>()
                        val keyValueStorage = koinInject<KeyValueStorage>()
                        val homeViewModel = viewModel<HomeViewModel>(viewModelStoreOwner) {
                            HomeViewModel(
                                iptvDatabase = database,
                                networkClient = networkClient,
                                historyRepository = historyRepository,
                                historyTracker = historyTracker,
                                keyValueStorage = keyValueStorage
                            )
                        }
                        val homeUIState by homeViewModel.uiState.collectAsState()
                        val totalPlaylist by homeViewModel.totalChannelList.collectAsState()
                        HomeScreen(
                            hazeState = hazeState,
                            parentNavController = navController,
                            authState = authState,
                            homeUiState = homeUIState,
                            totalPlaylist = totalPlaylist,
                            playerUIState = playerUIState,
                            onHomeEvent = {
                                if (it is HomeEvent.OnOpenVideoPlayer) {
                                    homeViewModel.getRelatedChannels(it.channel)
                                    homeViewModel.loadProgramForChannel(it.channel)
                                    playerViewModel.playIptv(it.channel)
                                    navController.navigate(NavRoutes.Player(it.channel.id))
                                    homeViewModel.onEmitEvent(HomeEvent.LoadHistory)
                                }


                                when (it) {
                                    is HomeEvent.OnPlayNowPlaying -> {
                                        homeViewModel.onEmitEvent(it)
                                        if (playerViewModel.mediaItemState.value.id != it.channel.id) {
                                            playerViewModel.playIptv(it.channel)
                                        } else {
                                            playerViewModel.onHandleEvent(PlayerEvent.Play)
                                        }
                                    }

                                    is HomeEvent.OnPauseNowPlaying -> {
                                        playerViewModel.onHandleEvent(PlayerEvent.Pause)
                                    }

                                    is HomeEvent.OnResumeMediaItem -> {
                                        navController.navigate(NavRoutes.Player(""))
                                        playerViewModel.resumeMediaItem(it.mediaItem)
                                        homeViewModel.onEmitEvent(it)
                                    }

                                    else -> homeViewModel.onEmitEvent(it)
                                }
                            }
                        )
                    }

                    composable<NavRoutes.Player> {
                        val channelId = it.toRoute<NavRoutes.Player>().mediaItemId
                        val mediaItem by playerViewModel.mediaItemState.collectAsState()
                        val historyRepository = koinInject<IHistoryRepository>()
                        val historyTracker = koinInject<ChannelHistoryTracker>()
                        val keyValueStorage = koinInject<KeyValueStorage>()
                        val homeViewModel = viewModel<HomeViewModel>(viewModelStoreOwner) {
                            HomeViewModel(
                                iptvDatabase = database,
                                networkClient = networkClient,
                                historyRepository = historyRepository,
                                historyTracker = historyTracker,
                                keyValueStorage = keyValueStorage
                            )
                        }
                        val homeUIState by homeViewModel.uiState.collectAsStateWithLifecycle()
                        val playerUIState by playerViewModel.playerUIState.collectAsState()
                        LaunchedEffect(channelId) {
                            playerViewModel.verifyPlayingMediaItem(channelId)
                        }

                        PlayerScreen(
                            mediaItem = mediaItem,
                            homeUIState = homeUIState,
                            mediaPlayer = playerViewModel.player,
                            playerControlState = playerUIState,
                        ) { event ->
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
                    }

                    composable<NavRoutes.ImportIptv> {
                        val database = koinInject<IPTVDatabase>()
                        val networkClient = koinInject<NetworkClient>()
                        val historyRepository = koinInject<IHistoryRepository>()
                        val historyTracker = koinInject<ChannelHistoryTracker>()
                        val keyValueStorage = koinInject<KeyValueStorage>()
                        val homeViewModel = viewModel<HomeViewModel>(viewModelStoreOwner) {
                            HomeViewModel(
                                iptvDatabase = database,
                                networkClient = networkClient,
                                historyRepository = historyRepository,
                                historyTracker = historyTracker,
                                keyValueStorage = keyValueStorage
                            )
                        }
                        val homeUIState by homeViewModel.uiState.collectAsState()
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
                }
            )
        }
    }
}
