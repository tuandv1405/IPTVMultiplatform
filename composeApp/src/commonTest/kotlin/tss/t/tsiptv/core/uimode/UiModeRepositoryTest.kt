package tss.t.tsiptv.core.uimode

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiModeRepositoryTest {

    @Test
    fun autoFollowsTheDevice() {
        assertTrue(UiMode.AUTO.resolveIsTv(deviceIsTv = true))
        assertFalse(UiMode.AUTO.resolveIsTv(deviceIsTv = false))
    }

    @Test
    fun explicitChoiceOverridesDetection() {
        assertTrue(UiMode.TV.resolveIsTv(deviceIsTv = false))
        assertFalse(UiMode.PHONE.resolveIsTv(deviceIsTv = true))
    }

    @Test
    fun unknownStoredValueFallsBackToAuto() {
        assertEquals(UiMode.AUTO, UiMode.fromName(null))
        assertEquals(UiMode.AUTO, UiMode.fromName(""))
        assertEquals(UiMode.AUTO, UiMode.fromName("LEANBACK"))
    }

    @Test
    fun defaultsToAutoWhenNothingStored() = runBlocking {
        val repository = UiModeRepository(InMemoryKeyValueStorage())
        assertEquals(UiMode.AUTO, repository.getUiMode())
        assertEquals(UiMode.AUTO, repository.observeUiMode().first())
    }

    @Test
    fun observersSeeEveryChangeIncludingBackToAuto() = runBlocking {
        val repository = UiModeRepository(InMemoryKeyValueStorage())
        val observed = repository.observeUiMode()

        repository.setUiMode(UiMode.TV)
        assertEquals(UiMode.TV, observed.first())

        repository.setUiMode(UiMode.PHONE)
        assertEquals(UiMode.PHONE, observed.first())

        // Going back to AUTO must reach an observer that is already subscribed.
        repository.setUiMode(UiMode.AUTO)
        assertEquals(UiMode.AUTO, observed.first())
        assertEquals(UiMode.AUTO, repository.getUiMode())
    }
}
