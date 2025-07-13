package tss.t.tsiptv.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extension function for NavController to navigate to a destination and remove the current route from backstack.
 * This is useful when you want to navigate to a new screen and don't want the user to be able to go back to the current screen.
 *
 * @param route The route to navigate to
 * @param builder Optional lambda to configure additional NavOptions
 */
fun NavController.navigateAndRemoveFromBackStack(
    route: NavRoutes.RootRoutes,
    builder: (NavOptionsBuilder.() -> Unit)? = null
) {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (currentRoute != null) {
        val options = navOptions {
            popUpTo(currentRoute) { inclusive = true }
            builder?.invoke(this)
        }
        navigate(route, options)
    } else {
        navigate(route)
    }
}

/**
 * A composable that detects swipe gestures from left to right and triggers back navigation
 * with animation and alpha effects.
 */
@Composable
fun SwipeToGoBack(
    navController: NavController,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    previousContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Track swipe offset
    var offsetX by remember { mutableStateOf(0f) }
    // Maximum offset for full swipe (screen width approximation)
    val maxOffset = remember {
        1000f
    }

    // Calculate progress based on offset (0f to 1f)
    val progress = (offsetX / maxOffset).coerceIn(0f, 1f)

    // Animate alpha values
    val currentScreenAlpha by animateFloatAsState(
        targetValue = 1f - (progress * 0.5f), // Current screen fades slightly
        animationSpec = tween(durationMillis = 0),
        label = "currentAlpha"
    )

    val previousScreenAlpha by animateFloatAsState(
        targetValue = progress, // Previous screen becomes visible as we swipe
        animationSpec = tween(durationMillis = 0),
        label = "previousAlpha"
    )

    // Animate offset for current screen
    val currentScreenOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = 0),
        label = "currentOffset"
    )

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val canGoBack = currentBackStackEntry != null && navController.previousBackStackEntry != null
    val swipeModifier = if (enabled && canGoBack) {
        modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {

                },
                onDragEnd = {
                    // If swiped more than halfway, navigate back
                    if (offsetX > maxOffset / 3) {
                        navController.popBackStack()
                    }
                    // Reset offset
                    offsetX = 0f
                },
                onDragCancel = {
                    // Reset offset
                    offsetX = 0f
                },
                onHorizontalDrag = { _, dragAmount ->
                    // Only respond to right swipes (positive dragAmount)
                    if (dragAmount > 0) {
                        // Update offset based on drag amount
                        offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffset)
                    } else if (dragAmount < 0) {
                        // Allow dragging back (negative dragAmount)
                        offsetX = max(0f, offsetX + dragAmount)
                    }
                }
            )
        }
    } else {
        modifier
    }

    Box(modifier = swipeModifier) {
        // Show previous screen if available and we're swiping
        if (previousContent != null && progress > 0) {
            Box(
                modifier = Modifier
                    .alpha(previousScreenAlpha.coerceAtMost(0.8f))
            ) {
                previousContent()
            }
        }

        // Show current screen with offset and alpha
        Box(
            modifier = Modifier
                .offset { IntOffset(currentScreenOffset.roundToInt(), 0) }
                .alpha(currentScreenAlpha)
        ) {
            content()
        }
    }
}
