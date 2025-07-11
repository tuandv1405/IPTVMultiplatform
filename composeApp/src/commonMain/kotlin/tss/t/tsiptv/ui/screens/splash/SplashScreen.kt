package tss.t.tsiptv.ui.screens.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tsiptv.composeapp.generated.resources.Res
import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.navigation.navigateAndRemoveFromBackStack
import tss.t.tsiptv.ui.screens.login.AuthUiState
import tss.t.tsiptv.ui.widgets.AppLogoCircle

@Composable
fun SplashScreen(authState: AuthUiState, navController: NavHostController) {
    LaunchedEffect(authState.isAuthenticated) {
        delay(500L)
        if (authState.isAuthenticated) {
            navController.navigateAndRemoveFromBackStack(NavRoutes.HOME) {
                launchSingleTop = true
            }
        } else if (authState.isNetworkAvailable) {
            navController.navigateAndRemoveFromBackStack(NavRoutes.LOGIN) {
                launchSingleTop = true
            }
        }
    }

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
