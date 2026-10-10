package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** QC round 3: two playlists with the same entries must not take each other's channels. */
class ChannelIdNamespaceTest {
    private fun playlist(id: String) = Playlist(id = id, name = id, url = "https://$id.example/list.m3u", lastUpdated = 1)
    private fun channels(playlistId: String, n: Int) =
        (1..n).map { Channel(id = "ch$it", name = "Channel $it", url = "https://cdn.example/$it.m3u8", playlistId = playlistId, sortIndex = it) }

    @Test
    fun identicalPlaylistsKeepTheirOwnChannels() = runBlocking<Unit> {
        val db = InMemoryIPTVDatabase()
        db.replacePlaylistContent(playlist("big"), emptyList(), channels("big", 50), emptyMap(), emptyMap())
        db.replacePlaylistContent(playlist("copy"), emptyList(), channels("copy", 50), emptyMap(), emptyMap())

        val big = db.getAllChannelsByPlayListId("big").first()
        val copy = db.getAllChannelsByPlayListId("copy").first()
        assertEquals(50, big.size)
        assertEquals(50, copy.size)
        // The first owner keeps the plain ids; the copy is namespaced, and its guide id is unchanged.
        assertEquals("ch1", big.first().id)
        assertNotEquals("ch1", copy.first().id)
        assertTrue(copy.first().id.startsWith("ch1" + ChannelIdNamespace.SEPARATOR))
        assertEquals("ch1", copy.first().guideId)

        // A refresh of either one maps the entries to the same ids again (history, favourites).
        val idsBefore = copy.map { it.id }
        db.replacePlaylistContent(playlist("copy"), emptyList(), channels("copy", 50), emptyMap(), emptyMap())
        assertEquals(idsBefore, db.getAllChannelsByPlayListId("copy").first().map { it.id })
        db.replacePlaylistContent(playlist("big"), emptyList(), channels("big", 50), emptyMap(), emptyMap())
        assertEquals(50, db.getAllChannelsByPlayListId("big").first().size)
        assertEquals(50, db.getAllChannelsByPlayListId("copy").first().size)
    }

    @Test
    fun tagIsStableAndShort() {
        assertEquals(ChannelIdNamespace.tag("abc"), ChannelIdNamespace.tag("abc"))
        assertNotEquals(ChannelIdNamespace.tag("abc"), ChannelIdNamespace.tag("abd"))
        assertEquals(8, ChannelIdNamespace.tag("x").length)
    }
}
