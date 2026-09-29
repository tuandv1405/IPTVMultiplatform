package tss.t.tsiptv.core.stremio

/**
 * QC only: a blocklist URL to use instead of the production one, or null. Android returns a value
 * only in a **debuggable** build whose `debug_addon_blocklist_url` resource (defined for the debug
 * build type only, see composeApp/build.gradle.kts) is non-empty. Desktop and iOS: always null.
 */
expect fun debugAddonBlocklistUrlOverride(): String?
