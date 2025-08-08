package tss.t.tsiptv

import platform.UIKit.UIDevice
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSLocale
import platform.Foundation.NSBundle
import platform.Foundation.NSArray

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val isIOS: Boolean
        get() = true
}

actual fun getPlatform(): Platform = IOSPlatform()

/**
 * Platform-specific utilities for iOS.
 */
object IOSPlatformUtils {
    
    /**
     * Gets the current saved language code from NSUserDefaults.
     * This method retrieves the language code stored by the app.
     */
    fun getCurrentLanguageCode(): String? {
        return try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val languageCode = userDefaults.stringForKey("language_code")
            if (languageCode.isNullOrEmpty()) null else languageCode
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Sets the language code in NSUserDefaults.
     * This method stores the language code for persistence across app restarts.
     */
    fun setLanguageCode(languageCode: String?) {
        try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            if (languageCode != null) {
                userDefaults.setObject(languageCode, "language_code")
            } else {
                userDefaults.removeObjectForKey("language_code")
            }
            userDefaults.synchronize()
        } catch (e: Exception) {
            // Handle error silently
        }
    }
    
    /**
     * Gets the system locale language code.
     * This returns the device's current language setting.
     */
    fun getSystemLanguageCode(): String {
        return try {
            // Use NSUserDefaults to get preferred languages
            val userDefaults = NSUserDefaults.standardUserDefaults
            val preferredLanguages = userDefaults.arrayForKey("AppleLanguages") as? NSArray
            val firstLanguage = preferredLanguages?.objectAtIndex(0u) as? String
            // Extract language code (first 2 characters) using Kotlin string methods
            firstLanguage?.take(2) ?: "en"
        } catch (e: Exception) {
            "en"
        }
    }
    
    /**
     * Applies the locale change by updating NSUserDefaults.
     * Note: iOS apps typically require a restart to fully apply locale changes
     * to system-provided UI elements, but Compose content will update immediately.
     */
    fun applyLocaleChange(languageCode: String?) {
        setLanguageCode(languageCode)
        
        // For iOS, we store the preference and the app will use it
        // The Compose UI will update immediately through the language repository
        // System UI elements may require an app restart to fully update
    }
}
