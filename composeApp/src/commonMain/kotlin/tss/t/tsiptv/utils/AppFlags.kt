package tss.t.tsiptv.utils

/** Start-up switches a platform sets before the UI starts. Unlike [DebugFlags], these ship in release. */
object AppFlags {
    /**
     * This platform has no working account system (desktop: Firebase Auth is in memory only), so the
     * app works signed out, as the TV layout does: playlists and playback are local.
     */
    var worksSignedOut: Boolean = false

    /** Firebase Analytics works here (desktop: no, gitlive's JVM analytics throws). See AppAnalytics. */
    var analyticsAvailable: Boolean = true
}
