package tss.t.tsiptv.core.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Composition local for providing the current locale.
 */
val LocalAppLocale = compositionLocalOf { SupportedLanguage.ENGLISH }

/**
 * Manager for handling locale changes in the app.
 *
 * @property languageRepository The repository for language settings
 */
class LocaleManager(private val languageRepository: LanguageRepository) {
    /**
     * Get the current language.
     *
     * @return The current language
     */
    suspend fun getCurrentLanguage(): SupportedLanguage {
        return languageRepository.getCurrentLanguage()
    }

    /**
     * Set the current language.
     *
     * @param language The language to set
     */
    suspend fun setLanguage(language: SupportedLanguage) {
        languageRepository.setLanguage(language.code)
    }

    /**
     * Observe changes to the current language.
     *
     * @return A flow of supported languages
     */
    fun observeCurrentLanguage(): Flow<SupportedLanguage> {
        return languageRepository.observeLanguageSettings().map { settings ->
            SupportedLanguage.fromCode(settings.languageCode)
        }
    }
}

/**
 * Composable function that provides the current locale to the composition.
 *
 * @param localeManager The locale manager
 * @param initialLanguage The initial language to use
 * @param content The content to provide the locale to
 */
@Composable
fun AppLocaleProvider(
    localeManager: LocaleManager,
    initialLanguage: SupportedLanguage,
    content: @Composable () -> Unit
) {
    // Observe the current language from the locale manager
    val currentLanguage by remember(localeManager) { 
        localeManager.observeCurrentLanguage() 
    }.collectAsState(initial = initialLanguage)

    CompositionLocalProvider(LocalAppLocale provides currentLanguage) {
        content()
    }
}
