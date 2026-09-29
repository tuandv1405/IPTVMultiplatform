package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.TestAssets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddonBlocklistTest {
    private val list = AddonBlocklist(
        ids = listOf("org.example.blocked"),
        hosts = listOf("Bad.Example.org", "ports.example.org:8080", " "),
    )

    @Test
    fun matchesIdsAndHostsIncludingSubdomains() {
        assertTrue(list.isBlocked("org.example.blocked", StremioFixtures.transport("https://fine.example.org/manifest.json")))
        assertTrue(list.isBlocked("x", StremioFixtures.transport("https://bad.example.org/cfg/manifest.json")))
        assertTrue(list.isBlocked("x", StremioFixtures.transport("https://cdn.bad.example.org/manifest.json")))
        assertFalse(list.isBlocked("x", StremioFixtures.transport("https://notbad.example.org/manifest.json")))
        assertTrue(list.isBlocked("x", StremioFixtures.transport("http://ports.example.org:8080/manifest.json")))
        assertFalse(list.isBlocked("x", StremioFixtures.transport("https://ports.example.org/manifest.json")))
        assertFalse(AddonBlocklist.EMPTY.isBlocked("org.example.blocked", StremioFixtures.transport("https://bad.example.org/manifest.json")))
    }

    @Test
    fun entriesAreNormalised() {
        assertEquals("evil.example" to 443, AddonBlocklist.normaliseEntry(" https://Evil.Example:443/path?q#f "))
        assertEquals("evil.example" to null, AddonBlocklist.normaliseEntry("*.evil.example."))
        assertEquals("evil.example" to null, AddonBlocklist.normaliseEntry("http://user:pw@evil.example/"))
        assertEquals("2001:db8::1" to 8080, AddonBlocklist.normaliseEntry("[2001:DB8::1]:8080"))
        assertEquals("2001:db8::1" to null, AddonBlocklist.normaliseEntry("2001:db8::1"))
        assertNull(AddonBlocklist.normaliseEntry("  "))
        assertNull(AddonBlocklist.normaliseEntry("host:notaport"))

        val list = AddonBlocklist(hosts = listOf("https://evil.example/whatever", "*.wild.example", "tls.example:443", "[::1]", "plain.example:80"))
        fun blocked(url: String) = list.isBlocked("x", StremioFixtures.transport(url))
        assertTrue(blocked("https://evil.example/cfg/manifest.json"))
        assertTrue(blocked("https://a.wild.example/manifest.json"))
        assertTrue(blocked("https://wild.example/manifest.json"))
        assertTrue(blocked("https://tls.example/manifest.json"))       // default port 443
        assertTrue(blocked("https://tls.example:443/manifest.json"))
        assertFalse(blocked("http://tls.example/manifest.json"))       // default port 80
        assertTrue(blocked("http://plain.example/manifest.json"))
        assertTrue(blocked("http://[::1]:7000/manifest.json"))
        assertFalse(AddonBlocklist(ids = listOf(" ")).isIdBlocked(""))
    }

    @Test
    fun fetchParsesThePublishedFileAndFailsSoftly() = runBlocking {
        // The file F4 publishes: web/public/policy/addon-blocklist.json.
        val published = """{"ids":[],"hosts":[]}"""
        val ok = FakeTransport { FakeTransport.ok(published) }
        val fetcher = AddonBlocklistFetcher(ok, "TSIPTV/1 (Stremio-addon-client)")
        assertEquals(AddonBlocklist.EMPTY, fetcher.fetch())
        assertEquals(AddonBlocklistFetcher.BLOCKLIST_URL, ok.requests.single())

        assertEquals(
            AddonBlocklist(listOf("a"), listOf("h")),
            AddonBlocklistFetcher(FakeTransport { FakeTransport.ok("""{"ids":["a"],"hosts":["h"],"extra":1}""") }, "ua").fetch(),
        )
        val bom = Char(0xFEFF).toString()
        assertEquals(
            AddonBlocklist(hosts = listOf("h")),
            AddonBlocklistFetcher(FakeTransport { FakeTransport.ok(bom + """{"ids":[],"hosts":["h"]}""") }, "ua").fetch(),
        )
        assertNull(AddonBlocklistFetcher(FakeTransport { FakeTransport.status(404) }, "ua").fetch())
        assertNull(AddonBlocklistFetcher(FakeTransport { FakeTransport.ok("<html/>") }, "ua").fetch())
        assertNull(AddonBlocklistFetcher(FakeTransport { error("offline") }, "ua").fetch())
    }

    @Test
    fun dailyCheck() {
        val day = AddonBlocklistFetcher.CHECK_INTERVAL_MS
        assertTrue(AddonBlocklistFetcher.isDue(null, 5))
        assertFalse(AddonBlocklistFetcher.isDue(1_000, 1_000 + day - 1))
        assertTrue(AddonBlocklistFetcher.isDue(1_000, 1_000 + day))
        assertTrue(AddonBlocklistFetcher.isDue(1_000, 999))
    }

    @Test
    fun blocklistUrlIsOurOwnHost() {
        assertTrue(AddonBlocklistFetcher.BLOCKLIST_URL.startsWith("https://tsiptv-8bdd6.web.app/"))
        // Sanity: fixtures directory is reachable (shared helper).
        assertTrue(TestAssets.read("stremio/sampler/manifest.json").contains("org.tsiptv.publicdomain.static"))
    }
}
