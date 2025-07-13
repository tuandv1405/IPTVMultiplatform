package tss.t.tsiptv.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    @Serializable
    sealed interface RootRoutes

    @Serializable
    data object Splash : RootRoutes

    @Serializable
    data object Login : RootRoutes

    @Serializable
    data class Home(
        val childNodes: String = HomeScreens.HOME_FEED,
    ) : RootRoutes

    @Serializable
    data class AddIptv(
        val defaultValue: String? = null,
    ) : RootRoutes

    @Serializable
    data class Player(
        val mediaItemId: String? = null,
    ) : RootRoutes

    @Serializable
    data object ImportIptv : RootRoutes
    @Serializable
    data object SignUp : RootRoutes

    @Serializable
    data class LanguageSettings(val defaultValue: String? = null) : RootRoutes

    @Serializable
    data class WebView(
        val url: String,
    ) : RootRoutes

    object HomeScreens {
        const val HOME_FEED = "home_feed"
        const val SETTINGS = "settings"
        const val FAVORITES = "favorites"
        const val PROFILE = "profile"
    }
}
