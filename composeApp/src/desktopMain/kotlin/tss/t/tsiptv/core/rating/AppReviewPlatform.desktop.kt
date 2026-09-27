package tss.t.tsiptv.core.rating

/** The desktop build is not distributed through a store, so there is nothing to rate. */
class DesktopAppReviewPlatform : AppReviewPlatform {
    override val canOpenStoreListing: Boolean = false
    override suspend fun requestInAppReview(): Boolean = false
    override suspend fun openStoreListing(): Boolean = false
}

actual fun getAppReviewPlatform(): AppReviewPlatform = DesktopAppReviewPlatform()
