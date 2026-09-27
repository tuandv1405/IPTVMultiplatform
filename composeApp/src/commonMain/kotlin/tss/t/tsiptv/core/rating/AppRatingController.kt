package tss.t.tsiptv.core.rating

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Decides when to ask for a rating and forwards the ask to the platform.
 *
 * Plays are counted once per media item, the first time it reaches PLAYING.
 * The prompt is only requested from [maybePrompt], which the app calls when the
 * user comes back from the player, never while something is being watched.
 */
@OptIn(ExperimentalTime::class)
class AppRatingController(
    private val repository: AppRatingRepository,
    private val platform: AppReviewPlatform,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private var lastCountedMediaId: String? = null

    val canOpenStoreListing: Boolean get() = platform.canOpenStoreListing

    suspend fun onAppStarted() {
        repository.recordFirstLaunchIfNeeded(now())
    }

    suspend fun onPlaybackStarted(mediaId: String) {
        if (mediaId.isEmpty()) return
        mutex.withLock {
            // PLAYING fires again after every pause and buffer; one item is one play.
            if (mediaId == lastCountedMediaId) return
            lastCountedMediaId = mediaId
            repository.recordPlay()
        }
    }

    /** Returns true when the review flow was requested. */
    suspend fun maybePrompt(): Boolean {
        val shouldAsk = mutex.withLock {
            val at = now()
            if (!AppRatingPolicy.shouldPrompt(repository.getState(), at)) {
                false
            } else {
                // Recorded before asking: if the store's quota swallows the card we
                // must not retry on every return to Home.
                repository.recordPrompt(at)
                true
            }
        }
        return shouldAsk && platform.requestInAppReview()
    }

    /** The "Rate TS IPTV" button. */
    suspend fun openStoreListing(): Boolean {
        repository.recordManualRate(now())
        return platform.openStoreListing()
    }
}
