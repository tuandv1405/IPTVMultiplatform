package tss.t.tsiptv.core.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current

        if (default == null) {
            default = Locale.getDefault()
        }

        val new = when (value) {
            null -> default!!
            else -> Locale(value)
        }
        Locale.setDefault(new)
        configuration.setLocale(new)
        val currentContext = LocalContext.current
        val newContext = currentContext.createConfigurationContext(configuration)
        LocalContext.provides(newContext)
        return LocalConfiguration.provides(configuration)
    }
}