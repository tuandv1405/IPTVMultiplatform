package tss.t.tsiptv.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import tss.t.tsiptv.TSAndroidApplication

/**
 * Android implementation of UrlOpener.
 *
 * Android TV images resolve `ACTION_VIEW https` to a do-nothing stub
 * (`com.android.tv.frameworkpackagestubs/.Stubs$BrowserStub`): the intent "succeeds" but nothing
 * is shown. That stub counts as "no browser", so callers show the link as text instead.
 * Package visibility (API 30+) needs the VIEW/BROWSABLE `<queries>` entry in the manifest.
 */
class AndroidUrlOpener : UrlOpener {

    override suspend fun openUrl(url: String): Boolean {
        return try {
            val context = TSAndroidApplication.instance.applicationContext
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Only the stub resolves: nothing visible would happen.
            if (resolvesOnlyToStub(intent)) return false
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun canHandleUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            realHandlers(intent).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun realHandlers(intent: Intent): List<String> {
        val pm = TSAndroidApplication.instance.applicationContext.packageManager
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it !in STUB_PACKAGES }
    }

    /** True only when something resolves and all of it is a stub (unknown visibility → try). */
    private fun resolvesOnlyToStub(intent: Intent): Boolean {
        val pm = TSAndroidApplication.instance.applicationContext.packageManager
        val all = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).mapNotNull { it.activityInfo?.packageName }
        return all.isNotEmpty() && all.all { it in STUB_PACKAGES }
    }

    private companion object {
        val STUB_PACKAGES = setOf("com.android.tv.frameworkpackagestubs")
    }
}

/**
 * Android implementation of getUrlOpener.
 */
actual fun getUrlOpener(): UrlOpener = AndroidUrlOpener()
