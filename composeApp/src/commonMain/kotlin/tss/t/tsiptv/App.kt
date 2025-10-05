package tss.t.tsiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.error_occurred
import tsiptv.composeapp.generated.resources.iptv_import_success_msg
import tsiptv.composeapp.generated.resources.iptv_import_success_title
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.try_again
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.core.language.AppLocaleProvider
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.navigation.navigateAndRemoveFromBackStack
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
import tss.t.tsiptv.ui.widgets.TSDialog
import tss.t.tsiptv.utils.LocalAppViewModelStoreOwner
import tss.t.tsiptv.utils.PlatformUtils
import kotlin.reflect.typeOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun App() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.uiState.collectAsState()
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
        val appViewModelStore = LocalAppViewModelStoreOwner.current!!

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
                        val homeViewModel = koinViewModel<HomeViewModel>(
                            viewModelStoreOwner = appViewModelStore
                        )
                        val playerViewModel = koinViewModel<PlayerViewModel>(
                            viewModelStoreOwner = appViewModelStore
                        )

                        val homeUIState by homeViewModel.uiState.collectAsState()
                        val totalPlaylist by homeViewModel.totalChannelList.collectAsState()
                        val playerUIState by playerViewModel.playerUIState.collectAsState()

                        HomeBottomNavigationScreen(
                            hazeState = hazeState,
                            parentNavController = navController,
                            totalPlaylist = totalPlaylist,
                            homeUiState = homeUIState,
                            playerUIState = playerUIState
                        ) { homeEvent ->
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
