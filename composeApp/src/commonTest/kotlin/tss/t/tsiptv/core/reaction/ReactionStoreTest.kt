package tss.t.tsiptv.core.reaction

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReactionStoreTest {

    @Test
    fun tappingTurnsOnSwitchesOrClears() {
        assertEquals(Reaction.LIKE, Reactions.next(null, Reaction.LIKE))
        assertEquals(Reaction.DISLIKE, Reactions.next(Reaction.LIKE, Reaction.DISLIKE))
        assertEquals(Reaction.LIKE, Reactions.next(Reaction.DISLIKE, Reaction.LIKE))
        assertNull(Reactions.next(Reaction.LIKE, Reaction.LIKE))
        assertNull(Reactions.next(Reaction.DISLIKE, Reaction.DISLIKE))
    }

    @Test
    fun oneReactionPerKeyAndListsNewestFirst() = runBlocking {
        val store = InMemoryReactionStore()
        store.upsert(ReactionRecord("vtv1", Reaction.LIKE, "VTV1", null, 1))
        store.upsert(ReactionRecord("stremio:a:tt1", Reaction.LIKE, "Movie", null, 3))
        store.upsert(ReactionRecord("htv7", Reaction.DISLIKE, "HTV7", null, 2))

        assertEquals(listOf("stremio:a:tt1", "vtv1"), store.observeAll(Reaction.LIKE).first().map { it.contentKey })

        // Switching replaces the row: a key is never both liked and disliked.
        store.upsert(ReactionRecord("vtv1", Reaction.DISLIKE, "VTV1", null, 4))
        assertEquals(Reaction.DISLIKE, store.observe("vtv1").first())
        assertEquals(listOf("stremio:a:tt1"), store.observeAll(Reaction.LIKE).first().map { it.contentKey })
        assertEquals(listOf("vtv1", "htv7"), store.observeAll(Reaction.DISLIKE).first().map { it.contentKey })

        store.delete("vtv1")
        assertNull(store.observe("vtv1").first())
        assertNull(store.get("vtv1"))
    }
}
