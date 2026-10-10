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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.bottom_sheet_refresh_channel
import tsiptv.composeapp.generated.resources.error_occurred
import tsiptv.composeapp.generated.resources.ok
import tsiptv.composeapp.generated.resources.open_link_failed
import tsiptv.composeapp.generated.resources.playlist_file_refresh_hint
import tsiptv.composeapp.generated.resources.try_again
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import tss.t.tsiptv.core.language.AppLocaleProvider
import tss.t.tsiptv.core.rating.AppRatingController
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
import tss.t.tsiptv.ui.screens.addiptv.ImportSummaryDialog
import tss.t.tsiptv.ui.screens.home.HomeBottomNavigationScreen
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeNotice
import tss.t.tsiptv.ui.screens.home.HomeViewModel
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.login.LoginScreenDesktop2
import tss.t.tsiptv.ui.screens.login.LoginScreenPhone
import tss.t.tsiptv.ui.screens.login.SignUpScreen
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.player.PlayerEvent
import tss.t.tsiptv.ui.screens.player.PlayerOptionsDialog
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
import tss.t.tsiptv.core.stremio.AddonRepository
import tss.t.tsiptv.ui.screens.addons.AddonsScreen
import tss.t.tsiptv.ui.screens.discover.CatalogScreen
import tss.t.tsiptv.ui.screens.discover.DiscoverSearchScreen
import tss.t.tsiptv.ui.screens.discover.DiscoverViewModel
import tss.t.tsiptv.ui.screens.mediadetail.MediaDetailScreen
import tss.t.tsiptv.ui.screens.source.SourceAboutScreen
import tss.t.tsiptv.ui.screens.source.SourceAboutViewModel
import tss.t.tsiptv.ui.screens.source.SourceHomeActions
import tss.t.tsiptv.ui.screens.source.SourceHomeViewModel
import tss.t.tsiptv.ui.screens.source.SourceSeeAllContent
import tsiptv.composeapp.generated.resources.source_refresh_failed
import tss.t.tsiptv.core.model.Channel
import androidx.navigation.NavHostController
import tss.t.tsiptv.utils.PlatformUtils
import tss.t.tsiptv.utils.getScreenOrientationUtils
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.first
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.feature.lan.CastCommand
import tss.t.tsiptv.feature.lan.CastStream
import tss.t.tsiptv.feature.lan.LanSender
import tss.t.tsiptv.feature.lan.LanValidation
import tss.t.tsiptv.platform.PickedPlaylistFile
import tss.t.tsiptv.player.ui.LocalCastAction
import tss.t.tsiptv.ui.screens.connect.AccountHost
import tss.t.tsiptv.ui.screens.connect.ConnectScreen
import tss.t.tsiptv.ui.screens.connect.LanReceiverHost
import tss.t.tsiptv.ui.screens.connect.TvSendDialog

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
            // QA only (debug build with -Ptsiptv.debugSkipLogin=true): the phone layout signed
            // out, as the TV layout already works, to check screens without a real account.
            isTvMode || tss.t.tsiptv.utils.DebugFlags.skipLogin -> {
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

    // Rating prompt: only on the way back from the player to Home, so it never
    // interrupts playback. The controller decides whether it is due.
    val appRating: AppRatingController = koinInject()
    // F2: daily manifest refresh and blocklist check (never throws, no UI).
    val addonRepository: AddonRepository = koinInject()
    LaunchedEffect(Unit) { addonRepository.dailyMaintenance() }
    val appScope = rememberCoroutineScope()
    var showOpenLinkFailed by remember { mutableStateOf(false) }
    // A tapped notification's link (already checked against the allowlist), applied once the app is
    // past Splash / Login (docs/prd-push-notifications.md R3).
    val pushManager: tss.t.tsiptv.feature.push.PushManager = koinInject()
    LaunchedEffect(navController) {
        pushManager.links.collect { target ->
            navController.currentBackStackEntryFlow.first { entry ->
                val d = entry.destination
                !d.hasRoute<NavRoutes.Splash>() && !d.hasRoute<NavRoutes.Login>()
            }
            pushManager.consumeLink()
            // One bad target must never stop later links from being handled.
            try { when (target) {
                tss.t.tsiptv.feature.push.PushTarget.Home -> {
                    navController.popBackStack<NavRoutes.Home>(inclusive = false)
                    pushManager.requestHomeTab()
                }
                tss.t.tsiptv.feature.push.PushTarget.Addons -> navController.navigate(NavRoutes.Addons)
                tss.t.tsiptv.feature.push.PushTarget.NotificationSettings -> navController.navigate(NavRoutes.NotificationSettings)
                tss.t.tsiptv.feature.push.PushTarget.Store -> if (!appRating.openStoreListing()) showOpenLinkFailed = true
                is tss.t.tsiptv.feature.push.PushTarget.Web -> if (!tss.t.tsiptv.utils.getUrlOpener().openUrl(target.url)) showOpenLinkFailed = true
            } } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("TSPush: link not applied: ${e::class.simpleName}: ${e.message}")
            }
        }
    }
    LaunchedEffect(navController) {
        appRating.onAppStarted()
        var wasOnPlayer = false
        navController.currentBackStackEntryFlow.collect { entry ->
            val destination = entry.destination
            if (wasOnPlayer && destination.hasRoute<NavRoutes.Home>()) {
                appRating.maybePrompt()
            }
            wasOnPlayer = destination.hasRoute<NavRoutes.Player>()
        }
    }

    AppLocaleProvider {
        val appViewModelStore = LocalAppViewModelStoreOwner.current!!

        CompositionLocalProvider(
            LocalUiMode provides uiMode,
            LocalIsTvMode provides isTvMode,
            tss.t.tsiptv.ui.screens.connect.LocalSignInAction provides { navController.navigate(NavRoutes.Login) },
            // "Remove ads" link and "Get Unlimited" on quota screens (docs/prd-subscriptions.md §3.2).
            tss.t.tsiptv.ui.screens.plans.LocalOpenPlans provides { navController.navigate(NavRoutes.Plans) { launchSingleTop = true } },
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
                                // F3: a channel from Source Home; its section is the zap list.
                                if (homeEvent is HomeEvent.OnPlaySourceChannel) {
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

                            // Refresh feedback from Settings (phone sheet or TV dialog).
                            if (homeUIState.notice == HomeNotice.FILE_REFRESH_HINT) {
                                TSDialog(
                                    title = stringResource(Res.string.bottom_sheet_refresh_channel),
                                    message = stringResource(Res.string.playlist_file_refresh_hint),
                                    positiveButtonText = stringResource(Res.string.ok),
                                    onPositiveClick = { homeViewModel.onEmitEvent(HomeEvent.OnDismissNotice) },
                                    onDismissRequest = { homeViewModel.onEmitEvent(HomeEvent.OnDismissNotice) },
                                )
                            }
                            if (homeUIState.notice == HomeNotice.SOURCE_REFRESH_FAILED) {
                                TSDialog(
                                    title = stringResource(Res.string.bottom_sheet_refresh_channel),
                                    message = stringResource(Res.string.source_refresh_failed),
                                    positiveButtonText = stringResource(Res.string.ok),
                                    onPositiveClick = { homeViewModel.onEmitEvent(HomeEvent.OnDismissNotice) },
                                    onDismissRequest = { homeViewModel.onEmitEvent(HomeEvent.OnDismissNotice) },
                                )
                            }
                            // Device limit, "signed out elsewhere" and a new sync (prd-tv-cast-and-sync).
                            AccountHost(onOpenConnect = { navController.navigate(NavRoutes.Connect) })

                            homeUIState.importSummary
                                ?.takeIf { it.fromRefresh && it.skippedTotal > 0 }
                                ?.let { summary ->
                                    ImportSummaryDialog(
                                        summary = summary,
                                        onDismiss = { homeViewModel.onEmitEvent(HomeEvent.OnDismissImportSummary) },
                                    )
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
                                    onOpenAddons = { navController.navigate(NavRoutes.Addons) },
                                    onNavigate = { route -> navController.navigate(route) },
                                    onOpenSourceAbout = { id -> navController.navigate(NavRoutes.SourceAbout(id)) },
                                    onRateApp = if (appRating.canOpenStoreListing) {
                                        {
                                            appScope.launch {
                                                // Common on Android TV: no Play Store app and no browser.
                                                if (!appRating.openStoreListing()) showOpenLinkFailed = true
                                            }
                                        }
                                    } else null,
                                )
                                if (showOpenLinkFailed) {
                                    TSDialog(
                                        title = stringResource(Res.string.error_occurred),
                                        message = stringResource(Res.string.open_link_failed),
                                        positiveButtonText = stringResource(Res.string.ok),
                                        onPositiveClick = { showOpenLinkFailed = false },
                                        onDismissRequest = { showOpenLinkFailed = false },
                                    )
                                }
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
                            var showPlayerOptions by remember { mutableStateOf(false) }

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
                                    // F3: streams of a source channel and subtitle tracks (AC-T21).
                                    PlayerEvent.OnSettings -> showPlayerOptions = true
                                    else -> playerViewModel.onHandleEvent(event)
                                }
                            }
                            if (showPlayerOptions) {
                                val channelStreams by playerViewModel.channelStreams.collectAsStateWithLifecycle()
                                PlayerOptionsDialog(
                                    player = playerViewModel.player,
                                    streams = channelStreams,
                                    onPickStream = playerViewModel::playChannelStream,
                                    onDismiss = { showPlayerOptions = false },
                                )
                            }

                            if (isTvMode) {
                                TvPlayerScreen(
                                    mediaItem = mediaItem,
                                    homeUIState = homeUIState,
                                    mediaPlayer = playerViewModel.player,
                                    onEvent = onPlayerEvent,
                                    optionsVisible = showPlayerOptions,
                                    hasStreamChoice = playerViewModel.channelStreams.collectAsStateWithLifecycle().value != null,
                                )
                            } else {
                                // Cast to a TS IPTV TV on the same Wi-Fi (prd-tv-cast-and-sync §2).
                                var castCommand by remember { mutableStateOf<CastCommand?>(null) }
                                val lanSender: LanSender = koinInject()
                                CompositionLocalProvider(
                                    LocalCastAction provides if (lanSender.isSupported && mediaItem.uri.isNotEmpty()) {
                                        { castCommand = CastCommand(mediaItem.toCastStream(playerViewModel.player)) }
                                    } else null,
                                ) {
                                    PlayerScreen(
                                        mediaItem = mediaItem,
                                        homeUIState = homeUIState,
                                        mediaPlayer = playerViewModel.player,
                                        playerControlState = playerUIState,
                                        onEvent = onPlayerEvent,
                                    )
                                }
                                castCommand?.let { command ->
                                    TvSendDialog(command = command, onDismiss = { castCommand = null })
                                }
                            }
                        }

                        composable<NavRoutes.ImportIptv> {
                            val homeViewModel = koinViewModel<HomeViewModel>(
                                viewModelStoreOwner = appViewModelStore
                            )
                            val homeUIState by homeViewModel.uiState.collectAsStateWithLifecycle()
                            // The result comes from state, not a one-shot event, so it is never lost
                            // when the import finishes before this screen is resumed (QC r2 N1). Only
                            // a summary produced after the screen opened is shown here; refreshes and
                            // playlists imported from a phone on a TV have their own dialogs.
                            val summaryAtEntry = remember { homeUIState.importSummary }
                            val summary = homeUIState.importSummary
                            if (summary != null && summary !== summaryAtEntry && !summary.fromRefresh && !summary.fromLan) {
                                ImportSummaryDialog(
                                    summary = summary,
                                    onDismiss = {
                                        homeViewModel.onEmitEvent(HomeEvent.OnDismissImportSummary)
                                        navController.popBackStack()
                                    },
                                )
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

                        composable<NavRoutes.NotificationSettings> {
                            tss.t.tsiptv.ui.screens.settings.NotificationSettingsScreen(onBack = { navController.popBackStack() })
                        }

                        // F2: Stremio-compatible addons.
                        composable<NavRoutes.Addons> {
                            AddonsScreen(onBack = { navController.popBackStack() })
                        }

                        composable<NavRoutes.AddonCatalog> { entry ->
                            CatalogScreen(
                                route = entry.toRoute<NavRoutes.AddonCatalog>(),
                                onNavigate = { navController.navigate(it) },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<NavRoutes.MediaDetail> { entry ->
                            MediaDetailScreen(
                                route = entry.toRoute<NavRoutes.MediaDetail>(),
                                onNavigate = { navController.navigate(it) },
                                onPlay = { id -> navController.navigate(NavRoutes.Player(id)) },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        // F3: TS IPTV Sources.
                        composable<NavRoutes.SourceSeeAll> { entry ->
                            val homeViewModel = koinViewModel<HomeViewModel>(viewModelStoreOwner = appViewModelStore)
                            val playerViewModel = koinViewModel<PlayerViewModel>(viewModelStoreOwner = appViewModelStore)
                            val sourceViewModel = koinViewModel<SourceHomeViewModel>()
                            SourceSeeAllContent(
                                route = entry.toRoute<NavRoutes.SourceSeeAll>(),
                                viewModel = sourceViewModel,
                                actions = SourceHomeActions(
                                    onPlayChannel = { channel, zap -> playSourceChannel(homeViewModel, playerViewModel, navController, channel, zap) },
                                    onNavigate = { navController.navigate(it) },
                                    onOpenAbout = {},
                                    onRefresh = {},
                                ),
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<NavRoutes.SourceAbout> { entry ->
                            val homeViewModel = koinViewModel<HomeViewModel>(viewModelStoreOwner = appViewModelStore)
                            val route = entry.toRoute<NavRoutes.SourceAbout>()
                            SourceAboutScreen(
                                playlistId = route.playlistId,
                                viewModel = koinViewModel<SourceAboutViewModel>(),
                                onConfirmAdult = { homeViewModel.onEmitEvent(HomeEvent.OnConfirmSourceAdult(route.playlistId)) },
                                onRemove = {
                                    homeViewModel.onEmitEvent(HomeEvent.OnRemoveSource(route.playlistId))
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<NavRoutes.Connect> {
                            val homeViewModel = koinViewModel<HomeViewModel>(viewModelStoreOwner = appViewModelStore)
                            val database: IPTVDatabase = koinInject()
                            ConnectScreen(
                                isTvLayout = isTvMode,
                                onBack = { navController.popBackStack() },
                                onSyncApplied = {
                                    // Replace may have removed the playlist on screen: pick another one.
                                    appScope.launch {
                                        val all = database.getAllPlaylists().first()
                                        val current = homeViewModel.uiState.value.playListId
                                        if (current == null || all.none { p -> p.id == current }) {
                                            all.firstOrNull()?.let { homeViewModel.onEmitEvent(HomeEvent.OnRequestChangePlaylist(it)) }
                                        }
                                    }
                                },
                            )
                        }

                        composable<NavRoutes.Plans> {
                            tss.t.tsiptv.ui.screens.plans.PlansScreen(
                                isTvLayout = isTvMode,
                                onBack = { navController.popBackStack() },
                                onSignIn = { navController.navigate(NavRoutes.Login) },
                            )
                        }

                        composable<NavRoutes.DiscoverSearch> { entry ->
                            val discoverViewModel = koinViewModel<DiscoverViewModel>(viewModelStoreOwner = appViewModelStore)
                            DiscoverSearchScreen(
                                viewModel = discoverViewModel,
                                initialQuery = entry.toRoute<NavRoutes.DiscoverSearch>().query,
                                onNavigate = { navController.navigate(it) },
                                onBack = { navController.popBackStack() },
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

                // TV: receive casts and playlists from paired phones while the app is open.
                if (isTvMode) {
                    val homeViewModel = koinViewModel<HomeViewModel>(viewModelStoreOwner = appViewModelStore)
                    val playerViewModel = koinViewModel<PlayerViewModel>(viewModelStoreOwner = appViewModelStore)
                    // While the import screen is open (a playlist being added), a new offer is
                    // refused and the phone says "TV is busy".
                    val currentEntry by navController.currentBackStackEntryAsState()
                    val onImportScreen = currentEntry?.destination?.hasRoute<NavRoutes.ImportIptv>() == true
                    val lanState by homeViewModel.uiState.collectAsStateWithLifecycle()
                    val lanImporting = lanState.isLoading && lanState.importFromLan
                    // A playlist from a phone is imported in the background (QC r2 N1): the result
                    // shows here over any screen. Only an import that needs the user (a single
                    // stream, a TS IPTV Source preview, an error) opens the import screen.
                    lanState.importSummary?.takeIf { it.fromLan }?.let { summary ->
                        ImportSummaryDialog(
                            summary = summary,
                            onDismiss = { homeViewModel.onEmitEvent(HomeEvent.OnDismissImportSummary) },
                        )
                    }
                    val needsUser = lanState.importFromLan &&
                        (lanState.pendingSingleStream != null || lanState.sourceImport != null || lanState.error != null)
                    // The import screen this flow opened is closed again once nothing waits for the
                    // user there any more (QC r3 N6), so later offers are not refused as "busy".
                    var lanOpenedImport by remember { mutableStateOf(false) }
                    LaunchedEffect(needsUser, lanState.isLoading, onImportScreen) {
                        when {
                            needsUser && !lanState.isLoading && !onImportScreen -> {
                                lanOpenedImport = true
                                navController.navigate(NavRoutes.ImportIptv)
                            }
                            lanOpenedImport && !needsUser && !lanState.isLoading -> {
                                lanOpenedImport = false
                                if (onImportScreen) navController.popBackStack()
                            }
                        }
                    }
                    LanReceiverHost(
                        canTakePlaylists = !onImportScreen && !lanImporting,
                        // One phone import at a time: the next offer waits (QC r3 N5).
                        importRunning = lanImporting,
                        onCast = { stream ->
                            val id = playerViewModel.playCast(stream)
                            if (navController.currentBackStackEntry?.destination?.hasRoute<NavRoutes.Player>() != true) {
                                navController.navigate(NavRoutes.Player(id))
                            }
                        },
                        onAcceptPlaylist = { shared ->
                            // The normal importer, in the background: playback and navigation stay.
                            val content = shared.content
                            if (content != null) {
                                homeViewModel.importFile(
                                    shared.name,
                                    PickedPlaylistFile.Picked(shared.fileName ?: "playlist.m3u", content.encodeToByteArray()),
                                    confirmedReplace = true,
                                    fromLan = true,
                                )
                            } else {
                                homeViewModel.parseIptvSource(shared.name, shared.url.orEmpty(), fromLan = true)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** What a paired TV needs to play [this] where the phone is (prd-tv-cast-and-sync §2.2). */
private fun tss.t.tsiptv.player.models.MediaItem.toCastStream(player: tss.t.tsiptv.player.MediaPlayer): CastStream {
    val duration = player.duration.value
    val isLive = duration <= 0
    return CastStream(
        url = uri,
        title = title,
        logo = artworkUri?.takeIf { LanValidation.isHttpUrl(it) },
        mimeType = mimeType,
        headers = headers,
        drm = drm,
        subtitles = subtitles.filter { LanValidation.isHttpUrl(it.url) },
        isLive = isLive,
        positionMs = if (isLive) null else player.currentPosition.value,
    )
}

/** F3: plays a Source Home channel through the Home flow ([HomeEvent.OnPlaySourceChannel]). */
private fun playSourceChannel(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    channel: Channel,
    zap: List<Channel>,
) {
    homeViewModel.onEmitEvent(HomeEvent.OnPlaySourceChannel(channel, zap))
    homeViewModel.loadProgramForChannel(channel)
    playerViewModel.playIptv(channel)
    navController.navigate(NavRoutes.Player(channel.id))
    homeViewModel.onEmitEvent(HomeEvent.LoadHistory)
}
