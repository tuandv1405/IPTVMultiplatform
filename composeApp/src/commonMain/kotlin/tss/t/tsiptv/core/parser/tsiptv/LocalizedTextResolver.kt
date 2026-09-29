package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.serialization.Serializable

/**
 * A Text or LongText value (spec §5.2), already validated.
 *
 * A plain string is stored under the document's `meta.language`, so both forms resolve the
 * same way. [defaultLanguage] travels with the value because items of included documents keep
 * their own document's `meta.language` after they are merged into the root's pools.
 *
 * @property values Language tag → text, in document order (at least one entry)
 * @property defaultLanguage `meta.language` of the document the value came from
 */
@Serializable
data class LocalizedText(
    val values: Map<String, String>,
    val defaultLanguage: String = LocalizedTextResolver.FALLBACK_LANGUAGE,
) {
    /** The text to show for [uiLanguage] (BCP 47 such as `vi`, `zh-CN`; `zh-rCN` and `zh_CN` are accepted). */
    fun resolve(uiLanguage: String?): String =
        LocalizedTextResolver.resolve(values, uiLanguage, defaultLanguage) ?: ""

    companion object {
        fun plain(text: String, language: String = LocalizedTextResolver.FALLBACK_LANGUAGE) =
            LocalizedText(mapOf(language to text), language)
    }
}

/**
 * Resolution order of spec §5.2:
 *
 * 1. exact match of the UI language (`zh-CN`);
 * 2. its primary subtag (`zh`), then any key with the same primary subtag (`zh-TW`), in document order;
 * 3. `meta.language`;
 * 4. `en`;
 * 5. the first entry in document order.
 *
 * Tags are compared case-insensitively. Written for TS IPTV Sources; usable for any
 * `Map<language, text>` (Stremio has no localized values today).
 */
object LocalizedTextResolver {
    const val FALLBACK_LANGUAGE = "en"

    fun resolve(values: Map<String, String>, uiLanguage: String?, documentLanguage: String?): String? {
        if (values.isEmpty()) return null
        val ui = uiLanguage?.let(::normalizeTag)?.takeIf { it.isNotEmpty() }
        if (ui != null) {
            values.entries.firstOrNull { it.key.equals(ui, ignoreCase = true) }?.let { return it.value }
            val primary = primarySubtag(ui)
            values.entries.firstOrNull { it.key.equals(primary, ignoreCase = true) }?.let { return it.value }
            values.entries.firstOrNull { primarySubtag(it.key).equals(primary, ignoreCase = true) }
                ?.let { return it.value }
        }
        documentLanguage?.let { lang ->
            values.entries.firstOrNull { it.key.equals(lang, ignoreCase = true) }?.let { return it.value }
        }
        values.entries.firstOrNull { it.key.equals(FALLBACK_LANGUAGE, ignoreCase = true) }?.let { return it.value }
        return values.values.first()
    }

    /** `zh-rCN` (Android resource qualifier) and `zh_CN` (Java locale) become `zh-CN`. */
    fun normalizeTag(tag: String): String {
        val parts = tag.trim().replace('_', '-').split('-').filter { it.isNotEmpty() }
        return parts.mapIndexed { index, part ->
            if (index > 0 && part.length == 3 && (part[0] == 'r' || part[0] == 'R') && part.drop(1).all { it.isLetter() }) {
                part.drop(1)
            } else {
                part
            }
        }.joinToString("-")
    }

    fun primarySubtag(tag: String): String = tag.substringBefore('-')
}
