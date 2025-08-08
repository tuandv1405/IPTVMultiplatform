package tss.t.tsiptv.core.language

/**
 * Represents the language settings for the application.
 */
data class LanguageSettings(
    /**
     * The language code (e.g., "en", "es", "fr", "de", "vi").
     * If null, the system default language will be used.
     */
    val languageCode: String? = null,
)

/**
 * Enum representing the supported languages in the application.
 *
 * @property code The ISO language code
 * @property displayName The human-readable name of the language
 */
enum class SupportedLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    VIETNAMESE("vi", "Tiếng Việt");

    companion object {
        /**
         * Get a SupportedLanguage by its code.
         *
         * @param code The language code
         * @return The corresponding SupportedLanguage, or ENGLISH if not found
         */
        fun fromCode(code: String?): SupportedLanguage {
            return values().find { it.code == code } ?: ENGLISH
        }
    }
}
