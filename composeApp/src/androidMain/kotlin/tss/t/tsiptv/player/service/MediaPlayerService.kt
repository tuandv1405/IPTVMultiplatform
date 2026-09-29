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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.clearKeyJwks
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
import tss.t.tsiptv.player.models.MediaItem as AppMediaItem
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import tss.t.tsiptv.player.network.playerHttpDataSourceFactory
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
        private const val EXTRA_MEDIA_ITEM = "media_item_json"
        private const val EXTRA_SEQUENCE = "start_sequence"

        /**
         * Sequence of the latest start request. onStartCommand runs asynchronously: when the user zaps
         * fast, an older intent (channel A) must not start after a newer request (B, or a refusal).
         */
        private val latestSequence = java.util.concurrent.atomic.AtomicLong(0)

        /** Makes every start intent still in flight stale (e.g. the new item was refused). */
        fun invalidatePendingStarts() {
            latestSequence.incrementAndGet()
        }
        private const val USER_AGENT = "User-Agent"

        /** Ids of items the service could not even build a MediaSource for. */
        val sourceFailures = MutableSharedFlow<String>(extraBufferCapacity = 1)

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
            // The whole item, headers and DRM included: the service builds the channel's own
            // data source from it. The intent is explicit, so the extras stay in the app.
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                putExtra(EXTRA_MEDIA_ITEM, Json.encodeToString(AppMediaItem.serializer(), mediaItem))
                putExtra(EXTRA_SEQUENCE, latestSequence.incrementAndGet())
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

        // Items from onStartCommand get their own MediaSource (channel headers and DRM, see
        // createMediaSource); this default covers anything a session controller sets.
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(playerHttpDataSourceFactory())

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
                    // Never log artwork URLs: addon and playlist links can carry tokens.
                    if (cachedArtworkBitmap != null && artworkUri == lastLoadedArtworkUri) {
                        return cachedArtworkBitmap
                    }

                    // If we have a new URI, load the bitmap
                    if (artworkUri != null) {
                        // Update the last loaded URI
                        lastLoadedArtworkUri = artworkUri

                        // Load bitmap in a coroutine to avoid blocking the main thread
                        serviceScope.launch(Dispatchers.IO) {
                            val bitmap = loadBitmapFromUrl(artworkUri)
                            if (bitmap != null) {
                                // Cache the bitmap
                                cachedArtworkBitmap = bitmap

                                // Notify the callback when the bitmap is loaded
                                callback.onBitmap(bitmap)
                            } else {
                                Log.w("MediaPlayerService", "Artwork could not be loaded (host: ${runCatching { java.net.URI(artworkUri).host }.getOrNull()})")
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
        // A newer start (or a refusal) was requested after this intent: ignore it.
        val sequence = intent.getLongExtra(EXTRA_SEQUENCE, -1L)
        if (sequence != -1L && sequence != latestSequence.get()) {
            return START_STICKY
        }

        val appItem = intent.getStringExtra(EXTRA_MEDIA_ITEM)
            ?.let { runCatching { Json.decodeFromString(AppMediaItem.serializer(), it) }.getOrNull() }
            ?: return result
        val mediaUri = appItem.uri

        val mediaTitle = appItem.title.ifEmpty { "Unknown Title" }
        val mediaArtist = appItem.artist.ifEmpty { "Unknown Artist" }
        val artworkUri = appItem.artworkUri

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
            .setMediaType(
                if (appItem.isRadio) MediaMetadata.MEDIA_TYPE_RADIO_STATION else MediaMetadata.MEDIA_TYPE_VIDEO
            )
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaId(appItem.id.ifEmpty { mediaUri })
            .setMediaMetadata(metadata)
        appItem.mimeType?.let { mediaItemBuilder.setMimeType(it) }
        // No DrmSpec, no DRM configuration: guessing from the URL sent licence requests for
        // clear streams. HLS AES-128 needs none; Media3 handles #EXT-X-KEY itself.
        drmConfiguration(appItem.drm)?.let { mediaItemBuilder.setDrmConfiguration(it) }
        // F3: side-loaded WebVTT / SubRip subtitles (TS IPTV Source movies and episodes). They are
        // fetched with the item's headers by the same data source factory.
        if (appItem.subtitles.isNotEmpty()) {
            mediaItemBuilder.setSubtitleConfigurations(
                appItem.subtitles.map { track ->
                    MediaItem.SubtitleConfiguration.Builder(track.url.toUri())
                        .setMimeType(
                            if (track.mimeType == tss.t.tsiptv.player.models.SubtitleTrack.MIME_SRT) {
                                androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                            } else androidx.media3.common.MimeTypes.TEXT_VTT
                        )
                        .setLanguage(track.language)
                        .setLabel(track.label ?: track.language)
                        // Not selected by default (the spec asks for no preselection): Off until
                        // the viewer picks one in the player's track menu.
                        .setSelectionFlags(0)
                        .build()
                }
            )
        }

        val source = try {
            createMediaSource(mediaItemBuilder.build(), appItem)
        } catch (e: Exception) {
            // An item Media3 cannot build a source for (unknown container, bad DRM data) must not
            // take the service down with it. Only the type is logged: the URI may hold a token.
            Log.e("MediaPlayerService", "Cannot create media source: ${e.javaClass.simpleName}")
            player.stop()
            player.clearMediaItems()
            sourceFailures.tryEmit(appItem.id)
            return START_STICKY
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true

        return START_STICKY
    }

    /**
     * A MediaSource for this item only: its headers reach the manifest, variant, segment and
     * key requests and the licence server (some check User-Agent / Referer), and nothing
     * carries over to the next channel.
     */
    private fun createMediaSource(mediaItem: MediaItem, appItem: AppMediaItem): MediaSource {
        val dataSourceFactory = playerHttpDataSourceFactory(appItem.headers)
        val drm = appItem.drm
        val drmProvider: DrmSessionManagerProvider =
            if (drm?.system == DrmSystem.CLEARKEY && drm.clearKeys.isNotEmpty()) {
                // Keys from the playlist: answer the CDM's key request locally.
                val manager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(LocalMediaDrmCallback(drm.clearKeyJwks().toByteArray(Charsets.UTF_8)))
                DrmSessionManagerProvider { manager }
            } else {
                // OkHttpDataSource sends its own user agent and would add a second one from
                // the request properties, so a licence User-Agent goes into the factory instead.
                val licenseUserAgent = drm?.licenseHeaders?.entries
                    ?.firstOrNull { it.key.equals(USER_AGENT, ignoreCase = true) }?.value
                val licenseFactory = if (licenseUserAgent == null) {
                    dataSourceFactory
                } else {
                    playerHttpDataSourceFactory(
                        appItem.headers.filterKeys { !it.equals(USER_AGENT, ignoreCase = true) } +
                                (USER_AGENT to licenseUserAgent)
                    )
                }
                DefaultDrmSessionManagerProvider().apply { setDrmHttpDataSourceFactory(licenseFactory) }
            }
        // Subtitle files never get the stream's headers (they may carry tokens; spec §12).
        val subtitleUris = appItem.subtitles.map { it.url.toUri().toString() }.toSet()
        val sourceFactory = if (subtitleUris.isEmpty()) dataSourceFactory
        else tss.t.tsiptv.player.network.SubtitleAwareDataSourceFactory(dataSourceFactory, playerHttpDataSourceFactory(), subtitleUris)
        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(sourceFactory)
            .setDrmSessionManagerProvider(drmProvider)
            .createMediaSource(mediaItem)
    }

    /** Preflight has already refused unsupported specs; this only maps supported ones. */
    private fun drmConfiguration(drm: DrmSpec?): MediaItem.DrmConfiguration? {
        if (drm == null || !drm.isSupported) return null
        val scheme = when (drm.system) {
            DrmSystem.WIDEVINE -> C.WIDEVINE_UUID
            DrmSystem.PLAYREADY -> C.PLAYREADY_UUID
            DrmSystem.CLEARKEY -> C.CLEARKEY_UUID
            null -> return null
        }
        return MediaItem.DrmConfiguration.Builder(scheme).apply {
            drm.licenseUrl?.let {
                setLicenseUri(it)
                // The playlist's licence server overrides one named in the manifest, as in Kodi.
                setForceDefaultLicenseUri(true)
            }
            setLicenseRequestHeaders(drm.licenseHeaders.filterKeys { !it.equals(USER_AGENT, ignoreCase = true) })
        }.build()
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
        } catch (e: Exception) {
            // Type only: the exception message holds the artwork URL (FileNotFoundException(<url>)).
            Log.w("MediaPlayerService", "Artwork download failed: ${e.javaClass.simpleName}")
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
