package tss.t.tsiptv.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URL
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import tss.t.tsiptv.player.network.SafeDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tss.t.tsiptv.MainActivity
import tss.t.tsiptv.R
import tss.t.tsiptv.core.network.SSLTrustAllUtils
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.UUID
import tss.t.tsiptv.player.models.MediaItem as AppMediaItem
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import tss.t.tsiptv.player.network.TrustAllHttpDataSource
import kotlin.math.log

/**
 * Media player service using Media3's MediaSessionService.
 * This implementation follows Google's best practices for media playback and notifications.
 */
@UnstableApi
class MediaPlayerService : MediaSessionService() {

    companion object {
        val globalPlayer by lazy {
            MutableStateFlow<ExoPlayer?>(null)
        }
        private const val CHANNEL_ID = "media_playback_channel"
        private const val CHANNEL_NAME = "Media Playback"
        private const val NOTIFICATION_ID = 1234

        // DRM-related constants
        private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
        private val CLEARKEY_UUID = UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e")

        private var instance: MediaPlayerService? = null
        private var exoPlayer: ExoPlayer? = null
            set(value) {
                globalPlayer.value = value
                field = value
            }

        /**
         * Start the media service with the given media item
         */
        fun startService(context: Context, mediaItem: AppMediaItem) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                putExtra("media_uri", mediaItem.uri)
                putExtra("media_title", mediaItem.title)
                putExtra("media_artist", mediaItem.artist)
                putExtra("media_artwork_uri", mediaItem.artworkUri)
                putExtra("drm_license_key", mediaItem.drmLicenseKey)
            }
            context.startService(intent)
        }

        /**
         * Stop the media service
         */
        fun stopService(context: Context) {
            val intent = Intent(context, MediaPlayerService::class.java)
            context.stopService(intent)
        }


        /**
         * Get the ExoPlayer instance from the service
         */
        fun getExoPlayer(): ExoPlayer? = globalPlayer.value

        /**
         * Play the current media
         */
        fun play() {
            exoPlayer?.play()
        }

        /**
         * Pause the current media
         */
        fun pause() {
            exoPlayer?.pause()
        }

        /**
         * Stop the current media
         */
        fun stop() {
            exoPlayer?.stop()
        }

        /**
         * Seek to a specific position
         */
        fun seekTo(positionMs: Long) {
            exoPlayer?.seekTo(positionMs)
        }

        /**
         * Set the playback speed
         */
        fun setPlaybackSpeed(speed: Float) {
            exoPlayer?.setPlaybackSpeed(speed)
        }

        /**
         * Set the volume level (0.0 to 1.0)
         */
        fun setVolume(volume: Float) {
            exoPlayer?.volume = volume.coerceIn(0f, 1f)
        }

        /**
         * Mute or unmute the player
         */
        fun setMuted(muted: Boolean) {
            exoPlayer?.let { player ->
                if (muted) {
                    player.volume = 0f
                } else {
                    player.volume = 1f
                }
            }
        }

        /**
         * Get the current volume level
         */
        fun getVolume(): Float {
            return exoPlayer?.volume ?: 1f
        }

        /**
         * Check if the player is muted
         */
        fun isMuted(): Boolean {
            return exoPlayer?.volume == 0f
        }
    }

    private var startForeground = false
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Rebuilt per channel so each play gets the right SafeDrmCallback (inline key or URL)
    private var currentDrmSessionManager: DrmSessionManager = DrmSessionManager.DRM_UNSUPPORTED

    private lateinit var mediaSession: MediaSession
    private lateinit var player: ExoPlayer
    private var isLiveStream = false
    private var currentMediaTitle: String? = null
    private var currentMediaArtist: String? = null
    private var currentArtworkUri: String? = null
    private var cachedArtworkBitmap: Bitmap? = null
    private var lastLoadedArtworkUri: String? = null
    private lateinit var playerNotificationManager: PlayerNotificationManager

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Create notification channel for Android O and above
        createNotificationChannel()

        // Install global SSL trust all configuration
        installTrustAllSSLConfig()

        // Create the player with appropriate audio attributes and DRM support
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Create media source factory — DRM provider delegates to currentDrmSessionManager
        // so we can swap it per channel without rebuilding the factory.
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDrmSessionManagerProvider { currentDrmSessionManager }
            .setDataSourceFactory(TrustAllHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true))

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // Add player listener to update playback state
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                onUpdateNotification(mediaSession, true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onUpdateNotification(mediaSession, true)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // Update media metadata for notification
                currentMediaTitle = mediaMetadata.title?.toString()
                currentMediaArtist = mediaMetadata.artist?.toString()

                // Check if this is a live stream
                val mediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                if (mediaUri != null) {
                    isLiveStream = mediaUri.endsWith(".m3u8") ||
                            mediaUri.contains("live")
                }
            }
        })

        // Store the player instance for external access
        exoPlayer = player

        // Create the media session with session activity
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(getSessionActivityPendingIntent())
            .build()

        addSession(mediaSession)

        // Initialize the PlayerNotificationManager
        setupNotificationManager()
    }

    private fun setupNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        ).setPlayActionIconResourceId(R.drawable.ic_play_circle)
            .setPauseActionIconResourceId(R.drawable.ic_pause)
            .setNextActionIconResourceId(R.drawable.ic_next)
            .setPreviousActionIconResourceId(R.drawable.ic_previous)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return currentMediaTitle ?: "Unknown Title"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    return getSessionActivityPendingIntent()
                }

                override fun getCurrentContentText(player: Player): CharSequence? {
                    return currentMediaArtist ?: "Unknown Artist"
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback,
                ): Bitmap? {
                    // Get the current artwork URI
                    val artworkUri = currentArtworkUri

                    // If we have a cached bitmap and the URI hasn't changed, return the cached bitmap
                    if (cachedArtworkBitmap != null && artworkUri == lastLoadedArtworkUri) {
                        Log.d(
                            "MediaPlayerService",
                            "Using cached artwork bitmap for URI: $artworkUri"
                        )
                        return cachedArtworkBitmap
                    }

                    // If we have a new URI, load the bitmap
                    if (artworkUri != null) {
                        android.util.Log.d(
                            "MediaPlayerService",
                            "Loading new artwork bitmap for URI: $artworkUri"
                        )

                        // Update the last loaded URI
                        lastLoadedArtworkUri = artworkUri

                        // Load bitmap in a coroutine to avoid blocking the main thread
                        serviceScope.launch(Dispatchers.IO) {
                            val bitmap = loadBitmapFromUrl(artworkUri)
                            if (bitmap != null) {
                                Log.d(
                                    "MediaPlayerService",
                                    "Successfully loaded artwork bitmap for URI: $artworkUri"
                                )

                                // Cache the bitmap
                                cachedArtworkBitmap = bitmap

                                // Notify the callback when the bitmap is loaded
                                callback.onBitmap(bitmap)
                            } else {
                                Log.e(
                                    "MediaPlayerService",
                                    "Failed to load artwork bitmap for URI: $artworkUri"
                                )
                            }
                        }
                    }

                    // Return the cached bitmap if available, otherwise null
                    // The callback will be used when a new bitmap is loaded
                    return cachedArtworkBitmap
                }
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean,
                ) {
                    Log.d("TuanDV", "onNotificationPosted: $ongoing")
                    if (ongoing && !startForeground) {
                        startForeground(notificationId, notification)
                        startForeground = true
                    }
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean,
                ) {
                    startForeground = false
                    Log.d("TuanDV", "stopSelf")
                    player?.stop()
                }

            })
            .build()

        playerNotificationManager.setPlayer(player)
        playerNotificationManager.setUseNextAction(true)
        playerNotificationManager.setUsePreviousAction(true)
        playerNotificationManager.setUseStopAction(false)
        playerNotificationManager.setUseFastForwardAction(false)
        playerNotificationManager.setUseRewindAction(false)
        playerNotificationManager.setColorized(true)
        playerNotificationManager.setColor("#03041D".toColorInt())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)

        // Extract media information from intent
        if (intent == null) {
            return START_STICKY
        }

        val mediaUri = intent.getStringExtra("media_uri")
        if (mediaUri == null) {
            return result
        }

        val mediaTitle = intent.getStringExtra("media_title") ?: "Unknown Title"
        val mediaArtist = intent.getStringExtra("media_artist") ?: "Unknown Artist"
        val artworkUri = intent.getStringExtra("media_artwork_uri")
        val drmLicenseKey = intent.getStringExtra("drm_license_key")

        // Store media info for notification
        currentMediaTitle = mediaTitle
        currentMediaArtist = mediaArtist

        // Check if artwork URI has changed
        if (currentArtworkUri != artworkUri) {
            // Clear cached bitmap if artwork URI has changed
            cachedArtworkBitmap = null
            currentArtworkUri = artworkUri
        }

        // Check if this is a live stream (m3u8)
        isLiveStream = mediaUri.endsWith(".m3u8") || mediaUri.contains("live")

        // Create Media3 MediaItem with metadata
        val metadata = MediaMetadata.Builder()
            .setTitle(mediaTitle)
            .setArtist(mediaArtist)
            .setArtworkUri(artworkUri?.toUri())
            .setDisplayTitle(mediaTitle)
            .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
            .build()

        // Explicitly set MIME type so ExoPlayer uses the correct parser
        // (e.g. DASH for .mpd, HLS for .m3u8) instead of guessing from the URI
        val mimeType = when {
            mediaUri.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            mediaUri.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            else -> null
        }

        // Build the media item
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaId(mediaUri)
            .setMediaMetadata(metadata)
            .apply { mimeType?.let { setMimeType(it) } }

        // Build a SafeDrmCallback and DrmSessionManager appropriate for this channel,
        // then expose it through the factory's provider before calling setMediaItem.
        currentDrmSessionManager = when {
            // Inline ClearKey: "kid_hex:key_hex" — 32-char hex pair separated by ':'
            !drmLicenseKey.isNullOrEmpty() && isClearKeyFormat(drmLicenseKey) -> {
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(CLEARKEY_UUID).build()
                )
                createDrmSessionManager(clearKeyHex = drmLicenseKey, uuid = CLEARKEY_UUID)
            }
            // Widevine with explicit license URL
            !drmLicenseKey.isNullOrEmpty() && drmLicenseKey.startsWith("http", ignoreCase = true) -> {
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(WIDEVINE_UUID)
                        .setLicenseUri(drmLicenseKey)
                        .build()
                )
                createDrmSessionManager(overrideLicenseUrl = drmLicenseKey, uuid = WIDEVINE_UUID)
            }
            // No explicit key — SafeDrmCallback will use the URL from the manifest
            else -> createDrmSessionManager(uuid = WIDEVINE_UUID)
        }

        val mediaItem = mediaItemBuilder.build()

        // Set the media item to the player
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Release the player and session
        mediaSession.release()
        player.release()
        exoPlayer = null
        instance = null

        Log.d("TuanDV", "onDestroy: ")

        // Release cached bitmap
        if (cachedArtworkBitmap != null && !cachedArtworkBitmap!!.isRecycled) {
            cachedArtworkBitmap!!.recycle()
            cachedArtworkBitmap = null
        }

        // Cancel coroutines
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.mediaItemCount == 0) {
            // Only stop the service if there's no media
            Log.d("TuanDV", "stopSelf - no media items")
            stopSelf()
        } else {
            // Keep the service running even if playback is paused
            Log.d("TuanDV", "keeping service running after app closed")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun isPlaybackOngoing(): Boolean {
        val isPlaybackOngoing = super.isPlaybackOngoing()
        Log.d("TuanDV", "super.isPlaybackOngoing: $isPlaybackOngoing")
        return true
    }

    private fun getSessionActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "TS IPTV"
                setShowBadge(false)
            }

            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Installs a global SSL trust all configuration.
     * WARNING: This should only be used for development or in very specific cases
     * where certificate validation is not required. Using this in production
     * can lead to security vulnerabilities.
     */
    private fun installTrustAllSSLConfig() {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(SSLTrustAllUtils.createTrustAllTrustManager())

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Set the default SSL socket factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)

            // Set the default hostname verifier
            HttpsURLConnection.setDefaultHostnameVerifier(SSLTrustAllUtils.trustAllHostnameVerifier)

            Log.d("MediaPlayerService", "Installed trust all SSL configuration")
        } catch (e: Exception) {
            Log.e("MediaPlayerService", "Error installing trust all SSL configuration", e)
        }
    }

    /**
     * Creates a per-channel [DrmSessionManager] backed by [SafeDrmCallback].
     *
     * @param clearKeyHex  Inline ClearKey in "kid_hex:key_hex" format — callback returns
     *                     the JSON license directly without a network request.
     * @param overrideLicenseUrl  Widevine license URL when the manifest omits one.
     * @param uuid         The DRM system UUID ([CLEARKEY_UUID] or [WIDEVINE_UUID]).
     */
    private fun createDrmSessionManager(
        clearKeyHex: String? = null,
        overrideLicenseUrl: String? = null,
        uuid: UUID = WIDEVINE_UUID,
    ): DrmSessionManager {
        val dataSourceFactory = TrustAllHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val drmCallback = SafeDrmCallback(
            clearKeyHex = clearKeyHex,
            overrideLicenseUrl = overrideLicenseUrl,
            dataSourceFactory = dataSourceFactory,
        )

        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(false)
            .build(drmCallback)
    }

    /**
     * Returns true when [key] looks like an inline ClearKey pair:
     * two colon-separated hex strings of exactly 32 characters each (128-bit).
     * Example: "a7c942778e874d43be92b8d0a0cd11b4:6d54358306571658ffdb952c6560688b"
     */
    private fun isClearKeyFormat(key: String): Boolean {
        val colon = key.indexOf(':')
        if (colon != 32 || key.length != 65) return false
        return key.replace(":", "").all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * Loads a bitmap from a URL.
     * This is used to load artwork for the notification.
     *
     * @param url The URL to load the bitmap from
     * @return The loaded bitmap, or null if loading failed
     */
    private fun loadBitmapFromUrl(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            val inputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            bitmap
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Formats time in seconds to a string in the format MM:SS or HH:MM:SS
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
