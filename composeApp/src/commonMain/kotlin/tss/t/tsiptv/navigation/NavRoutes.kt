package tss.t.tsiptv.navigation

import kotlinx.serialization.Serializable
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount

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
    data object ChangeIPTV : RootRoutes

    @Serializable
    data object SignUp : RootRoutes

    @Serializable
    data object LanguageSettings : RootRoutes

    @Serializable
    data class ProgramDetail(
        val program: ChannelWithProgramCount,
    ) : RootRoutes

    @Serializable
    data class WebView(
        val url: String,
    ) : RootRoutes

    /** F2: installed addons (Profile → Addons on phone, Settings → Addons on TV). */
    @Serializable
    data object Addons : RootRoutes

    /**
     * F2: one addon catalogue ("See all" or a `stremio:///discover/…` link). [extraJson] is a
     * JSON object of initial selections (`{"genre":["Comedy"]}`), empty for none.
     */
    @Serializable
    data class AddonCatalog(
        val addonId: String,
        val type: String,
        val catalogId: String,
        val extraJson: String = "",
    ) : RootRoutes

    /**
     * F2: movie / series / tv detail. [previewJson] is the catalogue item (a `StremioMeta` as JSON)
     * used when no addon provides meta. [openStreams] opens the stream picker for [videoId]
     * (Continue watching), resuming from the saved position.
     */
    @Serializable
    data class MediaDetail(
        val type: String,
        val id: String,
        val addonId: String? = null,
        val videoId: String? = null,
        val openStreams: Boolean = false,
        val previewJson: String = "",
        /**
         * F3: set for a TS IPTV Source movie or series: [id] is its pool id in that source (playlist)
         * and the detail is built from the stored source, not from addons.
         */
        val sourcePlaylistId: String? = null,
        /** F3 hero `autoplay`: play the first playable stream as soon as the streams are known. */
        val autoplay: Boolean = false,
    ) : RootRoutes

    /** F3: "See all" of a Source Home row (the section's query as a grid without its limit). */
    @Serializable
    data class SourceSeeAll(
        val playlistId: String,
        val sectionKey: String,
        val title: String,
    ) : RootRoutes

    /** F3: "About this source". */
    @Serializable
    data class SourceAbout(val playlistId: String) : RootRoutes

    /** Profile › Notifications (docs/prd-push-notifications.md). */
    @Serializable
    data object NotificationSettings : RootRoutes

    /** "TV & thiết bị": receive / send to TV, device sync, signed-in devices (prd-tv-cast-and-sync). */
    @Serializable
    data object Connect : RootRoutes

    /** F2: addon search (a `stremio:///search` link, or TV search). */
    @Serializable
    data class DiscoverSearch(
        val query: String = "",
    ) : RootRoutes

    object HomeScreens {
        const val HOME_FEED = "home_feed"
        const val DISCOVER = "discover"
        const val SETTINGS = "settings"
        const val HISTORY = "favorites"
        const val PROFILE = "profile"
        const val PROGRAM = "program_screen"
    }
}
