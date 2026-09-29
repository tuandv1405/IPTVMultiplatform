package tss.t.tsiptv.core.stremio

import android.content.pm.ApplicationInfo
import tss.t.tsiptv.TSAndroidApplication

actual fun debugAddonBlocklistUrlOverride(): String? = try {
    val context = TSAndroidApplication.instance.applicationContext
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!debuggable) {
        null
    } else {
        // Looked up by name: the resource exists only in the debug build type.
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier("debug_addon_blocklist_url", "string", context.packageName)
        if (id == 0) null else context.getString(id).trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
} catch (_: Exception) {
    null
}
