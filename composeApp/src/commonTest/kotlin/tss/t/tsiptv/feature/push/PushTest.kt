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
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.feature.account.AccountCloud
import tss.t.tsiptv.feature.account.DeviceLimitTest
import tss.t.tsiptv.feature.account.DeviceSessionManager
import tss.t.tsiptv.feature.account.InMemoryAccountCloud
import tss.t.tsiptv.feature.account.LocalDevice
import tss.t.tsiptv.feature.account.RegisterResult
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
        var refreshes = 0
        var deletes = 0
        var nextToken = "tok-1"
        override fun permission() = if (granted) PushPermission.GRANTED else PushPermission.DENIED
        override suspend fun requestPermission(): PushPermission {
            prompts++
            return permission()
        }
        override fun openSystemSettings() = Unit
        override suspend fun subscribe(topic: String) = topics.add(topic).let { true }
        override suspend fun unsubscribe(topic: String) = topics.remove(topic).let { true }
        override suspend fun refreshToken() {
            refreshes++
            if (tokenFlow.value == null) tokenFlow.value = nextToken
        }
        override suspend fun deleteToken() {
            deletes++
            tokenFlow.value = null
        }
        override fun systemLanguage() = "vi"
    }

    /** Counts token writes; registration can be slowed down to reproduce the first sign-in race. */
    private class CountingCloud(val inner: InMemoryAccountCloud, val registerDelay: Long = 0) : AccountCloud by inner {
        var tokenWrites = 0
        override suspend fun registerDevice(uid: String, device: RegisteredDevice, max: Int): RegisterResult {
            delay(registerDelay)
            return inner.registerDevice(uid, device, max)
        }
        override suspend fun updateFcmToken(uid: String, deviceId: String, token: String) {
            tokenWrites++
            inner.updateFcmToken(uid, deviceId, token)
        }
    }

    private suspend fun until(check: suspend () -> Boolean) = withTimeout(5_000) { while (!check()) delay(10) }

    @Test
    fun noTokenBeforeOptInAndTokenRemovedOnOptOut() = runBlocking<Unit> {
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

            // Nothing asked, subscribed or created at start (R1, no token before opt-in).
            delay(200)
            assertEquals(0, platform.prompts)
            assertEquals(0, platform.refreshes)
            assertNull(platform.tokenFlow.value)
            assertTrue(platform.topics.isEmpty())
            assertTrue(cloud.fcmTokens.isEmpty())

            // Denied: the switch stays off, still no token.
            assertEquals(PushPermission.DENIED, push.setEnabled(true))
            assertEquals(false, push.settings.value.enabled)
            assertEquals(1, platform.prompts)
            delay(100)
            assertEquals(0, platform.refreshes)

            // Opt-in: token, topics, and the token on the registered device document.
            platform.granted = true
            assertEquals(PushPermission.GRANTED, push.setEnabled(true))
            until { platform.topics == setOf("all", "updates", "lang_vi") }
            until { cloud.fcmTokens["u1" to local.installationId()] == "tok-1" }
            assertEquals("tok-1", storage.getString(PushManager.KEY_TOKEN))
            assertTrue(push.showMessagesStored())
            push.setGeneral(false)
            until { platform.topics == setOf("updates", "lang_vi") }

            // Opt-out: topics left first, token deleted on the device and removed from the document.
            push.setEnabled(false)
            until { platform.topics.isEmpty() && platform.deletes == 1 }
            until { cloud.fcmTokens.isEmpty() }
            assertNull(platform.tokenFlow.value)
            assertEquals("", storage.getString(PushManager.KEY_TOKEN))
            assertEquals(false, push.showMessagesStored())

            // Links: only allowlisted targets, else Home.
            push.onNotificationOpened("intent://evil")
            assertEquals(PushTarget.Home, push.links.replayCache.single())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun tokenIsWrittenAfterTheFirstRegistrationAndAgainAfterAFullRewrite() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
        storage.putBoolean(PushManager.KEY_ENABLED, true) // opted in before signing in
        val inner = InMemoryAccountCloud()
        val cloud = CountingCloud(inner, registerDelay = 300)
        val auth = DeviceLimitTest.FakeAuthForTests(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val local = LocalDevice(storage)
            val sessions = DeviceSessionManager(auth, cloud, local, storage, scope) { 1_000L }
            sessions.start()
            val platform = FakePlatform(granted = true)
            val push = PushManager(platform, storage, LanguageRepository(storage), sessions, scope)
            push.start()
            until { storage.getString(PushManager.KEY_TOKEN) == "tok-1" }
            assertTrue(inner.fcmTokens.isEmpty())

            // First sign-in: the uid is known about 300 ms before the device is registered (the old race).
            auth.state.value = auth.state.value.copy(
                isAuthenticated = true,
                user = FirebaseUser(uid = "u1", email = null, displayName = null, photoUrl = null, isEmailVerified = false),
            )
            until { inner.fcmTokens["u1" to local.installationId()] == "tok-1" }
            assertEquals(1, cloud.tokenWrites)

            // An unchanged token is not written again on the next check.
            sessions.check()
            delay(200)
            assertEquals(1, cloud.tokenWrites)

            // A full re-registration (document rewritten without fcmToken) writes it again.
            inner.removeDevice("u1", local.installationId())
            storage.remove(DeviceSessionManager.KEY_REGISTERED_UID)
            sessions.check()
            until { inner.fcmTokens["u1" to local.installationId()] == "tok-1" }
            assertEquals(2, cloud.tokenWrites)

            // A new token replaces it and the topics are subscribed again for it.
            platform.topics.clear()
            platform.tokenFlow.value = "tok-2"
            until { inner.fcmTokens["u1" to local.installationId()] == "tok-2" }
            until { platform.topics == setOf("all", "updates", "lang_vi") }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun noTokenWriteWhenSignedOut() = runBlocking<Unit> {
        val storage = InMemoryKeyValueStorage()
        storage.putBoolean(PushManager.KEY_ENABLED, true)
        val cloud = InMemoryAccountCloud()
        cloud.registerDevice("u1", RegisteredDevice(id = "x"))
        val auth = DeviceLimitTest.FakeAuthForTests(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sessions = DeviceSessionManager(auth, cloud, LocalDevice(storage), storage, scope) { 1_000L }
            sessions.start()
            val platform = FakePlatform(granted = true)
            platform.nextToken = "tok-2"
            val push = PushManager(platform, storage, LanguageRepository(storage), sessions, scope)
            push.start()
            until { storage.getString(PushManager.KEY_TOKEN) == "tok-2" }
            delay(100)
            assertTrue(cloud.fcmTokens.isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
