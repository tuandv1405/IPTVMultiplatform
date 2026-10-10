package tss.t.tsiptv.feature.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a notification may take the user (docs/prd-push-notifications.md R3). */
sealed interface PushTarget {
    data object Home : PushTarget
    data object Addons : PushTarget
    data object NotificationSettings : PushTarget

    /** The app's store page (Play Store app, else the browser). */
    data object Store : PushTarget

    /** An https page on the app's own site. */
    data class Web(val url: String) : PushTarget
}

/**
 * The deep-link allowlist (R3). Pure: no intent, scheme or host outside this list is ever opened.
 * Anything not allowed returns null and the app simply opens Home.
 */
object PushLinkPolicy {
    const val SITE_HOST = "tsiptv-8bdd6.web.app"
    private const val MAX_LINK = 2048

    fun parse(link: String?): PushTarget? {
        val raw = link?.trim() ?: return null
        if (raw.isEmpty() || raw.length > MAX_LINK) return null
        if (raw.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) return null
        val lower = raw.lowercase()
        if (lower.startsWith("tsiptv://")) {
            return when (lower.removePrefix("tsiptv://").trimEnd('/')) {
                "home" -> PushTarget.Home
                "addons" -> PushTarget.Addons
                "notifications" -> PushTarget.NotificationSettings
                "store" -> PushTarget.Store
                else -> null
            }
        }
        if (!lower.startsWith("https://")) return null
        val rest = raw.substring("https://".length)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        // No user info, no port, exactly the app's host.
        if ('@' in authority || ':' in authority) return null
        if (!authority.equals(SITE_HOST, ignoreCase = true)) return null
        if ("\\" in rest) return null
        return PushTarget.Web("https://$SITE_HOST" + rest.substring(authority.length).ifEmpty { "/" })
    }
}

/** FCM topics (PRD §5). */
object PushTopics {
    const val ALL = "all"
    const val UPDATES = "updates"
    /** `lang_<primary language>` ("zh-CN" → `lang_zh`). */
    fun language(code: String): String =
        "lang_" + code.lowercase().substringBefore('-').substringBefore('_').filter { it in 'a'..'z' }.take(8).ifEmpty { "en" }

    /** The topics a user with [settings] in app language [languageCode] should be subscribed to. */
    fun wanted(settings: PushSettings, languageCode: String): Set<String> {
        if (!settings.enabled) return emptySet()
        return buildSet {
            if (settings.general) add(ALL)
            if (settings.updates) add(UPDATES)
            if (settings.general || settings.updates) add(language(languageCode))
        }
    }
}

/** What the user chose on the Notifications screen. Off until the user turns it on (R1). */
data class PushSettings(
    val enabled: Boolean = false,
    val general: Boolean = true,
    val updates: Boolean = true,
)

enum class PushPermission { GRANTED, DENIED, NOT_REQUIRED, UNKNOWN }

/** One message as it arrives (foreground) or as built from data (background data-only). */
data class PushMessage(val title: String, val body: String, val channel: String, val link: String?) {
    // Never print the body or the link (they can be long or private).
    override fun toString(): String = "PushMessage(channel=$channel, hasLink=${link != null})"

    companion object {
        const val MAX_TITLE = 100
        const val MAX_BODY = 500

        /** From the FCM data map (or a notification's title/body), with limits and a known channel. */
        fun from(title: String?, body: String?, data: Map<String, String>): PushMessage? {
            val t = (title ?: data["title"]).orEmpty().trim().take(MAX_TITLE)
            val b = (body ?: data["body"]).orEmpty().trim().take(MAX_BODY)
            if (t.isEmpty() && b.isEmpty()) return null
            val channel = data["channel"]?.takeIf { it in PushChannels.ALL } ?: PushChannels.GENERAL
            return PushMessage(t, b, channel, data["link"])
        }
    }
}

object PushChannels {
    const val GENERAL = "general"
    const val UPDATES = "updates"
    val ALL = setOf(GENERAL, UPDATES)
}

/**
 * The platform's push service. Android: Firebase Cloud Messaging; iOS (until set up, see
 * `docs/setup-push-ios.md`) and desktop: [NoPushPlatform].
 */
interface PushPlatform {
    val isSupported: Boolean

    /** Debug builds: the token is shown on the Notifications screen (and logged) for test sends. */
    val showDebugToken: Boolean

    /** The current registration token, or null (not yet known, or unsupported). */
    val token: StateFlow<String?>

    fun permission(): PushPermission

    /** Shows the system prompt where needed; returns the result. */
    suspend fun requestPermission(): PushPermission

    /** Opens the system notification settings of the app. */
    fun openSystemSettings()

    suspend fun subscribe(topic: String): Boolean
    suspend fun unsubscribe(topic: String): Boolean

    /**
     * Allows the SDK to create a token and asks for it (fills [token]). Called only while the user
     * has notifications switched on (no token exists before opt-in).
     */
    suspend fun refreshToken()

    /** Opt-out: stops automatic token creation and deletes the token on the device and at FCM. */
    suspend fun deleteToken()

    /** The device language, for the language topic when the app follows the system. */
    fun systemLanguage(): String

    /** The in-app language changed (null = follow the system): channel names follow it. */
    fun refreshChannelNames(languageCode: String?) = Unit

    /** Debug builds: shows [message] through the same code path as a received FCM message. */
    fun showTestNotification(message: PushMessage) = Unit
}

object NoPushPlatform : PushPlatform {
    override val isSupported = false
    override val showDebugToken = false
    private val none = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = none.asStateFlow()
    override fun permission() = PushPermission.UNKNOWN
    override suspend fun requestPermission() = PushPermission.UNKNOWN
    override fun openSystemSettings() = Unit
    override suspend fun subscribe(topic: String) = false
    override suspend fun unsubscribe(topic: String) = false
    override suspend fun refreshToken() = Unit
    override suspend fun deleteToken() = Unit
    override fun systemLanguage() = "en"
}
