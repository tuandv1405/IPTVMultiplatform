package tss.t.tsiptv.feature.auth.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Work that must run while the account can still write, right before signing out (e.g. freeing
 * this device's slot in the account's device list). Each hook gets a few seconds; a failing or slow
 * hook never blocks the sign-out.
 */
object SignOutHooks {
    private val hooks = mutableListOf<suspend () -> Unit>()

    fun register(hook: suspend () -> Unit) {
        hooks += hook
    }

    internal suspend fun runAll() {
        for (hook in hooks.toList()) {
            try {
                withTimeoutOrNull(TIMEOUT_MS) { hook() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private const val TIMEOUT_MS = 4_000L
}
