package tss.t.tsiptv.core.rating

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene

/**
 * StoreKit's review card for the automatic prompt. The button stays hidden until
 * the app has an App Store ID to build a listing URL from.
 */
class IosAppReviewPlatform : AppReviewPlatform {

    override val canOpenStoreListing: Boolean = false

    override suspend fun requestInAppReview(): Boolean = withContext(Dispatchers.Main) {
        val scene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            // NSInteger typedef, so Kotlin/Native exposes the values as top-level Long constants.
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
            ?: return@withContext false
        SKStoreReviewController.requestReviewInScene(scene)
        true
    }

    override suspend fun openStoreListing(): Boolean = false
}

actual fun getAppReviewPlatform(): AppReviewPlatform = IosAppReviewPlatform()
