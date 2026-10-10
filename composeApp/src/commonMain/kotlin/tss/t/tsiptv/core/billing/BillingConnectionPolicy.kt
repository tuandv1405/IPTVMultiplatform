package tss.t.tsiptv.core.billing

/**
 * When the Play Billing client may (re)connect and query (pure; the Android client drives it).
 *
 * - Failed connections and lost services back off exponentially: [initialDelayMs], doubling up to
 *   [maxDelayMs]; a successful connection resets it.
 * - Nothing is attempted while waiting for the next try (no polling while disconnected).
 * - "Billing unavailable" (no Play Store or no account) stops all attempts until the next app start
 *   or a user action ([onUserAction]: Restore, a purchase).
 * - Activity resumes query at most once per [resumeQueryIntervalMs], and only while connected.
 */
class BillingConnectionPolicy(
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 5 * 60_000L,
    private val resumeQueryIntervalMs: Long = 60_000L,
) {
    enum class State { IDLE, CONNECTING, CONNECTED, WAITING, UNAVAILABLE }

    var state: State = State.IDLE
        private set

    /** Consecutive failures since the last successful connection. */
    var failures: Int = 0
        private set

    /** Elapsed-realtime of the next allowed attempt while [State.WAITING]. */
    var nextAttemptAtMs: Long = 0L
        private set

    private var lastResumeQueryMs: Long? = null

    /** The wait after [failures] consecutive failures (0 → initial). */
    fun delayAfter(failures: Int): Long {
        var d = initialDelayMs
        repeat(failures.coerceAtMost(40)) {
            d *= 2
            if (d >= maxDelayMs) return maxDelayMs
        }
        return d.coerceAtMost(maxDelayMs)
    }

    /** Whether a connection attempt may start now (no attempt while waiting or unavailable). */
    fun mayConnect(nowMs: Long): Boolean = when (state) {
        State.IDLE -> true
        State.WAITING -> nowMs >= nextAttemptAtMs
        State.CONNECTING, State.CONNECTED, State.UNAVAILABLE -> false
    }

    fun onConnecting() {
        state = State.CONNECTING
    }

    fun onConnected() {
        state = State.CONNECTED
        failures = 0
        nextAttemptAtMs = 0L
    }

    /** A failed attempt or a lost service. Returns the delay until the next attempt. */
    fun onFailure(nowMs: Long): Long {
        val delay = delayAfter(failures)
        failures++
        state = State.WAITING
        nextAttemptAtMs = nowMs + delay
        return delay
    }

    /** Play says billing cannot work here: stop until the next start or a user action. */
    fun onUnavailable() {
        state = State.UNAVAILABLE
    }

    /** Restore or a purchase: try again at once, whatever the backoff or "unavailable" said. */
    fun onUserAction() {
        if (state == State.WAITING || state == State.UNAVAILABLE) state = State.IDLE
        failures = 0
        nextAttemptAtMs = 0L
    }

    /** An activity resumed: query only when connected, at most once per interval. */
    fun shouldQueryOnResume(nowMs: Long): Boolean {
        if (state != State.CONNECTED) return false
        val last = lastResumeQueryMs
        if (last != null && nowMs - last < resumeQueryIntervalMs) return false
        lastResumeQueryMs = nowMs
        return true
    }
}
