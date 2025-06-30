package tss.t.tsiptv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.core.database.createDatabaseFactory
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.navigation.NavHost
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.navigation.rememberNavController
import tss.t.tsiptv.ui.AddIPTVScreen
import tss.t.tsiptv.ui.HomeScreen
import tss.t.tsiptv.ui.screens.login.LoginScreen
import tss.t.tsiptv.ui.PlayerScreen
import tss.t.tsiptv.ui.screens.login.LoginScreenDesktop
import tss.t.tsiptv.ui.themes.StreamVaultTheme
import tss.t.tsiptv.utils.PlatformUtils

@Composable
@Preview
fun App() {
    val networkClient = remember { NetworkClientFactory.create().getNetworkClient() }
    val database = remember { createDatabaseFactory(networkClient).createDatabase() }

    var selectedChannelId by remember { mutableStateOf("") }
    var selectedChannelName by remember { mutableStateOf("") }
    var selectedChannelUrl by remember { mutableStateOf("") }

    val navController = rememberNavController()

    StreamVaultTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = NavRoutes.LOGIN,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(NavRoutes.LOGIN) {
                    val isDesktop = remember(PlatformUtils.platform) {
                        PlatformUtils.platform.isDesktop
                    }
                    if (isDesktop) {
                        LoginScreenDesktop()
                    } else {
                        LoginScreen()
                    }
                }
                composable(NavRoutes.HOME) {
                    HomeScreen(
                        onNavigateToAddIPTV = {
                            navController.navigate(NavRoutes.ADD_IPTV)
                        },
                        onChannelClick = { channel ->
                            // Store the selected channel information
                            selectedChannelId = channel.id.toString()
                            selectedChannelName = channel.name
                            selectedChannelUrl = channel.url
                            // Navigate to the player screen
                            navController.navigate(NavRoutes.PLAYER)
                        },
                        database = database
                    )
                }
                composable(NavRoutes.ADD_IPTV) {
                    AddIPTVScreen(
                        database = database,
                        networkClient = networkClient,
                        onSuccess = {
                            navController.navigateBack()
                        },
                        onCancel = {
                            navController.navigateBack()
                        }
                    )
                }
                composable(NavRoutes.PLAYER) {
                    PlayerScreen(
                        channelId = selectedChannelId,
                        channelName = selectedChannelName,
                        channelUrl = selectedChannelUrl,
                        onBack = {
                            navController.navigateBack()
                        }
                    )
                }
            }
        }
    }
}
