package tss.t.tsiptv.core.rating

/**
 * What the app remembers about asking for a rating. All times are epoch millis;
 * 0 means "never happened".
 */
data class AppRatingState(
    val firstLaunchAt: Long = 0L,
    val playCount: Int = 0,
    val lastPromptAt: Long = 0L,
    val promptCount: Int = 0,
    val lastManualRateAt: Long = 0L,
)

/**
 * When the automatic review prompt may be shown. See docs/prd-rate-app.md.
 *
 * Play and the App Store both apply their own quota on top of this and may show
 * nothing; these rules only keep the app from asking too early or too often.
 */
object AppRatingPolicy {
    const val MIN_DAYS_SINCE_FIRST_LAUNCH = 3
    const val MIN_PLAYS = 5
    const val DAYS_BETWEEN_PROMPTS = 120
    const val MAX_PROMPTS = 3
    const val DAYS_AFTER_MANUAL_RATE = 120

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun shouldPrompt(state: AppRatingState, now: Long): Boolean {
        if (state.firstLaunchAt == 0L) return false
        if (now - state.firstLaunchAt < MIN_DAYS_SINCE_FIRST_LAUNCH * DAY_MILLIS) return false
        if (state.playCount < MIN_PLAYS) return false
        if (state.promptCount >= MAX_PROMPTS) return false
        if (state.lastPromptAt != 0L &&
            now - state.lastPromptAt < DAYS_BETWEEN_PROMPTS * DAY_MILLIS
        ) return false
        if (state.lastManualRateAt != 0L &&
            now - state.lastManualRateAt < DAYS_AFTER_MANUAL_RATE * DAY_MILLIS
        ) return false
        return true
    }
}
