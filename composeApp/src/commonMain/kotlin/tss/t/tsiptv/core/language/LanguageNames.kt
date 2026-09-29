package tss.t.tsiptv.core.language

/**
 * The name of the language [tag] (BCP 47, e.g. `vi`, `pt-BR`) in [uiLanguage], for labels such as
 * subtitle tracks without a `label` (F3 spec §8.7). Null when the platform doesn't know it.
 */
expect fun languageDisplayName(tag: String, uiLanguage: String?): String?
