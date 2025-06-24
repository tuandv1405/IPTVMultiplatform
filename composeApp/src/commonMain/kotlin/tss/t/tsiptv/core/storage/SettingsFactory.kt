package tss.t.tsiptv.core.storage

import com.russhwolf.settings.Settings

/**
 * Interface for creating a Settings instance.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface SettingsFactory {
    /**
     * Creates a Settings instance.
     *
     * @param name The name of the settings
     * @return The created Settings instance
     */
    fun createSettings(name: String): Settings
}
