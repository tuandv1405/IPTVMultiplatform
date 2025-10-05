package tss.t.tsiptv.core.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.ProgramEntity
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.player.MediaPlayer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service to track channel play history and calculate accurate play time.
 * Only tracks time when the player is actually playing (not paused) and screen is on.
 */
class ChannelHistoryTracker(
    private val database: IPTVDatabase,
    private val scope: CoroutineScope,
    private val mediaPlayer: MediaPlayer,
) {
    private var currentChannel: Channel? = null
    private var currentPlaylistId: String? = null
    private var playStartTime: Long = 0
    private var totalPlayedTime: Long = 0
    private var isPlaying: Boolean = false
    private var isScreenOn: Boolean = true
    private var trackingJob: Job? = null

    /**
     * Called when a channel starts playing.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun onChannelPlay(
        channel: Channel,
        playlistId: String,
        programEntity: ProgramEntity? = null
    ) {
        // Save previous channel's play time if different channel
        if (currentChannel != null && currentChannel?.id != channel.id) {
            saveCurrentPlayTime()
        }

        currentChannel = channel
        currentPlaylistId = playlistId
        playStartTime = Clock.System.now().toEpochMilliseconds()
        totalPlayedTime = 0
        isPlaying = true

        // Record the play event in database
        database.recordChannelPlay(channel.id, playlistId, playStartTime)

        // Start tracking play time
        startPlayTimeTracking()
    }

    /**
     * Called when playback is paused.
     */
    suspend fun onPlaybackPaused() {
        if (isPlaying) {
            saveCurrentPlayTime()
            isPlaying = false
            stopPlayTimeTracking()
        }
    }

    /**
     * Called when playback is resumed.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun onPlaybackResumed() {
        if (!isPlaying && currentChannel != null) {
            playStartTime = Clock.System.now().toEpochMilliseconds()
            isPlaying = true
            startPlayTimeTracking()
        }
    }

    /**
     * Called when playback is stopped.
     */
    suspend fun onPlaybackStopped() {
        if (currentChannel != null) {
            saveCurrentPlayTime()
            currentChannel = null
            currentPlaylistId = null
            isPlaying = false
            stopPlayTimeTracking()
        }
    }

    /**
     * Called when screen state changes.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun onScreenStateChanged(isScreenOn: Boolean) {
        if (this.isScreenOn != isScreenOn) {
            if (isPlaying) {
                if (isScreenOn) {
                    // Screen turned on - resume tracking
                    playStartTime = Clock.System.now().toEpochMilliseconds()
                } else {
                    // Screen turned off - save current time and pause tracking
                    saveCurrentPlayTime()
                }
            }
            this.isScreenOn = isScreenOn
        }
    }

    /**
     * Saves the current play time to the database.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun saveCurrentPlayTime() {
        val channel = currentChannel
        val playlistId = currentPlaylistId

        if (channel != null && playlistId != null && isPlaying && isScreenOn) {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val sessionPlayTime = currentTime - playStartTime

            if (sessionPlayTime > 0) {
                totalPlayedTime += sessionPlayTime
                database.updateChannelPlayTime(
                    channelId = channel.id,
                    playlistId = playlistId,
                    additionalTimeMs = sessionPlayTime,
                    timestamp = currentTime
                )
            }

            // Also update current position and duration
            val currentPosition = mediaPlayer.currentPosition.value
            val totalDuration = mediaPlayer.duration.value

            database.updateChannelPositionAndDuration(
                channelId = channel.id,
                playlistId = playlistId,
                currentPositionMs = currentPosition,
                totalDurationMs = totalDuration,
                timestamp = currentTime
            )
        }
    }

    /**
     * Starts the play time tracking coroutine.
     */
    @OptIn(ExperimentalTime::class)
    private fun startPlayTimeTracking() {
        stopPlayTimeTracking() // Stop any existing tracking

        trackingJob = scope.launch {
            while (isPlaying && currentChannel != null && isScreenOn) {
                delay(TRACKING_INTERVAL_MS)

                // Periodically save play time to avoid losing data
                if (isPlaying && isScreenOn) {
                    saveCurrentPlayTime()
                    playStartTime = Clock.System.now().toEpochMilliseconds()
                }
            }
        }
    }

    /**
     * Stops the play time tracking coroutine.
     */
    private fun stopPlayTimeTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    companion object {
        // Save play time every 30 seconds to avoid losing too much data
        private const val TRACKING_INTERVAL_MS = 30_000L
    }
}
