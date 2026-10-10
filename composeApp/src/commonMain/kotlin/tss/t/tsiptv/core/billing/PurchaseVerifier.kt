package tss.t.tsiptv.core.billing

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.network.NetworkClient

/**
 * Sends a purchase to the billing server (`POST /billing/verify`, docs/prd-subscriptions.md §5.3),
 * which checks it with Google Play, links it to the signed-in account and writes the entitlement
 * document. Disabled while no server URL is configured (`TSIPTV_BILLING_VERIFY_URL`).
 */
interface PurchaseVerifier {
    val enabled: Boolean

    /** true when the server accepted the purchase. Never throws (except cancellation). */
    suspend fun verify(productId: String, purchaseToken: String): Boolean
}

object DisabledPurchaseVerifier : PurchaseVerifier {
    override val enabled: Boolean = false
    override suspend fun verify(productId: String, purchaseToken: String): Boolean = false
}

@Serializable
private data class VerifyRequest(val productId: String, val purchaseToken: String)

/**
 * @param idToken the Firebase ID token of the signed-in user (sent as `Authorization: Bearer`), or
 *   null when signed out (then nothing is sent: the purchase stays on this device's Play account)
 */
class HttpPurchaseVerifier(
    private val url: String,
    private val network: NetworkClient,
    private val idToken: suspend () -> String?,
) : PurchaseVerifier {
    override val enabled: Boolean = url.startsWith("https://")

    override suspend fun verify(productId: String, purchaseToken: String): Boolean {
        if (!enabled) return false
        val token = try {
            idToken()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return false
        return try {
            val body = network.post(
                url,
                Json.encodeToString(VerifyRequest.serializer(), VerifyRequest(productId, purchaseToken)),
                mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"),
            )
            // `post` does not fail on HTTP errors: success is the entitlement in the answer.
            accepted(body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Never log the token or the response. The next refresh tries again.
            false
        }
    }

    companion object {
        private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The server answers `{"entitlement":{"plan":…,…}}` on success and `{"error":…}` otherwise. */
        fun accepted(body: String): Boolean = runCatching {
            val root = lenient.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return false
            root["error"] == null && root["entitlement"] is kotlinx.serialization.json.JsonObject
        }.getOrDefault(false)
    }
}
