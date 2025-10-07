package tss.t.tsiptv.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import tss.t.tsiptv.TSAndroidApplication

/**
 * Android implementation of UrlOpener.
 */
class AndroidUrlOpener : UrlOpener {
    
    override suspend fun openUrl(url: String): Boolean {
        return try {
            val context = TSAndroidApplication.instance.applicationContext
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun canHandleUrl(url: String): Boolean {
        return try {
            val context = TSAndroidApplication.instance.applicationContext
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            activities.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Android implementation of getUrlOpener.
 */
actual fun getUrlOpener(): UrlOpener = AndroidUrlOpener()