package tss.t.tsiptv.core.billing

import tss.t.tsiptv.core.billing.BillingConnectionPolicy.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingConnectionPolicyTest {

    @Test
    fun backoffDoublesFromOneSecondUpToFiveMinutes() {
        val p = BillingConnectionPolicy()
        val delays = (0 until 12).map { p.delayAfter(it) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 256_000L, 300_000L, 300_000L, 300_000L), delays)
        assertEquals(300_000L, p.delayAfter(1_000)) // no overflow
    }

    @Test
    fun noAttemptWhileWaitingAndResetOnSuccess() {
        val p = BillingConnectionPolicy()
        assertTrue(p.mayConnect(0))
        p.onConnecting()
        assertFalse(p.mayConnect(0))
        assertEquals(1_000L, p.onFailure(0))
        assertEquals(State.WAITING, p.state)
        assertFalse(p.mayConnect(999))
        assertTrue(p.mayConnect(1_000))
        p.onConnecting()
        assertEquals(2_000L, p.onFailure(1_000))
        assertFalse(p.mayConnect(2_999))
        assertTrue(p.mayConnect(3_000))
        p.onConnecting()
        p.onConnected()
        assertEquals(0, p.failures)
        assertFalse(p.mayConnect(3_000)) // already connected
        // A lost service starts over at one second.
        assertEquals(1_000L, p.onFailure(10_000))
    }

    @Test
    fun unavailableStopsUntilAUserAction() {
        val p = BillingConnectionPolicy()
        p.onConnecting()
        p.onUnavailable()
        assertFalse(p.mayConnect(0))
        assertFalse(p.mayConnect(Long.MAX_VALUE / 2))
        assertFalse(p.shouldQueryOnResume(0))
        p.onUserAction()
        assertEquals(State.IDLE, p.state)
        assertTrue(p.mayConnect(0))
    }

    @Test
    fun userActionSkipsTheBackoff() {
        val p = BillingConnectionPolicy()
        repeat(5) { p.onConnecting(); p.onFailure(0) }
        assertFalse(p.mayConnect(1_000))
        p.onUserAction()
        assertTrue(p.mayConnect(1_000))
        p.onConnecting()
        assertEquals(1_000L, p.onFailure(1_000))
    }

    @Test
    fun resumeQueriesOnlyWhenConnectedAndAtMostOncePerMinute() {
        val p = BillingConnectionPolicy()
        assertFalse(p.shouldQueryOnResume(0)) // idle
        p.onConnecting()
        p.onFailure(0)
        assertFalse(p.shouldQueryOnResume(100_000)) // waiting: no polling
        p.onConnecting()
        p.onConnected()
        assertTrue(p.shouldQueryOnResume(100_000))
        assertFalse(p.shouldQueryOnResume(110_000))
        assertFalse(p.shouldQueryOnResume(159_999))
        assertTrue(p.shouldQueryOnResume(160_000))
    }
}
