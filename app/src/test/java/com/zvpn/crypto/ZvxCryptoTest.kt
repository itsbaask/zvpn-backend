package com.zvpn.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZvxCryptoTest {
    private val sampleConfig =
        "vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none&security=reality#Node"

    private fun request(password: String?, expiresAt: Long? = null) = ZvxSealRequest(
        configPayload = sampleConfig,
        nameEn = "Berlin Fast",
        nameFa = "برلین سریع",
        note = "Join @mychannel for updates. 50GB cap.",
        expiresAt = expiresAt,
        password = password,
    )

    @Test
    fun roundtripWithoutPassword() {
        val file = ZvxCrypto.seal(request(password = null))
        assertEquals(sampleConfig, ZvxCrypto.open(file, password = null))
    }

    @Test
    fun roundtripWithPassword() {
        val file = ZvxCrypto.seal(request(password = "correct horse battery"))
        assertEquals(sampleConfig, ZvxCrypto.open(file, password = "correct horse battery"))
    }

    @Test
    fun peekExposesMetadataButNotConfig() {
        val file = ZvxCrypto.seal(request(password = "secret"))
        val meta = ZvxCrypto.peek(file)
        assertTrue(meta.passwordProtected)
        assertEquals("Berlin Fast", meta.nameEn)
        assertEquals("برلین سریع", meta.nameFa)
        assertTrue(meta.note.contains("mychannel"))
        assertFalse(meta.isExpired)
    }

    @Test
    fun newFilesUseVersionTwo() {
        val file = ZvxCrypto.seal(request(password = null))
        assertEquals("ZVX1", String(file.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(2, file[4].toInt())
    }

    @Test
    fun configIsNotStoredAsPlainText() {
        val file = String(ZvxCrypto.seal(request(password = null)), Charsets.ISO_8859_1)
        assertFalse(file.contains("example.com"))
        assertFalse(file.contains("vless://"))
    }

    @Test(expected = ZvxWrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val file = ZvxCrypto.seal(request(password = "the-real-password"))
        ZvxCrypto.open(file, password = "not-the-password")
    }

    @Test(expected = ZvxPasswordRequiredException::class)
    fun passwordProtectedRequiresPassword() {
        val file = ZvxCrypto.seal(request(password = "pw"))
        ZvxCrypto.open(file, password = null)
    }

    @Test(expected = ZvxTamperException::class)
    fun tamperedFileIsRejected() {
        val file = ZvxCrypto.seal(request(password = null))
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        ZvxCrypto.open(file, password = null)
    }

    @Test(expected = ZvxTamperException::class)
    fun truncatedFileIsRejected() {
        val file = ZvxCrypto.seal(request(password = null))
        ZvxCrypto.open(file.copyOfRange(0, file.size - 8), password = null)
    }

    @Test(expected = ZvxTamperException::class)
    fun otherFilesAreRejected() {
        ZvxCrypto.peek("{\"outbounds\":[]}".toByteArray())
    }

    @Test(expected = ZvxVersionException::class)
    fun unknownVersionIsRejected() {
        val file = ZvxCrypto.seal(request(password = null))
        file[4] = 0x7F
        ZvxCrypto.open(file, password = null)
    }

    @Test
    fun filesFromOlderVersionsAreReportedAsLegacy() {
        val file = "ZVX1".toByteArray(Charsets.US_ASCII) + byteArrayOf(1) + ByteArray(64) { it.toByte() }
        assertThrows(ZvxLegacyException::class.java) { ZvxCrypto.peek(file) }
        assertThrows(ZvxLegacyException::class.java) { ZvxCrypto.open(file, password = null) }
    }

    @Test(expected = ZvxExpiredException::class)
    fun expiredFileIsRejected() {
        val file = ZvxCrypto.seal(request(password = null, expiresAt = System.currentTimeMillis() - 1000))
        ZvxCrypto.open(file, password = null)
    }

    @Test
    fun differentSealsProduceDifferentCiphertext() {
        val a = ZvxCrypto.seal(request(password = null))
        val b = ZvxCrypto.seal(request(password = null))
        assertFalse(a.contentEquals(b))
    }
}
