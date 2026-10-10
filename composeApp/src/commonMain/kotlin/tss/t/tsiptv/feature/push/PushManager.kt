package tss.t.tsiptv.feature.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /** Bumped when a new token arrives: the topics are subscribed again for it. */
    private val tokenEpoch = MutableStateFlow(0)

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
            launch { syncAccountToken() }
            // Token and topics follow the settings and the app language (no token before opt-in).
            launch {
                languages.observeLanguageSettings().map { it.languageCode }.distinctUntilChanged()
                    .collect { runCatching { platform.refreshChannelNames(it) } }
            }
            // Opt-out that failed offline: retried when the network is back (and at every start).
            launch {
                combine(_settings.map { it.enabled }.distinctUntilChanged(), platform.online.distinctUntilChanged()) { on, online -> on to online }
                    .collectLatest { (on, online) ->
                        // A few spaced retries while online (the first try can race the network).
                        var wait = 15_000L
                        while (!on && online && storage.getBoolean(KEY_TOKEN_ISSUED, false) && wait <= 120_000L) {
                            deleteTokenIfIssued()
                            if (!storage.getBoolean(KEY_TOKEN_ISSUED, false)) break
                            kotlinx.coroutines.delay(wait)
                            wait *= 2
                        }
                    }
            }
            combine(_settings, languages.observeLanguageSettings(), tokenEpoch) { s, lang, epoch ->
                Triple(s, lang.languageCode ?: platform.systemLanguage(), epoch)
            }.distinctUntilChanged().collect { (s, lang) -> apply(s, lang) }
        }
    }

    private suspend fun apply(settings: PushSettings, language: String) {
        if (settings.enabled) {
            storage.putBoolean(KEY_TOKEN_ISSUED, true)
            safe { platform.refreshToken(); true }
            syncTopics(PushTopics.wanted(settings, language))
        } else {
            // No per-topic unsubscribes: deleting the token drops its subscriptions at FCM, and a
            // topic call would create a token again (and waits a long time offline).
            deleteTokenIfIssued()
        }
    }

    private val deleteMutex = Mutex()

    /**
     * Opt-out: deletes the token if one was issued. The flags are cleared only when the delete
     * succeeded, so a failure (offline) is retried on reconnect and at the next start.
     */
    private suspend fun deleteTokenIfIssued() = deleteMutex.withLock {
        if (_settings.value.enabled || !storage.getBoolean(KEY_TOKEN_ISSUED, false)) return@withLock
        if (safe { platform.deleteToken() }) {
            storage.remove(KEY_TOKEN_ISSUED)
            storage.remove(KEY_TOKEN)
            storage.remove(KEY_TOPICS) // the deleted token had all the subscriptions
        }
    }

    /**
     * The token on this device's account document (R4): written while notifications are on and the
     * installation is registered (again after each full re-registration, never twice for the same
     * value), removed on opt-out.
     */
    private suspend fun syncAccountToken() {
        combine(platform.token, _settings.map { it.enabled }.distinctUntilChanged(), sessions.registration) { token, on, reg ->
            Triple(token, on, reg)
        }.distinctUntilChanged().collect { (token, on, reg) ->
            if (on && token != null) {
                val previous = storage.getString(KEY_TOKEN)
                if (previous != token) {
                    storage.putString(KEY_TOKEN, token)
                    // A replaced token has none of the old subscriptions.
                    if (previous.isNotEmpty()) storage.remove(KEY_TOPICS)
                    tokenEpoch.value++
                }
                if (reg != null) {
                    val mark = "${reg.uid}|$token"
                    if (storage.getString(DeviceSessionManager.KEY_PUSH_TOKEN_STORED) != mark &&
                        safe { sessions.storePushToken(reg, token) }
                    ) storage.putString(DeviceSessionManager.KEY_PUSH_TOKEN_STORED, mark)
                }
            } else if (!on && token != null && token != storage.getString(KEY_TOKEN)) {
                // A token appeared while switched off (e.g. a topic operation FCM had queued before
                // the opt-out ran later and created one): delete it as well.
                storage.putString(KEY_TOKEN, token)
                storage.putBoolean(KEY_TOKEN_ISSUED, true)
                deleteTokenIfIssued()
            }
            if (!on && reg != null) {
                val stored = storage.getString(DeviceSessionManager.KEY_PUSH_TOKEN_STORED)
                if (stored.startsWith("${reg.uid}|") && safe { sessions.storePushToken(reg, null) }) {
                    storage.remove(DeviceSessionManager.KEY_PUSH_TOKEN_STORED)
                }
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

    /**
     * [showMessages] from the stored choice, for the messaging service: a message can start the
     * process before [start] loaded the settings.
     */
    suspend fun showMessagesStored(): Boolean = storage.getBoolean(KEY_ENABLED, false)

    /** A tapped notification's link: only allowed targets (R3); anything else opens Home. */
    fun onNotificationOpened(link: String?) {
        _links.tryEmit(PushLinkPolicy.parse(link) ?: PushTarget.Home)
    }

    private val _homeTabRequested = MutableStateFlow(false)

    /** A Home link: the phone home screen also selects its Home tab (then calls [homeTabShown]). */
    val homeTabRequested: StateFlow<Boolean> = _homeTabRequested.asStateFlow()

    fun requestHomeTab() {
        _homeTabRequested.value = true
    }

    fun homeTabShown() {
        _homeTabRequested.value = false
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
        if (done != current) storage.putString(KEY_TOPICS, done.sorted().joinToString(","))
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

        /** A token was requested (opt-in): opt-out deletes it. */
        const val KEY_TOKEN_ISSUED = "push_token_issued"
    }
}
