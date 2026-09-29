package tss.t.tsiptv.core.stremio

import tss.t.tsiptv.TestAssets
import kotlin.test.assertIs

internal object StremioFixtures {
    fun read(name: String): String = TestAssets.read("stremio/$name")

    fun manifest(name: String): StremioManifest =
        assertIs<ManifestParseResult.Valid>(StremioManifestParser.parse(read(name))).manifest

    val sampler: StremioManifest get() = manifest("sampler/manifest.json")
    val cinemeta: StremioManifest get() = manifest("cinemeta-manifest.json")

    fun transport(url: String): StremioTransport =
        assertIs<ManifestUrlResult.Ok>(ManifestUrlNormalizer.normalize(url)).transport
}

/** Records every request; answers with [handler]. */
internal class FakeTransport(
    private val handler: suspend (url: String) -> StremioHttpResponse,
) : StremioHttpTransport {
    val requests = mutableListOf<String>()
    val requestHeaders = mutableListOf<Map<String, String>>()
    val kinds = mutableListOf<StremioRequestKind>()

    override suspend fun get(url: String, headers: Map<String, String>, kind: StremioRequestKind): StremioHttpResponse {
        requests += url
        requestHeaders += headers
        kinds += kind
        return handler(url)
    }

    companion object {
        fun ok(body: String, cacheControl: String? = null) = StremioHttpResponse(
            200,
            buildMap { if (cacheControl != null) put("cache-control", cacheControl) },
            body,
        )

        fun status(code: Int, body: String = "") = StremioHttpResponse(code, emptyMap(), body)

        /**
         * Serves the sampler fixtures like a static host: `{base}/catalog/movie/x.json` →
         * `sampler/catalog__movie__x.json` (path decoded like a static host would); 404 otherwise.
         */
        fun samplerHost(base: String): FakeTransport = FakeTransport { url ->
            val path = url.removePrefix(base).removePrefix("/").substringBefore('?')
            val file = StremioUrlEncoding.decodeComponent(path).replace("/", "__")
            try {
                ok(StremioFixtures.read("sampler/$file"))
            } catch (_: Throwable) {
                status(404, "<html>Not found</html>")
            }
        }
    }
}
