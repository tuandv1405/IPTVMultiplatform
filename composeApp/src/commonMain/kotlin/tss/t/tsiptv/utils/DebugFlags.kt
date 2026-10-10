package tss.t.tsiptv.utils

/**
 * QA switches that exist only in debug builds (set by platform start-up code from Gradle `-P`
 * properties). Always false in release.
 */
object DebugFlags {
    /** `-Ptsiptv.debugSkipLogin=true`: the phone layout works signed out (like the TV layout). */
    var skipLogin: Boolean = false
}
