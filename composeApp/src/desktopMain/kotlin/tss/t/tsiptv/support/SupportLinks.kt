package tss.t.tsiptv.support

import java.net.URLEncoder

/**
 * Where the desktop support promos send people (docs/desktop-build.md). The values come from
 * [DesktopSupportConfig], generated at build time from `TSIPTV_SUPPORT_*` settings.
 */
object SupportLinks {
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=tss.t.tsiptv"

    /** Ko-fi, PayPal.me, GitHub Sponsors…: an international way to give, or null. */
    val supportUrl: String? = DesktopSupportConfig.SUPPORT_URL.takeIf { it.startsWith("https://") }

    /** The bank account for a VietQR transfer is configured. */
    val hasBank: Boolean = DesktopSupportConfig.BANK_ID.isNotBlank() && DesktopSupportConfig.ACCOUNT_NO.isNotBlank()

    /** At least one way to give exists: only then do the support promo and its start screen show. */
    val canDonate: Boolean = supportUrl != null || hasBank

    val bankId: String = DesktopSupportConfig.BANK_ID
    val accountNo: String = DesktopSupportConfig.ACCOUNT_NO
    val accountName: String = DesktopSupportConfig.ACCOUNT_NAME

    /**
     * The VietQR image of the account from the public VietQR quick link service (img.vietqr.io): any
     * Vietnamese banking app scans it and fills in the account. No amount, so people choose.
     */
    val vietQrImageUrl: String? = if (!hasBank) null else buildString {
        append("https://img.vietqr.io/image/")
        append(encode(bankId)).append('-').append(encode(accountNo)).append("-compact2.png")
        append("?addInfo=").append(encode("Ung ho TS IPTV"))
        if (accountName.isNotBlank()) append("&accountName=").append(encode(accountName))
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
