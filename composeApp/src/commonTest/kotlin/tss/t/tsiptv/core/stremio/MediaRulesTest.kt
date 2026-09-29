package tss.t.tsiptv.core.stremio

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaRulesTest {
    private val now = MediaRules.parseReleased("2026-09-28T00:00:00Z")!!

    private fun cinemetaSeries(): StremioMeta {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read("cinemeta-series-meta.json")).jsonObject
        return StremioJson.decodeFromJsonElement(StremioMeta.serializer(), root["meta"]!!)
    }

    @Test
    fun acceptanceS13SeasonsWithSpecialsLastAndUpcoming() {
        val meta = cinemetaSeries()
        val groups = MediaRules.groupSeasons(meta.videos!!)
        assertEquals(listOf(1, 2, 11, 0, null), groups.map { it.season })
        assertTrue(groups[3].isSpecials)
        assertEquals(listOf("tt0108778:1:1", "tt0108778:1:2"), groups[0].episodes.map { it.id })
        val upcoming = groups[2].episodes.single()
        assertTrue(MediaRules.isUpcoming(upcoming, now))
        assertFalse(MediaRules.isUpcoming(groups[0].episodes[0], now))
        assertTrue(MediaRules.isSeriesLike(meta))
    }

    @Test
    fun samplerSeries() {
        val root = StremioJson.parseToJsonElement(StremioFixtures.read("sampler/meta__series__tspd_superman.json")).jsonObject
        val meta = StremioJson.decodeFromJsonElement(StremioMeta.serializer(), root["meta"]!!)
        val groups = MediaRules.groupSeasons(meta.videos!!)
        assertEquals(1, groups.single().season)
        assertEquals("The Mechanical Monsters", groups.single().episodes.single().displayTitle)
        assertEquals("S1 · E1 The Mechanical Monsters", MediaRules.episodeLabel(groups.single().episodes.single()))
    }

    @Test
    fun releasedParsing() {
        assertEquals(MediaRules.parseReleased("1941-11-28T00:00:00.000Z"), MediaRules.parseReleased("1941-11-28"))
        assertNull(MediaRules.parseReleased("soon"))
        assertNull(MediaRules.parseReleased(null))
        assertFalse(MediaRules.isUpcoming(StremioVideo(id = "x", released = "garbage"), now))
    }

    @Test
    fun resumeAndFinished() {
        assertEquals(0L, MediaRules.resumePosition(59_000, 1_000_000))
        assertEquals(120_000L, MediaRules.resumePosition(120_000, 1_000_000))
        assertEquals(0L, MediaRules.resumePosition(950_000, 1_000_000))
        assertEquals(120_000L, MediaRules.resumePosition(120_000, 0))
        assertTrue(MediaRules.isFinished(950_000, 1_000_000))
        assertFalse(MediaRules.isFinished(949_999, 1_000_000))
        assertFalse(MediaRules.isFinished(10, 0))
    }

    @Test
    fun nextEpisodeSkipsSpecialsAndUpcoming() {
        val videos = cinemetaSeries().videos!!
        assertEquals("tt0108778:1:2", MediaRules.nextEpisode(videos, "tt0108778:1:1", now)?.id)
        assertEquals("tt0108778:2:1", MediaRules.nextEpisode(videos, "tt0108778:1:2", now)?.id)
        // Next after 2:1 is season 11, which is upcoming.
        assertNull(MediaRules.nextEpisode(videos, "tt0108778:2:1", now))
        assertNull(MediaRules.nextEpisode(videos, "unknown", now))
    }

    @Test
    fun duplicateVideoIdsAreDroppedBeforeTheyBecomeListKeys() {
        val videos = listOf(
            StremioVideo(id = "e1", season = 1, episode = 1),
            StremioVideo(id = "e1", season = 1, episode = 1, title = "duplicate"),
            StremioVideo(id = "", season = 1, episode = 2),
            StremioVideo(id = "e3", season = 1, episode = 3),
        )
        assertEquals(listOf("e1", "e3"), MediaRules.groupSeasons(videos).single().episodes.map { it.id })
    }

    @Test
    fun episodeLabels() {
        assertEquals("S1 · E3", MediaRules.episodeLabel(StremioVideo(id = "a", season = 1, episode = 3)))
        assertEquals("Pilot", MediaRules.episodeLabel(StremioVideo(id = "a", title = "Pilot")))
    }

    @Test
    fun preferredStream() {
        fun row(name: String, binge: String?, url: Boolean = true) = ClassifiedStream(
            StremioStream(name = name, url = if (url) "https://a/$name.mp4" else null, infoHash = if (url) null else "x",
                behaviorHints = StreamBehaviorHints(bingeGroup = binge)),
            StreamClassifier.classify(StremioStream(url = if (url) "https://a/$name.mp4" else null, infoHash = if (url) null else "x")),
        )
        val groups = listOf(
            "a1" to listOf(row("t", null, url = false), row("x", "g1")),
            "a2" to listOf(row("y", "g2"), row("z", "g1")),
        )
        assertEquals(3, MediaRules.preferredStreamIndex(groups, "a2", "g1"))
        assertEquals(1, MediaRules.preferredStreamIndex(groups, "a3", "g1"))
        assertEquals(1, MediaRules.preferredStreamIndex(groups, null, null))
        assertEquals(-1, MediaRules.preferredStreamIndex(listOf("a" to listOf(row("t", null, url = false))), null, null))
    }
}
