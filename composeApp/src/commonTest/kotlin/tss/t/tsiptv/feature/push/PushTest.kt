package tss.t.tsiptv.feature.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tss.t.tsiptv.core.language.LanguageRepository
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.feature.account.DeviceLimitTest
import tss.t.tsiptv.feature.account.DeviceSessionManager
import tss.t.tsiptv.feature.account.InMemoryAccountCloud
import tss.t.tsiptv.feature.account.LocalDevice
import tss.t.tsiptv.feature.account.RegisteredDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/prd-push-notifications.md: the link allowlist, topics, payload limits and the manager. */
class PushTest {

    @Test
    fun onlyAllowedLinksOpenSomething() {
        assertEquals(PushTarget.Home, PushLinkPolicy.parse("tsiptv://home"))
        assertEquals(PushTarget.Addons, PushLinkPolicy.parse("TSIPTV://addons/"))
        assertEquals(PushTarget.NotificationSettings, PushLinkPolicy.parse("tsiptv://notifications"))
        assertEquals(PushTarget.Store, PushLinkPolicy.parse("tsiptv://store"))
        assertEquals(
            PushTarget.Web("https://tsiptv-8bdd6.web.app/guides/stremio-addons/?lang=en"),
            PushLinkPolicy.parse("https://tsiptv-8bdd6.web.app/guides/stremio-addons/?lang=en"),
        )
        assertEquals(PushTarget.Web("https://tsiptv-8bdd6.web.app/"), PushLinkPolicy.parse("https://TSIPTV-8bdd6.web.app"))
        // Refused: other hosts, look-alikes, user info, ports, other schemes, intents, files.
        listOf(
            null, "", " ", "tsiptv://player?url=x", "tsiptv://", "http://tsiptv-8bdd6.web.app/",
            "https://evil.example/", "https://tsiptv-8bdd6.web.app.evil.example/", "https://evil.example/tsiptv-8bdd6.web.app",
            "https://user@tsiptv-8bdd6.web.app/", "https://tsiptv-8bdd6.web.app:444/", "https://tsiptv-8bdd6.web.app\\@evil.example/",
            "intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/x", "javascript:alert(1)", "market://details?id=x",
            "https://tsiptv-8bdd6.web.app/a b", "https://tsiptv-8bdd6.web.app/" + "x".repeat(3000),
        ).forEach { assertNull(PushLinkPolicy.parse(it), "should be refused: $it") }
    }

    @Test
    fun topicsFollowSettingsAndLanguage() {
        assertEquals(emptySet(), PushTopics.wanted(PushSettings(enabled = false), "vi"))
        assertEquals(setOf("all", "updates", "lang_vi"), PushTopics.wanted(PushSettings(enabled = true), "vi"))
        assertEquals(setOf("updates", "lang_en"), PushTopics.wanted(PushSettings(enabled = true, general = false), "en"))
        assertEquals(emptySet(), PushTopics.wanted(PushSettings(enabled = true, general = false, updates = false), "en"))
        assertEquals("lang_zh", PushTopics.language("zh-CN"))
    }

    @Test
    fun payloadsAreLimitedAndChannelsKnown() {
        assertNull(PushMessage.from(null, null, emptyMap()))
        val m = PushMessage.from(null, null, mapOf("title" to "T".repeat(300), "body" to "B", "channel" to "spam", "link" to "x"))!!
        assertEquals(PushMessage.MAX_TITLE, m.title.length)
        assertEquals(PushChannels.GENERAL, m.channel)
        assertEquals(PushChannels.UPDATES, PushMessage.from("t", "b", mapOf("channel" to "updates"))!!.channel)
        assertTrue("x" !in m.toString())
    }

    private class FakePlatform(var granted: Boolean) : PushPlatform {
        override val isSupported = true
        override val showDebugToken = false
        val tokenFlow = MutableStateFlow<String?>(null)
        override val token: StateFlow<String?> = tokenFlow
        val topics = mutableSetOf<String>()
        var prompts = 0
        override fun permission() = if (granted) PushPermission.GRANTED else PushPermission.DENIED
        override suspend fun requestPermission(): PushPermission {
            prompts++
            return permission()
        }
        override fun openSystemSettings() = Unit
        override suspend fun subscribe(topic: String) = topics.add(topic).let { true }
        override suspend fun unsubscribe(topic: String) = topics.remove(topic).let { true }
        override suspend fun refreshToken() = Unit
        override fun systemLanguage() = "vi"
    }

    private suspend fun until(check: suspend () -> Boolean) = withTimeout(5_000) { while (!check()) delay(10) }

    @Test
    fun managerAsksOnlyOnRequestSubscribesAndStoresTheTokenWhenSignedIn() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
        val cloud = InMemoryAccountCloud()
        val auth = DeviceLimitTest.FakeAuthForTests("u1")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val local = LocalDevice(storage)
            val sessions = DeviceSessionManager(auth, cloud, local, storage, scope) { 1_000L }
            sessions.start()
            until { cloud.isDeviceRegistered("u1", local.installationId()) }
            val platform = FakePlatform(granted = false)
            val push = PushManager(platform, storage, LanguageRepository(storage), sessions, scope)
            push.start()

            // Nothing asked or subscribed at start (R1).
            delay(100)
            assertEquals(0, platform.prompts)
            assertTrue(platform.topics.isEmpty())

            // Denied: the switch stays off.
            assertEquals(PushPermission.DENIED, push.setEnabled(true))
            assertEquals(false, push.settings.value.enabled)
            assertEquals(1, platform.prompts)

            platform.granted = true
            assertEquals(PushPermission.GRANTED, push.setEnabled(true))
            until { platform.topics == setOf("all", "updates", "lang_vi") }
            push.setGeneral(false)
            until { platform.topics == setOf("updates", "lang_vi") }
            push.setEnabled(false)
            until { platform.topics.isEmpty() }

            // The token reaches the registered device document while signed in.
            platform.tokenFlow.value = "tok-1"
            until { cloud.fcmTokens["u1" to local.installationId()] == "tok-1" }
            assertEquals("tok-1", storage.getString(PushManager.KEY_TOKEN))

            // Links: only allowlisted targets, else Home.
            push.onNotificationOpened("intent://evil")
            assertEquals(PushTarget.Home, push.links.replayCache.single())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun noTokenWriteWhenSignedOut() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
        val cloud = InMemoryAccountCloud()
        cloud.registerDevice("u1", RegisteredDevice(id = "x"))
        val auth = DeviceLimitTest.FakeAuthForTests(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sessions = DeviceSessionManager(auth, cloud, LocalDevice(storage), storage, scope) { 1_000L }
            sessions.start()
            val platform = FakePlatform(granted = true)
            val push = PushManager(platform, storage, LanguageRepository(storage), sessions, scope)
            push.start()
            platform.tokenFlow.value = "tok-2"
            until { storage.getString(PushManager.KEY_TOKEN) == "tok-2" }
            delay(100)
            assertTrue(cloud.fcmTokens.isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
