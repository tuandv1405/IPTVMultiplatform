package tss.t.tsiptv.core.rating

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppRatingControllerTest {

    private class FakePlatform(var requestSucceeds: Boolean = true) : AppReviewPlatform {
        var requests = 0
        var listingOpens = 0
        override val canOpenStoreListing = true
        override suspend fun requestInAppReview(): Boolean {
            requests++
            return requestSucceeds
        }

        override suspend fun openStoreListing(): Boolean {
            listingOpens++
            return true
        }
    }

    private class Clock(var now: Long = START)

    private val day = 24L * 60 * 60 * 1000

    private fun controller(
        platform: FakePlatform = FakePlatform(),
        clock: Clock = Clock(),
        repository: AppRatingRepository = AppRatingRepository(InMemoryKeyValueStorage()),
    ) = AppRatingController(repository, platform) { clock.now }

    private suspend fun AppRatingController.play(times: Int) {
        repeat(times) { onPlaybackStarted("channel-$it") }
    }

    @Test
    fun freshInstallNeverPromptsOnDayZero() = runBlocking {
        val platform = FakePlatform()
        val rating = controller(platform)
        rating.onAppStarted()
        rating.play(50)

        assertFalse(rating.maybePrompt())
        assertEquals(0, platform.requests)
    }

    @Test
    fun promptsOnceAfterThreeDaysAndFivePlays() = runBlocking {
        val platform = FakePlatform()
        val clock = Clock()
        val rating = controller(platform, clock)
        rating.onAppStarted()
        rating.play(5)
        clock.now += 3 * day

        assertTrue(rating.maybePrompt())
        assertFalse(rating.maybePrompt(), "the very next return to Home must not ask again")
        assertEquals(1, platform.requests)
    }

    @Test
    fun fourPlaysAreNotEnough() = runBlocking {
        val clock = Clock()
        val rating = controller(clock = clock)
        rating.onAppStarted()
        rating.play(4)
        clock.now += 10 * day

        assertFalse(rating.maybePrompt())
    }

    @Test
    fun pausingAndResumingTheSameChannelIsOnePlay() = runBlocking {
        val clock = Clock()
        val rating = controller(clock = clock)
        rating.onAppStarted()
        repeat(10) { rating.onPlaybackStarted("same-channel") }
        rating.onPlaybackStarted("")
        clock.now += 10 * day

        assertFalse(rating.maybePrompt())
    }

    @Test
    fun waits120DaysBetweenPromptsAndStopsAfterThree() = runBlocking {
        val platform = FakePlatform()
        val clock = Clock()
        val rating = controller(platform, clock)
        rating.onAppStarted()
        rating.play(5)
        clock.now += 3 * day

        assertTrue(rating.maybePrompt())
        clock.now += 119 * day
        assertFalse(rating.maybePrompt())
        clock.now += 1 * day
        assertTrue(rating.maybePrompt())
        clock.now += 120 * day
        assertTrue(rating.maybePrompt())
        clock.now += 365 * day
        assertFalse(rating.maybePrompt(), "three prompts is the lifetime maximum")
        assertEquals(3, platform.requests)
    }

    @Test
    fun aSwallowedPromptStillCountsSoItIsNotRetried() = runBlocking {
        val platform = FakePlatform(requestSucceeds = false)
        val clock = Clock()
        val rating = controller(platform, clock)
        rating.onAppStarted()
        rating.play(5)
        clock.now += 3 * day

        assertFalse(rating.maybePrompt())
        assertFalse(rating.maybePrompt())
        assertEquals(1, platform.requests)
    }

    @Test
    fun rateButtonOpensListingAndSuppressesTheAutomaticPrompt() = runBlocking {
        val platform = FakePlatform()
        val clock = Clock()
        val rating = controller(platform, clock)
        rating.onAppStarted()
        rating.play(5)
        clock.now += 3 * day

        assertTrue(rating.openStoreListing())
        assertEquals(1, platform.listingOpens)
        assertFalse(rating.maybePrompt())

        clock.now += 120 * day
        assertTrue(rating.maybePrompt())
    }

    @Test
    fun firstLaunchIsRecordedOnlyOnce() = runBlocking {
        val repository = AppRatingRepository(InMemoryKeyValueStorage())
        val clock = Clock()
        val rating = controller(clock = clock, repository = repository)
        rating.onAppStarted()
        clock.now += 5 * day
        rating.onAppStarted()

        assertEquals(START, repository.getState().firstLaunchAt)
    }

    @Test
    fun stateSurvivesANewController() = runBlocking {
        val storage = InMemoryKeyValueStorage()
        val clock = Clock()
        val first = controller(clock = clock, repository = AppRatingRepository(storage))
        first.onAppStarted()
        first.play(5)
        clock.now += 3 * day

        // Same storage, new process: the play count carried over.
        val platform = FakePlatform()
        val second = controller(platform, clock, AppRatingRepository(storage))
        assertTrue(second.maybePrompt())
    }

    @Test
    fun policyRejectsAMissingFirstLaunch() {
        assertFalse(
            AppRatingPolicy.shouldPrompt(AppRatingState(playCount = 100), now = START + 100 * day)
        )
    }

    private companion object {
        const val START = 1_790_000_000_000L
    }
}
