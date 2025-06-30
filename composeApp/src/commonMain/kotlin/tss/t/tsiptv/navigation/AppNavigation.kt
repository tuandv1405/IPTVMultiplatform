package tss.t.tsiptv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A simple navigation controller for multiplatform apps.
 */
class NavController {
    var currentRoute by mutableStateOf<String?>(null)
        private set

    private val backStack = mutableListOf<String>()

    fun navigate(route: String) {
        currentRoute?.let { backStack.add(it) }
        currentRoute = route
    }

    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            currentRoute = backStack.removeAt(backStack.size - 1)
        }
    }
}

/**
 * Composable function to remember a NavController.
 */
@Composable
fun rememberNavController(): NavController {
    return remember { NavController() }
}

/**
 * A simple NavHost for multiplatform apps.
 */
@Composable
fun NavHost(
    navController: NavController,
    startDestination: String,
    modifier: Modifier = Modifier,
    content: @Composable NavGraphBuilder.() -> Unit
) {
    val currentRoute = navController.currentRoute ?: startDestination

    // Initialize the controller with the start destination if not set
    if (navController.currentRoute == null) {
        navController.navigate(startDestination)
    }

    val navGraphBuilder = remember { NavGraphBuilder() }
    navGraphBuilder.content()

    val currentComposable = navGraphBuilder.destinations[currentRoute]
    currentComposable?.invoke()
}

/**
 * Builder for creating navigation destinations.
 */
class NavGraphBuilder {
    val destinations = mutableMapOf<String, @Composable () -> Unit>()

    fun composable(
        route: String,
        content: @Composable () -> Unit
    ) {
        destinations[route] = content
    }
}

/**
 * Extension function to add a composable destination to the NavGraphBuilder.
 */
fun NavGraphBuilder.composable(
    route: String,
    content: @Composable () -> Unit
) {
    destinations[route] = content
}
