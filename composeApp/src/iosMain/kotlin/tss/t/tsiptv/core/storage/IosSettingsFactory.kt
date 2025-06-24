package tss.t.tsiptv.core.storage

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of SettingsFactory.
 * This implementation uses NSUserDefaults to create a Settings instance.
 */
class IosSettingsFactory : SettingsFactory {
    /**
     * Creates a Settings instance.
     *
     * @param name The name of the settings
     * @return The created Settings instance
     */
    override fun createSettings(name: String): Settings {
        val userDefaults = NSUserDefaults(suiteName = name)
        return NSUserDefaultsSettings(userDefaults)
    }
}
