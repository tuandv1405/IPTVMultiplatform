package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.security.SecretCipher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reversible stand-in: the real cipher is platform code (AES-GCM). */
internal class FakeCipher : SecretCipher {
    /** Simulates a key store that is not available yet (transient). */
    var keyAvailable = true
    override fun encrypt(plain: String): String = "v1:" + plain.reversed()
    override fun decryptResult(token: String): SecretCipher.DecryptResult = when {
        !token.startsWith("v1:") -> SecretCipher.DecryptResult.Invalid
        !keyAvailable -> SecretCipher.DecryptResult.KeyUnavailable
        else -> SecretCipher.DecryptResult.Ok(token.removePrefix("v1:").reversed())
    }
}

internal class MapSettings : AddonSettings {
    val values = HashMap<String, Any>()
    override suspend fun getLong(key: String): Long? = values[key] as? Long
    override suspend fun putLong(key: String, value: Long) { values[key] = value }
    override suspend fun getString(key: String): String? = values[key] as? String
    override suspend fun putString(key: String, value: String) { values[key] = value }
}

class AddonRepositoryTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private var now = 1_000_000_000L
    private val base = "https://tsiptv-8bdd6.web.app/examples/stremio-sampler"
    private var blocklistJson = """{"ids":[],"hosts":[]}"""
    private val stores = InMemoryStremioStores()
    private val cipher = FakeCipher()
    private val settings = MapSettings()
    private val sampler = FakeTransport.samplerHost(base)
    private val transport = FakeTransport { url ->
        when {
            url == AddonBlocklistFetcher.BLOCKLIST_URL -> FakeTransport.ok(blocklistJson)
            url.startsWith(base) -> sampler.get(url, emptyMap(), StremioRequestKind.CATALOG)
            url.startsWith("https://other.example.org/") -> otherHost(url)
            else -> FakeTransport.status(404)
        }
    }
    private var otherManifest = """{"id":"org.example.other","name":"Other","version":"1","types":["movie"],
        "resources":[{"name":"stream","types":["movie"],"idPrefixes":["tspd_"]}],"catalogs":[]}"""

    private fun otherHost(url: String): StremioHttpResponse = when {
        url.endsWith("/manifest.json") -> FakeTransport.ok(otherManifest)
        "/stream/movie/tspd_his_girl_friday.json" in url ->
            FakeTransport.ok("""{"streams":[{"name":"Other","url":"https://other.example.org/a.m3u8"},{"name":"T","infoHash":"x"}]}""")
        else -> FakeTransport.status(404)
    }

    private val repo = AddonRepository(
        store = stores,
        history = stores,
        client = StremioClient(transport, "ua", nowMs = { now }),
        cipher = cipher,
        blocklistFetcher = AddonBlocklistFetcher(transport, "ua"),
        settings = settings,
        nowMs = { now },
        scope = scope,
    )

    @AfterTest
    fun tearDown() = scope.cancel()

    private suspend fun add(input: String): InstalledAddon {
        val preview = assertIs<AddonPreviewResult.Ready>(repo.preview(input)).preview
        return repo.install(preview)
    }

    @Test
    fun acceptanceS2AddFromStremioLinkEncryptsTheUrl() = runBlocking {
        val result = repo.preview("stremio://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json")
        val preview = assertIs<AddonPreviewResult.Ready>(result).preview
        assertEquals("$base/manifest.json", preview.transport.manifestUrl)
        assertFalse(preview.isHttp)
        assertTrue(preview.providesStreams)
        assertEquals(3, preview.catalogCount)
        assertNull(preview.existing)
        val installed = repo.install(preview)
        val row = stores.getAddon(installed.id)!!
        assertFalse("tsiptv-8bdd6.web.app/examples" in row.transportUrlEnc) // AC-S21 (with the real cipher: ciphertext)
        assertEquals("tsiptv-8bdd6.web.app", row.transportHost)
        assertEquals("$base/manifest.json", repo.current().single().transport?.manifestUrl)
        assertEquals(true, repo.hasActiveAddons.first { it != null })
    }

    /** QC F3 #20: an addon two sources own stays until the last owner is removed; the user's own is never taken. */
    @Test
    fun sourceOwnedAddonsAreRefCounted() = runBlocking<Unit> {
        val preview = assertIs<AddonPreviewResult.Ready>(repo.preview("$base/manifest.json")).preview
        val id = repo.installOwned(preview, "tsiptv:a").id
        repo.installOwned(preview, "tsiptv:b")
        assertEquals(setOf(id), repo.ownedBy("tsiptv:a"))
        assertEquals(setOf(id), repo.ownedBy("tsiptv:b"))
        assertTrue(repo.current().single().isFromSource)

        repo.removeOwnedBy("tsiptv:a")
        assertEquals(listOf(id), repo.current().map { it.id }) // B still owns it
        assertTrue(repo.ownedBy("tsiptv:a").isEmpty())
        repo.removeOwnedBy("tsiptv:b")
        assertTrue(repo.current().isEmpty())

        // A user-installed addon stays the user's, and a source's removal leaves it alone.
        val mine = repo.install(preview)
        repo.installOwned(preview, "tsiptv:a")
        assertFalse(repo.current().single().isFromSource)
        repo.removeOwnedBy("tsiptv:a")
        assertEquals(listOf(mine.id), repo.current().map { it.id })
    }

    @Test
    fun acceptanceS3AndS4Errors() = runBlocking {
        assertEquals(AddonPreviewResult.InvalidUrl(ManifestUrlError.NOT_MANIFEST), repo.preview("https://example.com/foo"))
        assertEquals(AddonPreviewResult.InvalidUrl(ManifestUrlError.LEGACY), repo.preview("https://x.example.org/stremio/v1"))
        assertEquals(
            AddonPreviewResult.InvalidUrl(ManifestUrlError.LOCAL_SERVER),
            repo.preview("http://127.0.0.1:11470/local-addon/manifest.json"),
        )
        otherManifest = """{"id":"x","name":"X","version":"1","types":[]}"""
        assertEquals(AddonPreviewResult.InvalidManifest("resources"), repo.preview("https://other.example.org/manifest.json"))
        otherManifest = """{"id":"x","name":"X","version":"1","types":[],"resources":[],"behaviorHints":{"configurationRequired":true}}"""
        assertEquals(
            AddonPreviewResult.NeedsConfiguration("https://other.example.org/configure", "other.example.org"),
            repo.preview("https://other.example.org/manifest.json"),
        )
        assertEquals(AddonPreviewResult.Unreachable, repo.preview("https://down.example.org/manifest.json"))
        assertTrue(repo.current().isEmpty())
    }

    @Test
    fun acceptanceS6ReplaceKeepsPositionAndEnabledState() = runBlocking {
        val first = add("$base/manifest.json")
        add("https://other.example.org/manifest.json")
        repo.setEnabled(first.id, false)
        val again = assertIs<AddonPreviewResult.Ready>(repo.preview("$base/manifest.json?v=2")).preview
        assertEquals(first.id, again.existing?.id)
        repo.install(again)
        val list = repo.current()
        assertEquals(listOf(first.id, "org.example.other"), list.map { it.id })
        assertFalse(list[0].enabled)
        assertEquals("$base/manifest.json?v=2", list[0].transport?.manifestUrl)
    }

    @Test
    fun acceptanceS7EnableReorderRemove() = runBlocking {
        val sampler = add("$base/manifest.json")
        val other = add("https://other.example.org/manifest.json")
        assertEquals(3, repo.boardRows(repo.current()).size)

        // Streams from both addons, grouped in addon order; unsupported kept but classified.
        var states = repo.streams("movie", "tspd_his_girl_friday", list = repo.current()).toList()
        assertEquals(listOf(sampler.id, other.id), states.last().groups.map { it.addonId })
        assertTrue(states.last().hasPlayable)

        repo.move(other.id, -1)
        assertEquals(listOf(other.id, sampler.id), repo.current().map { it.id })
        states = repo.streams("movie", "tspd_his_girl_friday", list = repo.current()).toList()
        assertEquals(listOf(other.id, sampler.id), states.last().groups.map { it.addonId })

        repo.setEnabled(sampler.id, false)
        assertEquals(AddonStatus.OFF, repo.current().first { it.id == sampler.id }.status)
        assertTrue(repo.boardRows(repo.current()).isEmpty())
        states = repo.streams("movie", "tspd_his_girl_friday", list = repo.current()).toList()
        assertEquals(listOf(other.id), states.last().groups.map { it.addonId })
        repo.setEnabled(sampler.id, true)
        assertEquals(3, repo.boardRows(repo.current()).size)

        stores.upsert(record(sampler.id))
        stores.upsert(record(other.id))
        repo.remove(sampler.id)
        assertEquals(listOf(other.id), repo.current().map { it.id })
        assertEquals(listOf(other.id), stores.observeHistory().first().map { it.sourceId })
        repo.remove(other.id)
        assertEquals(false, repo.hasActiveAddons.first { it != null })

        repo.reorder(listOf("nope"))
    }

    private fun record(sourceId: String) = MediaHistoryRecord(
        sourceKind = MediaSourceKind.STREMIO, sourceId = sourceId, itemType = "movie", itemId = "tspd_his_girl_friday",
        videoId = "tspd_his_girl_friday", title = "H", subtitle = null, posterUrl = null, season = null, episode = null,
        lastAddonId = sourceId, lastBingeGroup = null, positionMs = 120_000, durationMs = 5_000_000, finished = false, updatedAt = now,
    )

    @Test
    fun acceptanceS5AdultAddonsAreNotOnTheBoardOrInSearch() = runBlocking {
        otherManifest = """{"id":"org.example.adult","name":"A","version":"1","types":["movie"],"resources":["catalog"],
            "catalogs":[{"type":"movie","id":"a","extra":[{"name":"search"}]}],"behaviorHints":{"adult":true,"p2p":true}}"""
        val preview = assertIs<AddonPreviewResult.Ready>(repo.preview("https://other.example.org/manifest.json")).preview
        assertTrue(preview.isAdult)
        assertTrue(preview.isP2p)
        val adult = repo.install(preview)
        assertTrue(adult.isAdult)
        assertTrue(repo.boardRows(repo.current()).isEmpty())
        val search = repo.search("his", repo.current()).toList().last()
        assertTrue(search.groups.isEmpty())
        assertTrue(search.isDone)
    }

    @Test
    fun acceptanceS22Blocklist() = runBlocking {
        blocklistJson = """{"ids":[],"hosts":["other.example.org"]}"""
        assertEquals(AddonPreviewResult.Blocked, repo.preview("https://other.example.org/manifest.json"))

        blocklistJson = """{"ids":[],"hosts":[]}"""
        repo.checkBlocklistIfDue(force = true)
        val installed = add("https://other.example.org/manifest.json")
        assertEquals(AddonStatus.OK, repo.current().single().status)

        // Next daily check: now blocked → disabled.
        blocklistJson = """{"ids":["org.example.other"],"hosts":[]}"""
        repo.checkBlocklistIfDue() // not due yet: nothing changes
        assertTrue(repo.current().single().enabled)
        now += AddonRepository.DAY_MS
        repo.checkBlocklistIfDue()
        val blocked = repo.current().single()
        assertEquals(AddonStatus.BLOCKED, blocked.status)
        assertFalse(blocked.enabled)
        assertFalse(blocked.isActive)
        repo.setEnabled(installed.id, true) // cannot re-enable a blocked addon
        assertFalse(repo.current().single().enabled)
    }

    @Test
    fun refreshUpdatesVersionAndCountsFailures() = runBlocking {
        val other = add("https://other.example.org/manifest.json")
        otherManifest = otherManifest.replace("\"version\":\"1\"", "\"version\":\"2\"")
        assertEquals(AddonRefreshResult.Updated("2"), repo.refresh(other.id))
        assertEquals(AddonRefreshResult.Unchanged, repo.refresh(other.id))
        otherManifest = "<html>"
        repeat(3) { assertEquals(AddonRefreshResult.Failed, repo.refresh(other.id)) }
        val row = repo.current().single()
        assertEquals(AddonStatus.UNREACHABLE, row.status)
        assertEquals("invalid_manifest", row.stored.lastError)
        assertEquals("2", row.stored.version) // keeps working from the cached manifest
        assertTrue(row.isActive)
    }

    @Test
    fun metaAndInlineStreams() = runBlocking {
        add("$base/manifest.json")
        val list = repo.current()
        val meta = repo.meta("series", "tspd_superman", list = list)
        assertEquals("Fleischer Superman (1941-43)", meta.meta?.name)
        assertEquals("org.tsiptv.publicdomain.static", meta.sourceAddonId)
        val missing = repo.meta("movie", "tspd_nope", preview = StremioMeta(id = "tspd_nope", type = "movie", name = "Preview"), list = list)
        assertEquals("Preview", missing.meta?.name)
        assertEquals(0, missing.failedAddons) // 404 = not found here, not a failure

        // AC-S18: inline streams, no request.
        val before = transport.requests.size
        val inline = repo.streams("series", "x", inline = listOf(StremioStream(url = "https://a/b.mp4")), list = list).toList()
        assertEquals(before, transport.requests.size)
        assertEquals(StreamGroup.INLINE, inline.single().groups.single().addonId)
    }

    @Test
    fun searchFansOutProgressively() = runBlocking {
        otherManifest = """{"id":"org.example.other","name":"Other","version":"1","types":["movie"],"resources":["catalog"],
            "catalogs":[{"type":"movie","id":"s","extra":[{"name":"search","isRequired":true}]}]}"""
        add("https://other.example.org/manifest.json")
        val states = repo.search("his", repo.current()).toList()
        assertEquals(1, states.first().pending)
        assertTrue(states.last().isDone)
        assertEquals(1, states.last().failedAddons) // other.example.org answers 404 for the search
    }

    @Test
    fun anUnavailableKeyIsTransientAndRetried() = runBlocking {
        val a = add("$base/manifest.json")
        cipher.keyAvailable = false
        // A new token (not in the decrypt cache) while the key store is unavailable.
        stores.upsertAddon(stores.getAddon(a.id)!!.copy(transportUrlEnc = cipher.encrypt("$base/manifest.json?x=1")))
        val unavailable = repo.current().single()
        assertNull(unavailable.transport)
        assertFalse(unavailable.secretLost)
        assertEquals(AddonStatus.UNREACHABLE, unavailable.status)
        // QC r3 R2: never a known "no addons" while the only addon's key is just unavailable.
        assertEquals(null, repo.hasActiveAddons.first())
        // Disabled wins over the key state.
        repo.setEnabled(a.id, false)
        assertEquals(AddonStatus.OFF, repo.current().single().status)
        repo.setEnabled(a.id, true)
        assertEquals(AddonRefreshResult.Failed, repo.refresh(a.id))
        assertEquals("key_unavailable", repo.current().single().stored.lastError)
        // Not cached: once the key store is back, the same token decrypts.
        cipher.keyAvailable = true
        val back = repo.current().single()
        assertEquals("$base/manifest.json?x=1", back.transport?.manifestUrl)
        assertFalse(back.status == AddonStatus.SECRET_LOST)
    }

    @Test
    fun undecryptableAddonIsSecretLostAndInactive() = runBlocking {
        val a = add("$base/manifest.json")
        stores.upsertAddon(stores.getAddon(a.id)!!.copy(transportUrlEnc = "garbage"))
        val row = repo.current().single()
        assertNull(row.transport)
        assertEquals(AddonStatus.SECRET_LOST, row.status)
        assertFalse(row.isActive)
        assertEquals(AddonRefreshResult.Failed, repo.refresh(a.id))
        assertEquals("secret_lost", repo.current().single().stored.lastError)
        assertNotEquals(0, repo.current().single().stored.failCount)
    }
}
