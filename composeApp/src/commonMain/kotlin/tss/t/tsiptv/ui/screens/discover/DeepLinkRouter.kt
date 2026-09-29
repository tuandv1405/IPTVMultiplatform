package tss.t.tsiptv.ui.screens.discover

import tss.t.tsiptv.core.stremio.InstalledAddon
import tss.t.tsiptv.core.stremio.ResourceMatcher
import tss.t.tsiptv.core.stremio.StremioDeepLink
import tss.t.tsiptv.core.stremio.StremioTransport
import tss.t.tsiptv.navigation.NavRoutes

/**
 * `stremio:///…` links → app routes (PRD §6 "links"). `discover` links only open catalogues of an
 * **installed, enabled** addon (never a URL the user did not add); everything else → null.
 */
object DeepLinkRouter {
    fun route(link: StremioDeepLink, addons: List<InstalledAddon>, currentAddonId: String? = null): NavRoutes.RootRoutes? = when (link) {
        is StremioDeepLink.Search -> NavRoutes.DiscoverSearch(link.query)
        is StremioDeepLink.Detail -> NavRoutes.MediaDetail(
            type = link.type,
            id = link.id,
            addonId = currentAddonId,
            videoId = link.videoId,
            openStreams = link.videoId != null && link.autoPlay,
        )
        is StremioDeepLink.Discover -> {
            val target = StremioTransport.parse(link.transportUrl)?.manifestUrl
            val addon = addons.firstOrNull { it.isActive && it.transport?.manifestUrl == target }
            addon?.takeIf { ResourceMatcher.findCatalog(it.manifest, link.type, link.catalogId) != null }?.let {
                NavRoutes.AddonCatalog(it.id, link.type, link.catalogId, CatalogViewModel.extraJson(link.extra))
            }
        }
    }

    fun route(url: String?, addons: List<InstalledAddon>, currentAddonId: String? = null): NavRoutes.RootRoutes? =
        url?.let { StremioDeepLink.parse(it) }?.let { route(it, addons, currentAddonId) }
}
