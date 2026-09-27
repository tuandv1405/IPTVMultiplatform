package tss.t.tsiptv.core.rating

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tss.t.tsiptv.AndroidPlatformUtils
import tss.t.tsiptv.TSAndroidApplication

/**
 * Google Play In-App Review for the automatic prompt, and the Play listing for
 * the button. On devices without Play (emulators, Huawei) both fail quietly.
 */
class AndroidAppReviewPlatform : AppReviewPlatform {

    override val canOpenStoreListing: Boolean = true

    override suspend fun requestInAppReview(): Boolean = withContext(Dispatchers.Main) {
        try {
            val activity = AndroidPlatformUtils.appContext as? Activity
                ?: return@withContext false
            if (activity.isFinishing) return@withContext false
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReview()
            manager.launchReview(activity, reviewInfo)
            true
        } catch (e: Exception) {
            // ReviewException without Play Services, or appContext not set yet.
            println("[AppReview] in-app review unavailable: ${e.message}")
            false
        }
    }

    override suspend fun openStoreListing(): Boolean = withContext(Dispatchers.Main) {
        val context = TSAndroidApplication.instance
        val packageName = context.packageName
        listOf(
            "market://details?id=$packageName",
            "https://play.google.com/store/apps/details?id=$packageName",
        ).any { url ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
    }
}

actual fun getAppReviewPlatform(): AppReviewPlatform = AndroidAppReviewPlatform()
