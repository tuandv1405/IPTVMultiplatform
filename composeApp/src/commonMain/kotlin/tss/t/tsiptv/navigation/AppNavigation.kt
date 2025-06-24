package tss.t.tsiptv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Sealed class representing the different screens in the app.
 */
sealed class Screen {
    object Home : Screen()
    object AddIPTV : Screen()
    // Add more screens as needed
}

/**
 * A simple navigation controller for multiplatform apps.
 */
class NavigationController {
    var currentScreen by mutableStateOf<Screen>(Screen.Home)
        private set

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun navigateBack() {
        // Simple implementation - always go back to Home
        currentScreen = Screen.Home
    }
}

/**
 * Composable function to remember a NavigationController.
 */
@Composable
fun rememberNavigationController(): NavigationController {
    return remember { NavigationController() }
}
