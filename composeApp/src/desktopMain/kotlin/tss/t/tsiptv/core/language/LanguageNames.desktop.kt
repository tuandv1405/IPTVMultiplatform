package tss.t.tsiptv.core.language

import java.util.Locale

actual fun languageDisplayName(tag: String, uiLanguage: String?): String? {
    val locale = Locale.forLanguageTag(tag.replace('_', '-'))
    if (locale.language.isNullOrEmpty()) return null
    val display = Locale.forLanguageTag((uiLanguage ?: Locale.getDefault().toLanguageTag()).replace("-r", "-").replace('_', '-'))
    return locale.getDisplayName(display).takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(display) else it.toString() }
}
