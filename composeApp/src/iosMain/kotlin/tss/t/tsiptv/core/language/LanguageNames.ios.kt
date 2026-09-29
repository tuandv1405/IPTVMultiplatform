package tss.t.tsiptv.core.language

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier

actual fun languageDisplayName(tag: String, uiLanguage: String?): String? {
    val locale = NSLocale(localeIdentifier = (uiLanguage ?: "en").replace("-r", "-").replace('-', '_'))
    return locale.displayNameForKey(NSLocaleIdentifier, value = tag.replace('-', '_'))
        ?.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
}
