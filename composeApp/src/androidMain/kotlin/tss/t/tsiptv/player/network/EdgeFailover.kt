package tss.t.tsiptv.player.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Moves a host's requests off CDN edges that refuse them.
 *
 * Streams are often served from a multi-CDN host whose GSLB picks the edge from the
 * DNS resolver that asked. Some of those edges answer 403 to every client (one
 * CDN's edges return "deny code 22" whatever the headers), so a playlist can play
 * in a browser that uses its own secure DNS and
 * fail in the app on the same network. When an edge answers 403 its address is
 * set aside, the host is also looked up through public DNS-over-HTTPS resolvers,
 * which the GSLB tends to send to a different CDN, and the request is repeated
 * against an address that has not refused yet.
 *
 * Hosts that never answer 403 keep using the system resolver only.
 */
internal class EdgeFailover(bootstrap: OkHttpClient) {

    private val publicResolvers: List<Dns> = listOf(
        doh(bootstrap, "https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        doh(bootstrap, "https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
        doh(bootstrap, "https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112"),
    )

    /** Addresses that answered 403, by host. */
    private val refused = ConcurrentHashMap<String, MutableSet<InetAddress>>()

    /** Every address any resolver gave for a host that has refused, cached for [CANDIDATE_TTL_MS]. */
    private val candidates = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val refusedHere = refused[hostname] ?: return Dns.SYSTEM.lookup(hostname)
            val all = candidatesFor(hostname)
            if (all.isEmpty()) throw UnknownHostException(hostname)
            // Once every edge has refused, try them all again so the caller sees the real 403.
            return all.filterNot { it in refusedHere }.ifEmpty { all }
        }
    }

    /** Records the edge that served each 403. Install with [OkHttpClient.Builder.addNetworkInterceptor]. */
    val recordRefusals = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val host = chain.request().url.host
        val connection = chain.connection()
        val address = connection?.route()?.socketAddress?.address
        if (response.code == 403 && address != null) {
            refused.getOrPut(host) { ConcurrentHashMap.newKeySet() }.add(address)
            // OkHttp hands a call's retry the connection it already holds, and keeps pooled
            // connections for later calls, so both would go straight back to this edge.
            // A closed socket is unhealthy, which forces a new connection and a new lookup.
            if (hasUntriedEdge(host)) connection.socket().close()
        }
        response
    }

    /** Repeats a refused request on another edge. Install with [OkHttpClient.Builder.addInterceptor]. */
    val retryOnOtherEdge = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)
        var retries = 0
        while (response.code == 403 && retries < MAX_RETRIES && hasUntriedEdge(request.url.host)) {
            response.close()
            response = chain.proceed(request)
            retries++
        }
        response
    }

    private fun hasUntriedEdge(hostname: String): Boolean {
        val refusedHere = refused[hostname] ?: return false
        if (candidatesFor(hostname).any { it !in refusedHere }) return true
        // A GSLB rotates its answers between CDNs from one query to the next, so a
        // fresh lookup often turns up an edge the cached one did not have.
        candidates.remove(hostname)
        return candidatesFor(hostname).any { it !in refusedHere }
    }

    private fun candidatesFor(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        candidates[hostname]?.let { (at, addresses) ->
            if (now - at < CANDIDATE_TTL_MS) return addresses
        }
        val addresses = (listOf(Dns.SYSTEM) + publicResolvers)
            .flatMap { resolver ->
                try {
                    resolver.lookup(hostname)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            .distinct()
        candidates[hostname] = now to addresses
        return addresses
    }

    private companion object {
        const val MAX_RETRIES = 4
        const val CANDIDATE_TTL_MS = 60_000L

        fun doh(bootstrap: OkHttpClient, url: String, vararg bootstrapHosts: String): Dns =
            DnsOverHttps.Builder()
                .client(bootstrap)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(bootstrapHosts.map { InetAddress.getByName(it) })
                .build()
    }
}
