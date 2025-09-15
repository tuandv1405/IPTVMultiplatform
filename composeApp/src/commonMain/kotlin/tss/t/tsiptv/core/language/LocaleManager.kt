package tss.t.tsiptv.core.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.ui.screens.login.provider.LocalAuthProvider

var customAppLocale by mutableStateOf<String?>(null)
expect object LocalAppLocale {
    val current: String @Composable get
    @Composable infix fun provides(value: String?): ProvidedValue<*>
}

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
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppLocale provides customAppLocale,
    ) {
        key(customAppLocale) {
            content()
        }
    }
}
