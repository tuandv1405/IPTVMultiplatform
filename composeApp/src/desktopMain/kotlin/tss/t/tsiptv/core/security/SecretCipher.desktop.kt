package tss.t.tsiptv.core.security

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES-256-GCM with a 32-byte random key stored in [keyFile], readable and writable only by the
 * current user (POSIX `rw-------`, or on Windows an ACL with a single entry for the owner).
 */
@OptIn(ExperimentalEncodingApi::class)
class FileKeySecretCipher(private val keyFile: Path) : SecretCipher {
    private val random = SecureRandom()
    private val key: SecretKeySpec by lazy { SecretKeySpec(loadOrCreateKey(), "AES") }

    /**
     * Reads the key, or creates it atomically: a temp file in the same directory is restricted to
     * the owner **before** the key bytes are written, then renamed into place. An existing key file
     * is never overwritten, not even one of the wrong size (that throws: replacing it would make
     * every stored URL unreadable).
     */
    @Synchronized
    private fun loadOrCreateKey(): ByteArray {
        if (Files.exists(keyFile)) return readExisting()
        val dir = keyFile.toAbsolutePath().parent
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "addon-secrets", ".tmp")
        try {
            restrictToOwner(tmp)
            val bytes = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
            Files.write(tmp, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
            try {
                Files.move(tmp, keyFile) // no REPLACE_EXISTING: fails if another process won the race
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                return readExisting()
            }
            restrictToOwner(keyFile)
            return bytes
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun readExisting(): ByteArray {
        val bytes = Files.readAllBytes(keyFile)
        check(bytes.size == KEY_BYTES) { "The addon key file has an unexpected size; it is left untouched." }
        return bytes
    }

    override fun encrypt(plain: String): String {
        val iv = ByteArray(SecretCipher.IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(SecretCipher.TAG_BITS, iv))
        return SecretCipher.PREFIX + Base64.UrlSafe.encode(iv + cipher.doFinal(plain.encodeToByteArray()))
    }

    override fun decryptResult(token: String): SecretCipher.DecryptResult {
        val bytes = try {
            if (!token.startsWith(SecretCipher.PREFIX)) null
            else Base64.UrlSafe.decode(token.removePrefix(SecretCipher.PREFIX)).takeIf { it.size > SecretCipher.IV_BYTES }
        } catch (_: Exception) {
            null
        } ?: return SecretCipher.DecryptResult.Invalid
        // Key file unreadable right now (I/O error, unexpected size left untouched): not a lost secret.
        val key = try { key } catch (_: Exception) { return SecretCipher.DecryptResult.KeyUnavailable }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(SecretCipher.TAG_BITS, bytes, 0, SecretCipher.IV_BYTES))
            SecretCipher.DecryptResult.Ok(cipher.doFinal(bytes, SecretCipher.IV_BYTES, bytes.size - SecretCipher.IV_BYTES).decodeToString())
        } catch (_: javax.crypto.AEADBadTagException) {
            SecretCipher.DecryptResult.Invalid
        } catch (_: Exception) {
            SecretCipher.DecryptResult.KeyUnavailable
        }
    }

    companion object {
        private const val KEY_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** `~/.tsiptv/addon-secrets.key`. */
        fun defaultKeyFile(): Path = Paths.get(System.getProperty("user.home"), ".tsiptv", "addon-secrets.key")

        /** Owner-only access. Best effort: a file system without either view keeps its defaults. */
        fun restrictToOwner(path: Path) {
            val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
            if (posix != null) {
                posix.setPermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
                return
            }
            val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
            val owner = acl.owner
            val entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                    AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.READ_ATTRIBUTES, AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.READ_NAMED_ATTRS, AclEntryPermission.WRITE_NAMED_ATTRS,
                    AclEntryPermission.READ_ACL, AclEntryPermission.WRITE_ACL, AclEntryPermission.DELETE,
                    AclEntryPermission.SYNCHRONIZE,
                )
                .build()
            acl.acl = listOf(entry)
        }
    }
}

private val instance: SecretCipher by lazy { FileKeySecretCipher(FileKeySecretCipher.defaultKeyFile()) }

actual fun createSecretCipher(): SecretCipher = instance
