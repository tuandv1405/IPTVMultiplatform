package tss.t.tsiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.error_occurred
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.language.AppLocaleProvider
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.core.language.LocaleManager
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.navigation.navigateAndRemoveFromBackStack
import tss.t.tsiptv.ui.screens.iptv.AddIPTVScreen
import tss.t.tsiptv.ui.PlayerScreen
import tss.t.tsiptv.ui.screens.addiptv.ImportIPTVScreen
import tss.t.tsiptv.ui.screens.home.HomeScreen
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.login.LoginScreenDesktop2
import tss.t.tsiptv.ui.screens.login.LoginScreenPhone
import tss.t.tsiptv.ui.screens.login.SignUpScreen
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.settings.LanguageSettingsScreen
import tss.t.tsiptv.ui.screens.splash.SplashScreen
import tss.t.tsiptv.ui.themes.StreamVaultTheme
import tss.t.tsiptv.utils.PlatformUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun App() {
    val networkClient = koinInject<NetworkClient>()
    val database = koinInject<IPTVDatabase>()
    val localeManager = koinInject<LocaleManager>()
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

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            navController.navigateAndRemoveFromBackStack(NavRoutes.HOME) {
                launchSingleTop = true
            }
        }
    }

    // Wrap the app in the AppLocaleProvider to provide the current locale
    AppLocaleProvider(
        localeManager = localeManager,
        initialLanguage = LocalAppLocale.current
    ) {
        StreamVaultTheme {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.SPLASH,
                modifier = Modifier,
                builder = {
                    composable(NavRoutes.LOGIN) {
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

                        AnimatedVisibility(
                            visible = !authState.isAuthenticated &&
                                    !authState.error.isNullOrEmpty()
                        ) {
                            AlertDialog(
                                onDismissRequest = {
                                    authViewModel.onEvent(LoginEvents.OnDismissErrorDialog)
                                },
                                confirmButton = { },
                                title = {
                                    Text(stringResource(Res.string.error_occurred))
                                },
                                text = {
                                    Text(authState.error ?: "")
                                }
                            )
                        }

                        if (isDesktop) {
                            LoginScreenDesktop2()
                        } else {
                            LoginScreenPhone(authState) { event ->
                                if (event is LoginEvents.OnSignUpPressed) {
                                    navController.navigate(NavRoutes.SIGN_UP)
                                } else {
                                    authViewModel.onEvent(event)
                                }
                            }
                        }
                    }

                    composable(NavRoutes.SIGN_UP) {
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

                        AnimatedVisibility(
                            visible = !authState.isAuthenticated &&
                                    !authState.error.isNullOrEmpty()
                        ) {
                            AlertDialog(
                                onDismissRequest = {
                                    authViewModel.onEvent(LoginEvents.OnDismissErrorDialog)
                                },
                                confirmButton = { },
                                title = {
                                    Text(stringResource(Res.string.error_occurred))
                                },
                                text = {
                                    Text(authState.error ?: "")
                                }
                            )
                        }

                        LaunchedEffect(authState.isAuthenticated) {
                            if (authState.isAuthenticated) {
                                navController.navigateAndRemoveFromBackStack(NavRoutes.HOME)
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

                    composable(NavRoutes.SPLASH) {
                        SplashScreen()
                    }

                    composable(NavRoutes.HOME) {
                        val hazeState = rememberHazeState()
                        HomeScreen(hazeState = hazeState)
                    }

                    composable(NavRoutes.ADD_IPTV) {
                        AddIPTVScreen(
                            database = database,
                            networkClient = networkClient,
                            onSuccess = {
                                navController.popBackStack()
                            },
                            onCancel = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(NavRoutes.PLAYER) {
                        PlayerScreen(
                            channelId = "",
                            channelName = "",
                            channelUrl = "",
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(NavRoutes.IMPORT_IPTV) {
                        ImportIPTVScreen(hazeState = rememberHazeState())
                    }

                    composable(NavRoutes.LANGUAGE_SETTINGS) {
                        LanguageSettingsScreen(
                            onBackPressed = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            )
        }
    }
}
