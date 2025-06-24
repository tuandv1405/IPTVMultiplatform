package tss.t.tsiptv.core.storage

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Android implementation of SettingsFactory.
 * This implementation uses SharedPreferences to create a Settings instance.
 *
 * @property context The application context
 */
class AndroidSettingsFactory(private val context: Context) : SettingsFactory {
    /**
     * Creates a Settings instance.
     *
     * @param name The name of the settings
     * @return The created Settings instance
     */
    override fun createSettings(name: String): Settings {
        val sharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return SharedPreferencesSettings(sharedPreferences)
    }
}
