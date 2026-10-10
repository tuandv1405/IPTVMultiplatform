package tss.t.tsiptv.feature.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import tss.t.tsiptv.MainActivity
import tss.t.tsiptv.R
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Firebase Cloud Messaging (docs/prd-push-notifications.md). The token, topic subscriptions, the
 * `POST_NOTIFICATIONS` prompt (Android 13+, only on request from the Notifications screen) and the
 * notification channels.
 */
class AndroidPushPlatform(private val context: Context) : PushPlatform {

    override val isSupported: Boolean by lazy {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    override val showDebugToken: Boolean get() = isDebuggable(context)

    private val _token = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = _token.asStateFlow()

    internal fun onNewToken(token: String) {
        _token.value = token
    }

    override fun permission(): PushPermission = when {
        Build.VERSION.SDK_INT < 33 ->
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) PushPermission.NOT_REQUIRED else PushPermission.DENIED
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ->
            PushPermission.GRANTED
        else -> PushPermission.DENIED
    }

    override suspend fun requestPermission(): PushPermission {
        if (Build.VERSION.SDK_INT < 33) return permission()
        if (permission() == PushPermission.GRANTED) return PushPermission.GRANTED
        val launcher = PushPermissionRequests.launcher ?: return permission()
        val answer = CompletableDeferred<Boolean>()
        PushPermissionRequests.pending = answer
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        answer.await()
        return permission()
    }

    override fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override suspend fun subscribe(topic: String): Boolean = task { FirebaseMessaging.getInstance().subscribeToTopic(topic) }
    override suspend fun unsubscribe(topic: String): Boolean = task { FirebaseMessaging.getInstance().unsubscribeFromTopic(topic) }

    override suspend fun refreshToken() {
        if (!isSupported) return
        // Auto-init is off in the manifest: no token exists before the user switches notifications on.
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        val token = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { if (cont.isActive) cont.resume(if (it.isSuccessful) it.result else null) }
            }
        }
        if (token != null) _token.value = token
        if (isDebuggable(context)) Log.d(TAG, "FCM token: ${token ?: "(none)"}")
    }

    override suspend fun deleteToken(): Boolean {
        if (!isSupported) return true
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = false
        val ok = task { messaging.deleteToken() }
        if (ok) _token.value = null
        if (isDebuggable(context)) Log.d(TAG, if (ok) "FCM token deleted" else "FCM token delete failed (retried later)")
        return ok
    }

    override val online: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.callbackFlow {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        if (cm == null) {
            trySend(true); awaitClose { }; return@callbackFlow
        }
        val validated = android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
        // Default-network callback: "online" means the internet was validated, not just a link.
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                trySend(caps.hasCapability(validated))
            }
            override fun onLost(network: android.net.Network) { trySend(false) }
        }
        trySend(cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(validated) == true)
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }

    override fun refreshChannelNames(languageCode: String?) {
        val localized = if (languageCode.isNullOrBlank()) context else {
            val tag = if (languageCode == "zh") "zh-CN" else languageCode
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            context.createConfigurationContext(config)
        }
        createChannels(context, localized)
    }

    override fun systemLanguage(): String = Locale.getDefault().language.ifEmpty { "en" }

    override fun showTestNotification(message: PushMessage) {
        if (isDebuggable(context)) TsFirebaseMessagingService.show(context, message, (System.currentTimeMillis() % 100_000).toInt())
    }

    private suspend fun task(start: () -> com.google.android.gms.tasks.Task<Void>): Boolean {
        if (!isSupported) return false
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                start().addOnCompleteListener { if (cont.isActive) cont.resume(it.isSuccessful) }
            }
        } ?: false
    }

    companion object {
        const val TAG = "TSPush"

        /** Intent extra carrying the payload `link` into MainActivity. FCM uses data keys as extras. */
        const val EXTRA_LINK = "link"

        fun isDebuggable(context: Context) = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        /** Channels exist before the first message so FCM can use `general` for background messages. */
        fun createChannels(context: Context, names: Context = context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            // Re-creating an existing channel only updates its name (the user's settings stay).
            nm.createNotificationChannel(
                NotificationChannel(PushChannels.GENERAL, names.getString(R.string.push_channel_general), NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.createNotificationChannel(
                NotificationChannel(PushChannels.UPDATES, names.getString(R.string.push_channel_updates), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}

/** The `POST_NOTIFICATIONS` launcher registered by MainActivity (registerForActivityResult needs an activity). */
object PushPermissionRequests {
    @Volatile
    var launcher: ActivityResultLauncher<String>? = null

    @Volatile
    var pending: CompletableDeferred<Boolean>? = null

    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}

/**
 * Receives FCM messages. `notification` messages in the background are shown by FCM itself (default
 * channel `general`, `res/drawable/ic_stat_notification`); everything else is built here. Nothing is
 * shown when the user switched notifications off in the app (PRD R7). Never logs titles, bodies or links.
 */
class TsFirebaseMessagingService : FirebaseMessagingService() {

    private fun manager(): PushManager? = runCatching { GlobalContext.get().get<PushManager>() }.getOrNull()

    override fun onNewToken(token: String) {
        runCatching { (GlobalContext.get().get<PushPlatform>() as? AndroidPushPlatform)?.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = manager() ?: return
        // Read from storage: on a cold process the settings may not be loaded yet (runs off the main thread).
        if (!runCatching { runBlocking { push.showMessagesStored() } }.getOrDefault(false)) return
        val n = message.notification
        val msg = PushMessage.from(n?.title, n?.body, message.data) ?: return
        show(this, msg, message.messageId?.hashCode() ?: msg.hashCode())
    }

    companion object {
        fun show(context: Context, msg: PushMessage, id: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            // Only when missing: re-creating would reset names localized for the in-app language.
            if (Build.VERSION.SDK_INT >= 26 &&
                context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(msg.channel) == null
            ) AndroidPushPlatform.createChannels(context)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // Checked against the allowlist when it is opened, never trusted as an intent.
                .apply { msg.link?.let { putExtra(AndroidPushPlatform.EXTRA_LINK, it) } }
            val pending = PendingIntent.getActivity(
                context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, msg.channel)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(msg.title.ifEmpty { context.getString(R.string.app_name) })
                .setContentText(msg.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg.body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }
}
