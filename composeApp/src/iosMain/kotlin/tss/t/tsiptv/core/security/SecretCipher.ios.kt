@file:OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)

package tss.t.tsiptv.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCKeySizeAES256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * iOS: a 64-byte key (32 encryption + 32 MAC) in the Keychain
 * (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, never synced or backed up to other devices).
 *
 * Kotlin/Native has no public AES-GCM (CommonCrypto's GCM is SPI, CryptoKit is Swift-only), so the
 * token is AES-256-CBC + HMAC-SHA256 (encrypt-then-MAC), which is also authenticated encryption:
 * `v1:` + base64url(iv[16] ‖ ciphertext ‖ mac[32]). Tokens never leave the device, so the format may
 * differ from the other platforms.
 */
private class KeychainSecretCipher : SecretCipher {
    private val keys: ByteArray by lazy { loadKey() }

    /**
     * The Keychain key. A new one is created **only** when the item does not exist; any other
     * Keychain error throws, so an in-memory-only key (which would make every stored URL
     * unreadable after a restart) is never used.
     */
    private fun loadKey(): ByteArray {
        val (status, bytes) = readKey()
        return when {
            status == errSecSuccess && bytes != null -> bytes
            status == errSecSuccess -> error("Keychain key has an unexpected size")
            status == errSecItemNotFound -> createKey()
            else -> error("Keychain read failed ($status)")
        }
    }
    private val encKey get() = keys.copyOfRange(0, 32)
    private val macKey get() = keys.copyOfRange(32, 64)

    override fun encrypt(plain: String): String {
        val iv = randomBytes(IV)
        val ct = crypt(kCCEncrypt.convert(), encKey, iv, plain.encodeToByteArray()) ?: error("encryption failed")
        val mac = hmac(macKey, iv + ct)
        return SecretCipher.PREFIX + Base64.UrlSafe.encode(iv + ct + mac)
    }

    override fun decryptResult(token: String): SecretCipher.DecryptResult {
        val bytes = try {
            if (!token.startsWith(SecretCipher.PREFIX)) null
            else Base64.UrlSafe.decode(token.removePrefix(SecretCipher.PREFIX)).takeIf { it.size >= IV + MAC + 16 }
        } catch (_: Throwable) {
            null
        } ?: return SecretCipher.DecryptResult.Invalid
        // Keychain not readable right now (e.g. before first unlock): transient, retry later.
        val mk = try { macKey } catch (_: Throwable) { return SecretCipher.DecryptResult.KeyUnavailable }
        val body = bytes.copyOfRange(0, bytes.size - MAC)
        val mac = bytes.copyOfRange(bytes.size - MAC, bytes.size)
        if (!constantTimeEquals(mac, hmac(mk, body))) return SecretCipher.DecryptResult.Invalid
        val plain = crypt(kCCDecrypt.convert(), encKey, body.copyOfRange(0, IV), body.copyOfRange(IV, body.size))
            ?: return SecretCipher.DecryptResult.Invalid
        return SecretCipher.DecryptResult.Ok(plain.decodeToString())
    }

    private fun crypt(op: UInt, key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray? = memScoped {
        val out = ByteArray(input.size + kCCBlockSizeAES128.toInt())
        val moved = alloc<platform.posix.size_tVar>()
        val status = key.usePinned { k ->
            iv.usePinned { v ->
                input.usePinned { i ->
                    out.usePinned { o ->
                        CCCrypt(
                            op, kCCAlgorithmAES.convert(), kCCOptionPKCS7Padding.convert(),
                            k.addressOf(0), kCCKeySizeAES256.convert(), v.addressOf(0),
                            i.addressOf(0), input.size.convert(), o.addressOf(0), out.size.convert(), moved.ptr,
                        )
                    }
                }
            }
        }
        if (status == kCCSuccess.convert<Int>()) out.copyOf(moved.value.toInt()) else null
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(MAC)
        key.usePinned { k ->
            data.usePinned { d ->
                out.usePinned { o ->
                    CCHmac(kCCHmacAlgSHA256.convert(), k.addressOf(0), key.size.convert(), d.addressOf(0), data.size.convert(), o.addressOf(0))
                }
            }
        }
        return out
    }

    private fun randomBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        out.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.convert(), it.addressOf(0)) }
        return out
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun baseQuery(): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(null, 6, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8))
        CFDictionaryAddValue(query, kSecAttrAccount, CFStringCreateWithCString(null, ACCOUNT, kCFStringEncodingUTF8))
        return query
    }

    /** (status, key bytes); bytes are null for a wrong-size item. */
    private fun readKey(): Pair<Int, ByteArray?> = memScoped {
        val query = baseQuery()
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped status to null
        val data: CFDataRef = result.value?.reinterpret() ?: return@memScoped status to null
        val length = CFDataGetLength(data).toInt()
        val bytes = ByteArray(length)
        val ptr = CFDataGetBytePtr(data)
        if (ptr != null) for (i in 0 until length) bytes[i] = ptr[i].toByte()
        CFRelease(data)
        status to bytes.takeIf { it.size == KEY_BYTES }
    }

    private fun createKey(): ByteArray {
        val bytes = randomBytes(KEY_BYTES)
        val query = baseQuery()
        val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) }
        CFDictionaryAddValue(query, kSecValueData, data)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val status = SecItemAdd(query, null)
        CFRelease(query)
        CFRelease(data)
        check(status == errSecSuccess) { "Keychain write failed ($status)" }
        return bytes
    }

    private companion object {
        const val SERVICE = "tss.t.tsiptv.addon-secrets"
        const val ACCOUNT = "v1"
        const val KEY_BYTES = 64
        const val IV = 16
        const val MAC = 32
    }
}

private val instance: SecretCipher by lazy { KeychainSecretCipher() }

actual fun createSecretCipher(): SecretCipher = instance
