package tss.t.tsiptv.feature.lan

/**
 * The address scan: when mDNS finds nothing (multicast blocked by the router, mesh or the TV's
 * Wi-Fi), the phone asks every address of its own Wi-Fi on [LanProtocol.DEFAULT_PORT] with `hello`.
 * Plain unicast TCP, like "connect by IP", so it works wherever typing the IP works.
 */
object LanScan {
    /** Parallel probes; each one waits at most [LanProtocol.PROBE_CONNECT_TIMEOUT_MS] for a connection. */
    const val PARALLEL = 48

    /**
     * The hosts to ask: each network's addresses, at most its own /24 (a /16 office network is not
     * scanned whole), without the network and broadcast addresses and without this device.
     */
    fun hosts(networks: List<LanNetwork>): List<String> {
        val own = networks.mapNotNull { parse(it.address) }.toSet()
        return networks.flatMap { network ->
            val address = parse(network.address) ?: return@flatMap emptyList()
            if (network.prefixLength !in 1..32) return@flatMap emptyList()
            val prefix = maxOf(network.prefixLength, 24)
            val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val base = address and mask
            val last = base or (mask.inv() and 0xFFFFFFFFL)
            // A /31 or /32 has no other host worth asking.
            if (last - base < 2) return@flatMap emptyList()
            ((base + 1) until last).filter { it !in own }.map(::format)
        }.distinct()
    }

    private fun parse(text: String): Long? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val n = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    private fun format(value: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 0xFF).toString() }
}
