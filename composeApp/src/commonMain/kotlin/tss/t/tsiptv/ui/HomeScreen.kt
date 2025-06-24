package tss.t.tsiptv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import tss.t.tsiptv.Greeting

import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.compose_multiplatform

/**
 * Home screen of the application.
 */
@Composable
fun HomeScreen(
    onNavigateToAddIPTV: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Button to navigate to Add IPTV screen
        Button(onClick = onNavigateToAddIPTV) {
            Text("Add IPTV Playlist")
        }

        // Original demo content
        Button(onClick = { showContent = !showContent }) {
            Text("Click me!")
        }
        AnimatedVisibility(showContent) {
            val greeting = remember { Greeting().greet() }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(Res.drawable.compose_multiplatform), null)
                Text("Compose: $greeting")
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen(onNavigateToAddIPTV = {})
}
