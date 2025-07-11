package tss.t.tsiptv.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tss.t.tsiptv.player.MediaItem

/**
 * Foreground service for media playback using Media3 ExoPlayer.
 * This service allows playback to continue when the app is in the background.
 */
class MediaPlayerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "media_playback_channel"
        private const val CHANNEL_NAME = "Media Playback"
        
        private var instance: MediaPlayerService? = null
        private var exoPlayer: ExoPlayer? = null
        
        /**
         * Start the media service with the given media item
         */
        fun startService(context: Context, mediaItem: MediaItem) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                putExtra("media_uri", mediaItem.uri)
                putExtra("media_title", mediaItem.title)
                putExtra("media_artist", mediaItem.artist)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
        fun getExoPlayer(): ExoPlayer? = exoPlayer
        
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
    }
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Create the player
        exoPlayer = ExoPlayer.Builder(this).build()
        
        // Create notification channel for Android O and above
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extract media information from intent
        val mediaUri = intent?.getStringExtra("media_uri") ?: return START_NOT_STICKY
        val mediaTitle = intent.getStringExtra("media_title") ?: "Unknown Title"
        val mediaArtist = intent.getStringExtra("media_artist") ?: "Unknown Artist"
        
        // Create Media3 MediaItem
        val mediaItem = Media3MediaItem.fromUri(mediaUri)
        
        // Set the media item to the player
        exoPlayer?.let { player ->
            player.setMediaItem(mediaItem)
            player.prepare()
            
            // Create and display notification
            val notification = createNotification(mediaTitle, mediaArtist)
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        // Release the player
        exoPlayer?.release()
        exoPlayer = null
        instance = null
        
        // Cancel coroutines
        serviceScope.cancel()
        
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, artist: String): Notification {
        // Create a basic notification with media controls
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        
        // Add media control actions
        // Play/Pause action
        val playPauseIntent = Intent(this, MediaPlayerService::class.java).apply {
            action = if (exoPlayer?.isPlaying == true) "PAUSE" else "PLAY"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseIcon = if (exoPlayer?.isPlaying == true) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        
        builder.addAction(
            playPauseIcon,
            if (exoPlayer?.isPlaying == true) "Pause" else "Play",
            playPausePendingIntent
        )
        
        return builder.build()
    }
}