package tss.t.tsiptv.ui.screens.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import tsiptv.composeapp.generated.resources.Res
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.widgets.AppLogoCircle

@Composable
fun SplashScreen(authState: AuthUiState, navController: NavHostController) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = Res.getUri("drawable/background.png"),
            contentDescription = "Splash Screen Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        AppLogoCircle(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
