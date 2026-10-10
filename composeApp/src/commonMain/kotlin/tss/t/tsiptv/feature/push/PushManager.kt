package tss.t.tsiptv.feature.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.tsiptv.core.language.LanguageRepository
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.feature.account.DeviceSessionManager

/**
 * Push notifications (docs/prd-push-notifications.md): the user's choices, topic subscriptions that
 * follow them and the app language, the token kept locally and (signed in, registered device) on the
 * account's device document, and the deep links of tapped notifications.
 */
class PushManager(
    private val platform: PushPlatform,
    private val storage: KeyValueStorage,
    private val languages: LanguageRepository,
    private val sessions: DeviceSessionManager,
    private val scope: CoroutineScope,
) {
    private val _settings = MutableStateFlow(PushSettings())
    val settings: StateFlow<PushSettings> = _settings.asStateFlow()

    val isSupported: Boolean get() = platform.isSupported
    val showDebugToken: Boolean get() = platform.showDebugToken
    val token: StateFlow<String?> get() = platform.token

    private val _permission = MutableStateFlow(PushPermission.UNKNOWN)
    val permission: StateFlow<PushPermission> = _permission.asStateFlow()

    /** The system prompt was shown once: only then is "not granted" a refusal to explain. */
    private val _asked = MutableStateFlow(false)
    val asked: StateFlow<Boolean> = _asked.asStateFlow()

    private val _links = MutableSharedFlow<PushTarget>(replay = 1, extraBufferCapacity = 4)

    /** Targets of tapped notifications; the app root navigates (replayed once for a cold start). */
    val links: SharedFlow<PushTarget> = _links.asSharedFlow()

    private val mutex = Mutex()

    fun start() {
        if (!platform.isSupported) return
        scope.launch {
            _settings.value = PushSettings(
                enabled = storage.getBoolean(KEY_ENABLED, false),
                general = storage.getBoolean(KEY_GENERAL, true),
                updates = storage.getBoolean(KEY_UPDATES, true),
            )
            _asked.value = storage.getBoolean(KEY_ASKED, false)
            refreshPermission()
            runCatching { platform.refreshToken() }
            // Topics follow the settings and the app language.
            combine(_settings, languages.observeLanguageSettings()) { s, lang ->
                s to (lang.languageCode ?: platform.systemLanguage())
            }.distinctUntilChanged().collect { (s, lang) -> syncTopics(PushTopics.wanted(s, lang)) }
        }
        scope.launch {
            // The token goes to the device document only when signed in and registered (R4).
            combine(platform.token.filterNotNull(), sessions.uid) { token, uid -> token to uid }
                .distinctUntilChanged()
                .collect { (token, uid) ->
                    storage.putString(KEY_TOKEN, token)
                    if (uid != null) runCatching { sessions.storePushToken(token) }
                }
        }
    }

    fun openSystemSettings() = platform.openSystemSettings()

    /** Debug builds only (the button is hidden otherwise): a local notification with [link]. */
    fun showTestNotification(link: String?) {
        if (!platform.showDebugToken || !showMessages) return
        platform.showTestNotification(PushMessage("TS IPTV test", "Test notification (debug build)", PushChannels.GENERAL, link))
    }

    fun refreshPermission() {
        _permission.value = platform.permission()
    }

    /**
     * The master switch (R1). Turning it on asks for the permission first (after the screen showed
     * the rationale); when that is denied the switch stays off. Returns the permission result.
     */
    suspend fun setEnabled(enabled: Boolean): PushPermission {
        if (!enabled) {
            update { it.copy(enabled = false) }
            return _permission.value
        }
        val result = when (platform.permission()) {
            PushPermission.GRANTED, PushPermission.NOT_REQUIRED -> platform.permission()
            else -> {
                _asked.value = true
                storage.putBoolean(KEY_ASKED, true)
                platform.requestPermission()
            }
        }
        _permission.value = result
        if (result == PushPermission.GRANTED || result == PushPermission.NOT_REQUIRED) update { it.copy(enabled = true) }
        return result
    }

    suspend fun setGeneral(on: Boolean) = update { it.copy(general = on) }
    suspend fun setUpdates(on: Boolean) = update { it.copy(updates = on) }

    /** Whether a message that arrives now may be shown (R7). */
    val showMessages: Boolean get() = _settings.value.enabled

    /** A tapped notification's link: only allowed targets (R3); anything else opens Home. */
    fun onNotificationOpened(link: String?) {
        _links.tryEmit(PushLinkPolicy.parse(link) ?: PushTarget.Home)
    }

    /** The app handled the last link (so a later recomposition does not replay it). */
    fun consumeLink() {
        _links.resetReplayCache()
    }

    private suspend fun update(change: (PushSettings) -> PushSettings) {
        val next = change(_settings.value)
        _settings.value = next
        storage.putBoolean(KEY_ENABLED, next.enabled)
        storage.putBoolean(KEY_GENERAL, next.general)
        storage.putBoolean(KEY_UPDATES, next.updates)
    }

    private suspend fun syncTopics(wanted: Set<String>) = mutex.withLock {
        val current = storage.getString(KEY_TOPICS).split(',').filter { it.isNotBlank() }.toSet()
        val done = current.toMutableSet()
        for (topic in current - wanted) if (safe { platform.unsubscribe(topic) }) done -= topic
        for (topic in wanted - current) if (safe { platform.subscribe(topic) }) done += topic
        storage.putString(KEY_TOPICS, done.sorted().joinToString(","))
    }

    private suspend fun safe(block: suspend () -> Boolean): Boolean = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        const val KEY_ENABLED = "push_enabled"
        const val KEY_GENERAL = "push_topic_general"
        const val KEY_UPDATES = "push_topic_updates"
        const val KEY_TOPICS = "push_subscribed_topics"
        const val KEY_TOKEN = "push_fcm_token"
        const val KEY_ASKED = "push_permission_asked"
    }
}
