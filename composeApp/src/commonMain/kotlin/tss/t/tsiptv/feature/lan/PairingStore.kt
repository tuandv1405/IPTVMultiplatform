package tss.t.tsiptv.feature.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import tss.t.tsiptv.core.security.SecretCipher
import tss.t.tsiptv.core.storage.KeyValueStorage

/**
 * A device this one is paired with. The key itself never leaves [PairingStore]. [host] and [port]
 * are where it last answered (a phone keeps them for its TVs, so they show without mDNS).
 */
data class PairedPeer(val id: String, val name: String, val pairedAt: Long, val host: String? = null, val port: Int? = null)

/**
 * Pair keys at rest: one list per role (a TV keeps phones, a phone keeps TVs), each key wrapped
 * with the app's [SecretCipher] (Android Keystore AES-GCM).
 */
class PairingStore(
    private val storage: KeyValueStorage,
    private val cipher: SecretCipher,
    private val role: Role,
) {
    enum class Role(val storageKey: String) { RECEIVER("lan_paired_senders"), SENDER("lan_paired_receivers") }

    @Serializable
    private data class Entry(
        val id: String,
        val name: String,
        val key: String,
        val pairedAt: Long,
        // Where the TV answered last; entries from older builds have none until it answers once.
        val host: String? = null,
        val port: Int? = null,
    )

    private val mutex = Mutex()
    private var loaded: MutableList<Entry>? = null
    private val _peers = MutableStateFlow<List<PairedPeer>>(emptyList())
    val peers: StateFlow<List<PairedPeer>> = _peers

    private suspend fun entries(): MutableList<Entry> {
        loaded?.let { return it }
        val text = storage.getString(role.storageKey)
        val list = if (text.isEmpty()) mutableListOf() else runCatching {
            LanProtocol.json.decodeFromString(ListSerializer(Entry.serializer()), text).toMutableList()
        }.getOrElse { mutableListOf() }
        loaded = list
        _peers.value = list.map { it.toPeer() }
        return list
    }

    private suspend fun save(list: List<Entry>) {
        storage.putString(role.storageKey, LanProtocol.json.encodeToString(ListSerializer(Entry.serializer()), list))
        _peers.value = list.map { it.toPeer() }
    }

    // Names stored by older builds are cleaned on the way out too.
    private fun Entry.toPeer() = PairedPeer(id, LanValidation.cleanName(name), pairedAt, host, port)

    suspend fun load(): List<PairedPeer> = mutex.withLock { entries().map { it.toPeer() } }

    suspend fun keyFor(id: String): ByteArray? = mutex.withLock {
        val entry = entries().firstOrNull { it.id == id } ?: return@withLock null
        cipher.decrypt(entry.key)?.let { LanCrypto.unb64(it) }
    }

    suspend fun nameOf(id: String): String? = mutex.withLock { entries().firstOrNull { it.id == id }?.name?.let(LanValidation::cleanName) }

    suspend fun put(id: String, name: String, key: ByteArray, now: Long, host: String? = null, port: Int? = null) = mutex.withLock {
        val list = entries()
        list.removeAll { it.id == id }
        list.add(Entry(id, LanValidation.cleanName(name), cipher.encrypt(LanCrypto.b64(key)), now, host, port))
        // Bounded: the oldest pairing goes first.
        while (list.size > MAX_PEERS) list.removeAt(0)
        save(list)
    }

    /** Where paired [id] answered last (and its current name); nothing for an unknown id. */
    suspend fun remember(id: String, name: String, host: String, port: Int) = mutex.withLock {
        val list = entries()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@withLock
        val old = list[index]
        val clean = LanValidation.cleanName(name).ifBlank { old.name }
        if (old.host == host && old.port == port && old.name == clean) return@withLock
        list[index] = old.copy(name = clean, host = host, port = port)
        save(list)
    }

    suspend fun remove(id: String) = mutex.withLock {
        val list = entries()
        if (list.removeAll { it.id == id }) save(list)
    }

    companion object {
        const val MAX_PEERS = 20
    }
}
