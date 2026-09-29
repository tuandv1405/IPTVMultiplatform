package tss.t.tsiptv.core.stremio

import tss.t.tsiptv.navigation.NavRoutes
import tss.t.tsiptv.ui.screens.discover.CatalogViewModel
import tss.t.tsiptv.ui.screens.discover.DeepLinkRouter
import tss.t.tsiptv.ui.screens.player.isAddonItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinkRouterTest {
    private val base = "https://tsiptv-8bdd6.web.app/examples/stremio-sampler"

    private fun installed(enabled: Boolean = true) = InstalledAddon(
        stored = StoredAddon(
            addonId = StremioFixtures.sampler.id, transportUrlEnc = "x", transportHost = "h", manifestJson = "{}",
            name = "Sampler", version = "1", logoUrl = null, enabled = enabled, sortOrder = 0, addedAt = 0, lastFetchedAt = 0,
        ),
        manifest = StremioFixtures.sampler,
        transport = StremioFixtures.transport("$base/manifest.json"),
    )

    @Test
    fun discoverLinksOpenOnlyInstalledEnabledAddons() {
        val link = "stremio:///discover/" + StremioUrlEncoding.encodeComponent("$base/manifest.json") + "/movie/tspd-movie?genre=Comedy"
        assertEquals(
            NavRoutes.AddonCatalog(StremioFixtures.sampler.id, "movie", "tspd-movie", """{"genre":["Comedy"]}"""),
            DeepLinkRouter.route(link, listOf(installed())),
        )
        assertNull(DeepLinkRouter.route(link, listOf(installed(enabled = false))))
        assertNull(DeepLinkRouter.route(link, emptyList()))
        val otherHost = "stremio:///discover/" + StremioUrlEncoding.encodeComponent("https://not-installed.example.org/manifest.json") + "/movie/top"
        assertNull(DeepLinkRouter.route(otherHost, listOf(installed())))
        val unknownCatalog = "stremio:///discover/" + StremioUrlEncoding.encodeComponent("$base/manifest.json") + "/movie/nope"
        assertNull(DeepLinkRouter.route(unknownCatalog, listOf(installed())))
    }

    @Test
    fun detailAndSearchLinks() {
        assertEquals(NavRoutes.DiscoverSearch("his"), DeepLinkRouter.route("stremio:///search?search=his", emptyList()))
        assertEquals(
            NavRoutes.MediaDetail("series", "tt1", addonId = "a", videoId = "tt1:1:1", openStreams = true),
            DeepLinkRouter.route("stremio:///detail/series/tt1/tt1%3A1%3A1?autoPlay=true", emptyList(), currentAddonId = "a"),
        )
        assertNull(DeepLinkRouter.route("https://example.org/", emptyList()))
        assertNull(DeepLinkRouter.route("stremio:///board", emptyList()))
    }

    @Test
    fun catalogExtraJsonDropsSkipAndGroupsRepeatedKeys() {
        assertEquals("", CatalogViewModel.extraJson(emptyList()))
        assertEquals(
            """{"genre":["Action","Comedy"]}""",
            CatalogViewModel.extraJson(listOf("genre" to "Action", "genre" to "Comedy", "skip" to "100")),
        )
    }

    @Test
    fun addonMediaIds() {
        assertTrue(isAddonItemId("stremio:org.x:tt1"))
        assertFalse(isAddonItemId("channel-1"))
        assertFalse(isAddonItemId(null))
    }
}
