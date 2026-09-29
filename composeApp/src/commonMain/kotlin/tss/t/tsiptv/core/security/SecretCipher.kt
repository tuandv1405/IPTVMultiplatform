package tss.t.tsiptv.core.security

/**
 * Encrypts small secrets at rest (addon transport URLs can embed API keys, PRD §8).
 *
 * AES-256-GCM with a random 96-bit IV per call and a 128-bit tag. Token format:
 * `v1:` + base64url(iv ‖ ciphertext ‖ tag), so the key can be rotated later.
 * The key lives in the Android Keystore, the iOS Keychain, or (desktop) a file in the app data
 * directory readable only by the current user.
 */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** null if the token is malformed, was tampered with, or the key is gone. Never throws. */
    fun decrypt(token: String): String? = (decryptResult(token) as? DecryptResult.Ok)?.plain

    /**
     * Like [decrypt], but tells a **lost** secret (malformed token, authentication failure: it will
     * never decrypt) from a **transient** failure (the key store is not available right now, e.g.
     * locked or still starting: retry later). Never throws.
     */
    fun decryptResult(token: String): DecryptResult

    sealed interface DecryptResult {
        data class Ok(val plain: String) : DecryptResult {
            override fun toString(): String = "Ok(<redacted>)"
        }
        /** Will never decrypt (tampered, malformed, encrypted with another key). */
        data object Invalid : DecryptResult
        /** The key could not be loaded now; the token may still be fine. */
        data object KeyUnavailable : DecryptResult
    }

    companion object {
        const val PREFIX = "v1:"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** Platform implementation (`androidMain`, `iosMain`, `desktopMain`). */
expect fun createSecretCipher(): SecretCipher
