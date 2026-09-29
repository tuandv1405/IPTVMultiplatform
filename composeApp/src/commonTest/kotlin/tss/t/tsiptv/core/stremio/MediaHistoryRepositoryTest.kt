package tss.t.tsiptv.core.stremio

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaHistoryRepositoryTest {
    private var now = 1_000L
    private val stores = InMemoryStremioStores()
    private val repo = MediaHistoryRepository(stores, nowMs = { now })

    private fun movie() = MediaPlaybackContext(
        sourceAddonId = "a", itemType = "movie", itemId = "m", videoId = "m", title = "Movie", subtitle = null,
        posterUrl = "p", season = null, episode = null, streamAddonId = "s", bingeGroup = "g", isLive = false,
    )

    private fun episode(next: NextEpisodeInfo?) = MediaPlaybackContext(
        sourceAddonId = "a", itemType = "series", itemId = "tt1", videoId = "tt1:1:1", title = "Show", subtitle = "S1 · E1 Pilot",
        posterUrl = null, season = 1, episode = 1, streamAddonId = "s", bingeGroup = "g", isLive = false, next = next,
    )

    @Test
    fun acceptanceS20ContinueWatchingAndFinish() = runBlocking {
        repo.saveProgress(movie(), 120_000, 5_400_000)
        val cw = repo.continueWatching.first().single()
        assertEquals(120_000, cw.positionMs)
        assertEquals("s", cw.lastAddonId)
        assertEquals("g", cw.lastBingeGroup)
        assertEquals(120_000, MediaRules.resumePosition(cw.positionMs, cw.durationMs))

        now += 10_000
        repo.saveProgress(movie(), 5_200_000, 5_400_000)
        assertTrue(repo.continueWatching.first().isEmpty())
        assertEquals(1, repo.history.first().size) // one row per video, updated in place
    }

    @Test
    fun finishedEpisodeIsReplacedByTheNextOne() = runBlocking {
        repo.saveProgress(episode(NextEpisodeInfo("tt1:1:2", "S1 · E2 Second", 1, 2)), 300_000, 1_300_000)
        now += 10_000
        repo.saveProgress(episode(NextEpisodeInfo("tt1:1:2", "S1 · E2 Second", 1, 2)), 1_290_000, 1_300_000)
        val cw = repo.continueWatching.first().single()
        assertEquals("tt1:1:2", cw.videoId)
        assertEquals(0, cw.positionMs)
        assertEquals(MediaHistoryRepository.QUEUED_NEXT, cw.durationMs)
        assertEquals("S1 · E2 Second", cw.subtitle)
        assertEquals("tt1", repo.latestForItem("tt1")?.itemId)
    }

    @Test
    fun liveItemsNeverFinishStoreNoPositionAndStayOutOfContinueWatching() = runBlocking {
        repo.saveProgress(movie().copy(itemType = "tv", isLive = true), 9_999_999, 1)
        val row = repo.history.first().single()
        assertEquals(0, row.positionMs)
        assertTrue(repo.continueWatching.first().isEmpty())
        repo.remove(row)
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun historyIsKeyedByTitleNotByTheMetaAddon() = runBlocking {
        repo.saveProgress(movie(), 120_000, 5_400_000)
        now += 1_000
        // Next visit another addon answered the meta first: same row, resume still found.
        val other = movie().copy(sourceAddonId = "b")
        assertEquals(120_000, repo.find(other)?.positionMs)
        repo.saveProgress(other, 130_000, 5_400_000)
        val rows = repo.history.first()
        assertEquals(1, rows.size)
        assertEquals("a", rows.single().sourceId)
        assertEquals(130_000, rows.single().positionMs)
    }

    @Test
    fun finishingAnEpisodeWhoseNextAlreadyHasARowKeepsTheSeriesInContinueWatching() = runBlocking {
        val next = NextEpisodeInfo("tt1:1:2", "S1 · E2 Second", 1, 2)
        val second = episode(null).copy(videoId = "tt1:1:2", subtitle = "S1 · E2 Second", episode = 2)
        repo.saveProgress(second, 100_000, 1_300_000) // watched a bit of E2 first
        now += 10_000
        repo.saveProgress(episode(next), 1_290_000, 1_300_000) // then finished E1
        val cw = repo.continueWatching.first().single()
        assertEquals("tt1:1:2", cw.videoId)
        assertEquals(100_000, cw.positionMs)
    }

    /** QC r2 N1, movie: resume from Continue watching, the stream fails, Back → the title must stay. */
    @Test
    fun aFailedResumeNeverOverwritesTheKnownProgress() = runBlocking {
        repo.saveProgress(movie(), 1_200_000, 5_400_000)
        now += 60_000
        // Stop-save of the failed attempt: start position, no duration.
        repo.saveProgress(movie(), 1_200_000, 0)
        repo.saveProgress(movie(), 0, 0)
        val cw = repo.continueWatching.first().single()
        assertEquals(1_200_000, cw.positionMs)
        assertEquals(5_400_000, cw.durationMs)
        assertEquals(1, repo.history.first().size)
    }

    /** QC r2 N1, series: S1E3 half watched, S1E4 dead link, Back → the series stays on S1E3. */
    @Test
    fun aDeadNextEpisodeDoesNotHideTheSeries() = runBlocking {
        val e3 = episode(null).copy(videoId = "tt1:1:3", episode = 3, subtitle = "S1 · E3")
        val e4 = episode(null).copy(videoId = "tt1:1:4", episode = 4, subtitle = "S1 · E4")
        repo.saveProgress(e3, 600_000, 1_300_000)
        now += 60_000
        repo.saveProgress(e4, 0, 0)
        repo.saveProgress(e4, 5_000, 0)
        val cw = repo.continueWatching.first().single()
        assertEquals("tt1:1:3", cw.videoId)
        assertEquals(600_000, cw.positionMs)
    }

    @Test
    fun aNewerUnresumableRowNeverHidesAnOlderResumableOne() = runBlocking {
        // Even if such a row exists in the table (older builds), the latest *resumable* row wins.
        val e3 = episode(null).copy(videoId = "tt1:1:3", episode = 3)
        repo.saveProgress(e3, 600_000, 1_300_000)
        stores.upsert(
            repo.history.first().single().copy(id = 0, videoId = "tt1:1:4", positionMs = 5_000, durationMs = 0, updatedAt = now + 99)
        )
        assertEquals("tt1:1:3", repo.continueWatching.first().single().videoId)
    }

    @Test
    fun streamsThatNeverLoadedAreNotInContinueWatching() = runBlocking {
        repo.saveProgress(movie(), 0, 0)
        assertTrue(repo.continueWatching.first().isEmpty())
    }
}
