package tss.t.tsiptv.player.models

/**
 * Represents the current state of media playback
 */
enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}