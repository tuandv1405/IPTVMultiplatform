package tss.t.tsiptv.core.rating

/** The store-specific half of asking for a rating. */
interface AppReviewPlatform {
    /** Whether a "Rate the app" button has a store listing to open. */
    val canOpenStoreListing: Boolean

    /**
     * Asks the store to show its native review card. Returns false when the flow
     * could not be requested at all. True does not mean a card was shown: the
     * store decides that, and never says.
     */
    suspend fun requestInAppReview(): Boolean

    /** Opens the app's store listing. Returns false when nothing could open it. */
    suspend fun openStoreListing(): Boolean
}

expect fun getAppReviewPlatform(): AppReviewPlatform
