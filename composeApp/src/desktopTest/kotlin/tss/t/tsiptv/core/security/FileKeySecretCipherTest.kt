package tss.t.tsiptv.core.security

import java.nio.file.Files
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AC-S21: the stored token never contains the plain transport URL. */
class FileKeySecretCipherTest {
    private val dir = Files.createTempDirectory("tsiptv-cipher")
    private val keyFile = dir.resolve("sub").resolve("addon-secrets.key")

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun roundTripWithRandomIvAndNoPlaintext() {
        val cipher = FileKeySecretCipher(keyFile)
        val url = "https://addon.example.org/eyJ0b2tlbiI6InNlY3JldCJ9/manifest.json?key=abc"
        val a = cipher.encrypt(url)
        val b = cipher.encrypt(url)
        assertTrue(a.startsWith("v1:"))
        assertNotEquals(a, b)
        assertFalse("addon.example.org" in a)
        assertFalse("secret" in a)
        assertEquals(url, cipher.decrypt(a))
        assertEquals(url, cipher.decrypt(b))
        // A second instance (app restart) reads the same key.
        assertEquals(url, FileKeySecretCipher(keyFile).decrypt(a))
    }

    @Test
    fun tamperedMalformedOrForeignTokensAreRejected() {
        val cipher = FileKeySecretCipher(keyFile)
        val token = cipher.encrypt("https://a.example.org/manifest.json")
        val flipped = token.dropLast(2) + (if (token[token.length - 2] == 'A') "B" else "A") + token.last()
        assertNull(cipher.decrypt(flipped))
        assertNull(cipher.decrypt("v1:"))
        assertNull(cipher.decrypt("not a token"))
        assertNull(cipher.decrypt("v1:!!!"))
        val other = FileKeySecretCipher(dir.resolve("other.key"))
        assertNull(other.decrypt(token))
    }

    @Test
    fun aWrongSizeKeyFileIsNeverOverwritten() {
        Files.createDirectories(keyFile.parent)
        Files.write(keyFile, ByteArray(7) { 1 })
        val cipher = FileKeySecretCipher(keyFile)
        assertTrue(runCatching { cipher.encrypt("x") }.isFailure)
        assertNull(cipher.decrypt("v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
        assertEquals(7L, Files.size(keyFile))
        assertTrue(Files.list(keyFile.parent).use { s -> s.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun keyFileIsOwnerOnly() {
        FileKeySecretCipher(keyFile).encrypt("x")
        assertEquals(32L, Files.size(keyFile))
        val posix = Files.getFileAttributeView(keyFile, PosixFileAttributeView::class.java)
        if (posix != null) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                posix.readAttributes().permissions(),
            )
        } else {
            val acl = Files.getFileAttributeView(keyFile, AclFileAttributeView::class.java)!!
            val owner = acl.owner
            assertTrue(acl.acl.isNotEmpty())
            assertTrue(acl.acl.all { it.principal() == owner })
        }
    }
}
