package tss.t.tsiptv.feature.account

import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncPlannerTest {

    private fun local(id: String, name: String = id, url: String = "https://x.example/$id.m3u", updated: Long = 10, file: Boolean = false, epg: List<String> = emptyList()) =
        Playlist(
            id = id,
            name = name,
            url = if (file) "file:$id.m3u" else url,
            lastUpdated = updated,
            sourceType = if (file) PlaylistSourceType.FILE else PlaylistSourceType.URL,
            epgUrls = epg,
        )

    private fun remote(id: String, name: String = id, url: String = "https://x.example/$id.m3u", updated: Long = 10, epg: List<String> = emptyList()) =
        SyncedPlaylist(id = id, name = name, url = url, lastUpdated = updated, epgUrls = epg)

    @Test
    fun buildKeepsDefinitionsOnlyAndSkipsFiles() {
        val build = SyncPlanner.build(listOf(local("a", epg = listOf("https://e/1.xml")), local("f", file = true), local("b")))
        assertEquals(listOf("a", "b"), build.payload.playlists.map { it.id })
        assertEquals(listOf("f"), build.skippedFiles)
        assertEquals(listOf("https://e/1.xml"), build.payload.playlists[0].epgUrls)
        assertFalse(build.tooLarge)
        // No channel or stream data in the stored JSON.
        assertFalse("channels" in build.json)
        assertEquals(build.payload, SyncPlanner.parse(build.json))
    }

    @Test
    fun buildRefusesMoreThanTheCap() {
        val many = (0..SyncPlanner.MAX_PLAYLISTS).map { local("p$it") }
        assertTrue(SyncPlanner.build(many).tooLarge)
    }

    @Test
    fun parseDropsUnusableEntries() {
        val text = """{"v":1,"playlists":[{"id":"a","name":"A","url":"https://ok/a"},{"id":"b","name":"B","url":"javascript:alert(1)"},{"id":"a","name":"dup","url":"https://ok/a2"}]}"""
        assertEquals(listOf("a"), SyncPlanner.parse(text)!!.playlists.map { it.id })
        assertNull(SyncPlanner.parse("not json"))
    }

    @Test
    fun mergeAddsMissingKeepsLocalAndRemovesNothing() {
        val plan = SyncPlanner.planMerge(
            local = listOf(local("a"), local("only-local")),
            remote = listOf(remote("a"), remote("new")),
        )
        assertEquals(listOf("new"), plan.toAdd.map { it.id })
        assertTrue(plan.toUpdate.isEmpty())
        assertTrue(plan.toRemove.isEmpty())
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun mergeSameIdNewerWins() {
        val newerRemote = SyncPlanner.planMerge(listOf(local("a", name = "Old", updated = 10)), listOf(remote("a", name = "New", updated = 20)))
        assertEquals(listOf("New"), newerRemote.toUpdate.map { it.name })

        val newerLocal = SyncPlanner.planMerge(listOf(local("a", name = "Mine", updated = 30)), listOf(remote("a", name = "Theirs", updated = 20)))
        assertTrue(newerLocal.toUpdate.isEmpty())
        assertEquals(1, newerLocal.unchanged)
    }

    @Test
    fun mergeMatchesTheSameLinkUnderAnotherId() {
        val plan = SyncPlanner.planMerge(listOf(local("x1", url = "https://same/list.m3u")), listOf(remote("x2", url = "https://same/list.m3u")))
        assertTrue(plan.toAdd.isEmpty())
    }

    @Test
    fun replaceListsExactlyWhatIsRemoved() {
        val plan = SyncPlanner.planReplace(
            local = listOf(local("keep"), local("gone"), local("file", file = true), local("renamed", name = "A")),
            remote = listOf(remote("keep"), remote("renamed", name = "B", updated = 1), remote("added")),
        )
        assertEquals(listOf("gone", "file"), plan.toRemove.map { it.id })
        assertEquals(listOf("added"), plan.toAdd.map { it.id })
        // Replace takes the synced copy even when it is older.
        assertEquals(listOf("renamed"), plan.toUpdate.map { it.id })
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun replaceWithEmptySyncRemovesEverything() {
        val plan = SyncPlanner.planReplace(listOf(local("a"), local("b")), emptyList())
        assertEquals(2, plan.toRemove.size)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun onlyNewSyncsFromOtherDevicesAreOffered() {
        val doc = SyncDocument(fromDeviceId = "other", createdAt = 100)
        assertTrue(SyncPlanner.isNewForThisDevice(doc, "me", 50))
        assertFalse(SyncPlanner.isNewForThisDevice(doc, "me", 100))
        assertFalse(SyncPlanner.isNewForThisDevice(doc.copy(fromDeviceId = "me"), "me", 0))
        assertFalse(SyncPlanner.isNewForThisDevice(null, "me", 0))
    }
}
