package tss.t.tsiptv.core.tsiptv

import kotlinx.serialization.builtins.ListSerializer
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvStream

/**
 * The streams of a TS IPTV Source channel (spec §8.0/§8.4: 1–10, best first). The player plays the
 * first one the device supports (§8.5: on iOS/desktop a DRM stream is skipped for the next one
 * without DRM) and lets the viewer pick another.
 */
object TsiptvChannelStreams {
    private val list = ListSerializer(TsiptvStream.serializer())

    fun streams(channel: Channel): List<TsiptvStream> = channel.streamsJson?.let {
        runCatching { TsiptvStorageJson.decodeFromString(list, it) }.getOrNull()
    }.orEmpty()

    /** One [Channel] per stream (same id), or just [channel] when it has a single stream. */
    fun variants(channel: Channel): List<Channel> {
        val streams = streams(channel)
        if (streams.size < 2) return listOf(channel)
        return streams.map { s -> channel.copy(url = s.url, headers = s.headers, mimeType = s.mimeType, drm = s.drm) }
    }

    /** Picker labels: the stream's name, else quality · language, else [fallback] (`Source N`). */
    fun labels(channel: Channel, uiLanguage: String?, fallback: (Int) -> String): List<String> =
        streams(channel).mapIndexed { i, s ->
            s.name?.resolve(uiLanguage)?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(s.quality, s.language).joinToString(" · ").takeIf { it.isNotBlank() }
                ?: fallback(i + 1)
        }

    /** Index of the first variant [isPlayable] accepts; 0 when none does (its error is shown). */
    fun firstPlayable(variants: List<Channel>, isPlayable: (Channel) -> Boolean): Int =
        variants.indexOfFirst(isPlayable).takeIf { it >= 0 } ?: 0
}
