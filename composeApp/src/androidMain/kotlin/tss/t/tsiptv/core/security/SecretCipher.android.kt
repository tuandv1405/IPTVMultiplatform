package tss.t.tsiptv.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** AES-256-GCM with a non-exportable key in the Android Keystore. */
@OptIn(ExperimentalEncodingApi::class)
private class AndroidKeystoreSecretCipher : SecretCipher {
    private val lock = Any()

    private fun key(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore generates the IV (callers may not supply one for GCM keys).
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == SecretCipher.IV_BYTES) { "Unexpected IV size" }
        val sealed = cipher.doFinal(plain.encodeToByteArray())
        return SecretCipher.PREFIX + Base64.UrlSafe.encode(iv + sealed)
    }

    override fun decryptResult(token: String): SecretCipher.DecryptResult {
        val bytes = parse(token) ?: return SecretCipher.DecryptResult.Invalid
        // Keystore not usable right now (still starting, locked): transient, retry later.
        val key = try { key() } catch (_: Exception) { return SecretCipher.DecryptResult.KeyUnavailable }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(SecretCipher.TAG_BITS, bytes, 0, SecretCipher.IV_BYTES))
            SecretCipher.DecryptResult.Ok(cipher.doFinal(bytes, SecretCipher.IV_BYTES, bytes.size - SecretCipher.IV_BYTES).decodeToString())
        } catch (_: javax.crypto.AEADBadTagException) {
            SecretCipher.DecryptResult.Invalid
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            SecretCipher.DecryptResult.Invalid
        } catch (_: Exception) {
            SecretCipher.DecryptResult.KeyUnavailable
        }
    }

    private fun parse(token: String): ByteArray? = try {
        if (!token.startsWith(SecretCipher.PREFIX)) null
        else Base64.UrlSafe.decode(token.removePrefix(SecretCipher.PREFIX)).takeIf { it.size > SecretCipher.IV_BYTES }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tsiptv_addon_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private val instance: SecretCipher by lazy { AndroidKeystoreSecretCipher() }

actual fun createSecretCipher(): SecretCipher = instance
