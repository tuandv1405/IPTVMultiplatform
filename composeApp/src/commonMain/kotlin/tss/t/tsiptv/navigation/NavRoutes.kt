package tss.t.tsiptv.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    const val SPLASH = "splash"

    const val LOGIN = "login"

    const val HOME = "home"
    const val ADD_IPTV = "add_iptv"
    const val PLAYER = "player"
    const val IMPORT_IPTV = "import_iptv"
    const val SIGN_UP = "sign_up"
    const val LANGUAGE_SETTINGS = "language_settings"

    // Home screen bottom navigation routes
    object HomeScreens {
        const val HOME_FEED = "home_feed"
        const val SETTINGS = "settings"
        const val FAVORITES = "favorites"
        const val PROFILE = "profile"
    }
    // Add more routes as needed
}
