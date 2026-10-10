package tss.t.tsiptv.utils

/** Public web pages the app links out to. Served from Firebase Hosting (web/public). */
object AppLinks {
    private const val SITE = "https://tsiptv-8bdd6.web.app"

    /** Contributor programme: policy, sign-up and playlist submission all live on the web. */
    const val CONTRIBUTOR_URL = "$SITE/contributor/"

    /** Linked from the plans screen (docs/prd-subscriptions.md §3.1, Play subscriptions policy). */
    const val TERMS_URL = "$SITE/terms/"
    const val PRIVACY_URL = "$SITE/privacy/"
}
