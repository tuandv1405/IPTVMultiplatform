package tss.t.tsiptv.core.storage

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.util.prefs.Preferences

/**
 * Desktop implementation of SettingsFactory.
 * This implementation uses Java Preferences to create a Settings instance.
 */
class DesktopSettingsFactory : SettingsFactory {
    /**
     * Creates a Settings instance.
     *
     * @param name The name of the settings
     * @return The created Settings instance
     */
    override fun createSettings(name: String): Settings {
        val preferences = Preferences.userRoot().node(name)
        return PreferencesSettings(preferences)
    }
}
