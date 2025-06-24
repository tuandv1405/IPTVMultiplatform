package tss.t.tsiptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.core.database.createDatabaseFactory
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.navigation.Screen
import tss.t.tsiptv.navigation.rememberNavigationController
import tss.t.tsiptv.ui.AddIPTVScreen
import tss.t.tsiptv.ui.HomeScreen

import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    // Create database and network client instances
    val networkClient = remember { NetworkClientFactory.create().getNetworkClient() }
    val database = remember { createDatabaseFactory(networkClient).createDatabase() }

    // Create a navigation controller
    val navigationController = rememberNavigationController()

    MaterialTheme {
        // Use the current screen from the navigation controller to determine what to display
        when (val currentScreen = navigationController.currentScreen) {
            is Screen.Home -> {
                HomeScreen(
                    onNavigateToAddIPTV = {
                        navigationController.navigateTo(Screen.AddIPTV)
                    }
                )
            }
            is Screen.AddIPTV -> {
                AddIPTVScreen(
                    database = database,
                    networkClient = networkClient,
                    onSuccess = {
                        // Navigate back to home screen on success
                        navigationController.navigateBack()
                        // You could add a success message here
                    },
                    onCancel = {
                        // Navigate back to home screen on cancel
                        navigationController.navigateBack()
                    }
                )
            }
        }
    }
}
