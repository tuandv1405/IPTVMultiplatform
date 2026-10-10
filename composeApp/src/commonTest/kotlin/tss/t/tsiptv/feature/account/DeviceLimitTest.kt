package tss.t.tsiptv.feature.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tss.t.tsiptv.core.firebase.models.DeactivationRequest
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.model.AuthState
import tss.t.tsiptv.feature.auth.domain.model.AuthToken
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeviceLimitTest {

    private fun device(id: String, lastSeen: Long = 0) = RegisteredDevice(id = id, lastSeen = lastSeen)

    @Test
    fun policyDecisions() {
        val now = DeviceLimitPolicy.TOUCH_INTERVAL_MS * 10
        val four = (1..4).map { device("d$it", lastSeen = it.toLong()) }
        assertEquals(DeviceCheck.Register, DeviceLimitPolicy.check(four.take(3), "me", wasRegistered = false, now = now))
        val limit = DeviceLimitPolicy.check(four, "me", wasRegistered = false, now = now)
        assertIs<DeviceCheck.LimitReached>(limit)
        assertEquals(listOf("d4", "d3", "d2", "d1"), limit.devices.map { it.id })
        assertEquals(DeviceCheck.RemovedElsewhere, DeviceLimitPolicy.check(four.take(3), "me", wasRegistered = true, now = now))
        assertEquals(DeviceCheck.Registered(touch = true), DeviceLimitPolicy.check(four + device("me", 0), "me", true, now))
        assertEquals(DeviceCheck.Registered(touch = false), DeviceLimitPolicy.check(listOf(device("me", now - 1)), "me", true, now))
    }

    internal class FakeAuthForTests(uid: String?) : AuthRepository {
        val state = MutableStateFlow(AuthState(isAuthenticated = uid != null, user = uid?.let { FirebaseUser(uid = it, email = null, displayName = null, photoUrl = null, isEmailVerified = false) }, isLoading = false))
        var signOuts = 0
        override val authState: Flow<AuthState> = state
        override suspend fun signOut(): AuthResult {
            signOuts++
            state.value = AuthState(isAuthenticated = false, isLoading = false)
            return AuthResult.SignedOut
        }
        override suspend fun signInWithEmailAndPassword(email: String, password: String) = TODO()
        override suspend fun createUserWithEmailAndPassword(email: String, password: String) = TODO()
        override suspend fun signInWithGoogle() = TODO()
        override suspend fun signInWithApple() = TODO()
        override suspend fun refreshTokenIfNeeded() = TODO()
        override suspend fun getAuthToken(): AuthToken? = null
        override suspend fun getCurrentUser(): FirebaseUser? = null
        override suspend fun isAuthenticated() = state.value.isAuthenticated
        override suspend fun isTokenExpired() = false
        override suspend fun createDeactivationRequest(reason: String?) = TODO()
        override suspend fun getDeactivationRequest(): DeactivationRequest? = null
        override fun observeDeactivationRequest(): Flow<DeactivationRequest?> = TODO()
        override suspend fun cancelDeactivationRequest() = TODO()
        override suspend fun updateDisplayName(displayName: String) = TODO()
        override suspend fun changePassword(currentPassword: String, newPassword: String) = TODO()
        override suspend fun sendPasswordResetEmail(email: String) = TODO()
    }

    private suspend fun DeviceSessionManager.awaitUid() = withTimeout(5_000) { uid.first { it != null } }

    @Test
    fun fifthDeviceSeesTheLimitAndRemovingOneLetsItIn() = runBlocking<Unit> {
        val cloud = InMemoryAccountCloud()
        repeat(4) { cloud.registerDevice("u1", device("other$it", lastSeen = it.toLong())) }
        val auth = FakeAuthForTests("u1")
        val storage = InMemoryKeyValueStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = DeviceSessionManager(auth, cloud, LocalDevice(storage), storage, scope) { 1_000L }
        manager.start()
        manager.awaitUid()
        manager.check()
        val gate = manager.gate.value
        assertIs<DeviceGate.LimitReached>(gate)
        assertEquals(4, gate.devices.size)

        assertTrue(manager.signOutRemote("other0"))
        assertEquals(DeviceGate.None, manager.gate.value)
        val ids = cloud.listDevices("u1").map { it.id }
        assertEquals(4, ids.size)
        assertFalse("other0" in ids)
        assertTrue(manager.myDeviceId() in ids)
        scope.cancel()
    }

    @Test
    fun aDeviceRemovedElsewhereSignsItselfOut() = runBlocking<Unit> {
        val cloud = InMemoryAccountCloud()
        val auth = FakeAuthForTests("u1")
        val storage = InMemoryKeyValueStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = DeviceSessionManager(auth, cloud, LocalDevice(storage), storage, scope) { 1_000L }
        manager.start()
        manager.awaitUid()
        manager.check()
        assertTrue(cloud.isDeviceRegistered("u1", manager.myDeviceId()))

        cloud.removeDevice("u1", manager.myDeviceId())
        manager.check()
        assertEquals(DeviceGate.RemovedElsewhere, manager.gate.value)
        assertEquals(1, auth.signOuts)
        scope.cancel()
    }
}
