package tss.t.tsiptv.core.language

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.storage.KeyValueStorage

/**
 * Repository for managing language settings.
 *
 * @property storage The key-value storage used to persist language settings
 */
class LanguageRepository(private val storage: KeyValueStorage) {
    companion object {
        private const val KEY_LANGUAGE_CODE = "language_code"
    }

    /**
     * Get the current language settings.
     *
     * @return The current language settings
     */
    suspend fun getLanguageSettings(): LanguageSettings {
        val languageCode = storage.getString(KEY_LANGUAGE_CODE, "")
        return LanguageSettings(
            languageCode = languageCode.ifEmpty { null }
        )
    }

    /**
     * Set the language settings.
     *
     * @param settings The language settings to set
     */
    suspend fun setLanguageSettings(settings: LanguageSettings) {
        settings.languageCode?.let {
            storage.putString(KEY_LANGUAGE_CODE, it)
        } ?: run {
            // If languageCode is null, remove the key to use system default
            storage.remove(KEY_LANGUAGE_CODE)
        }
    }

    /**
     * Set the language by code.
     *
     * @param languageCode The language code to set, or null to use system default
     */
    suspend fun setLanguage(languageCode: String?) {
        setLanguageSettings(LanguageSettings(languageCode))
    }

    /**
     * Observe changes to the language settings.
     *
     * @return A flow of language settings
     */
    fun observeLanguageSettings(): Flow<LanguageSettings> {
        return storage.observeString(KEY_LANGUAGE_CODE, "").map { code ->
            LanguageSettings(languageCode = code.ifEmpty { null })
        }
    }

    /**
     * Get the current supported language.
     *
     * @return The current supported language
     */
    suspend fun getCurrentLanguage(): SupportedLanguage {
        val settings = getLanguageSettings()
        return SupportedLanguage.fromCode(settings.languageCode)
    }
}