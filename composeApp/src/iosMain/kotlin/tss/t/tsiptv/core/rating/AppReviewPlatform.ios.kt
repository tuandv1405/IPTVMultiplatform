package tss.t.tsiptv.core.rating

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationState
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
            // NS_ENUM, so Kotlin/Native exposes it as an enum class entry.
            .firstOrNull { it.activationState == UISceneActivationState.UISceneActivationStateForegroundActive }
            ?: return@withContext false
        SKStoreReviewController.requestReviewInScene(scene)
        true
    }

    override suspend fun openStoreListing(): Boolean = false
}

actual fun getAppReviewPlatform(): AppReviewPlatform = IosAppReviewPlatform()
