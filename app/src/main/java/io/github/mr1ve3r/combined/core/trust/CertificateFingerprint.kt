package io.github.mr1ve3r.combined.core.trust

import java.security.MessageDigest
import java.security.cert.X509Certificate

object CertificateFingerprint {
    fun sha256(certificate: X509Certificate): String = hex(digest("SHA-256", certificate))
    fun sha1(certificate: X509Certificate): String = hex(digest("SHA-1", certificate))

    fun formatForDisplay(hexFingerprint: String): String = bytes(hexFingerprint).joinToString(":") { "%02X".format(it) }

    fun matchesSha256(expectedHex: String, certificate: X509Certificate): Boolean {
        val expected = runCatching { bytes(expectedHex) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, digest("SHA-256", certificate))
    }

    fun matchesAnySha256(expectedHexFingerprints: Set<String>, certificate: X509Certificate): Boolean {
        var matched = false
        expectedHexFingerprints.forEach { candidate ->
            if (matchesSha256(candidate, certificate)) {
                matched = true
            }
        }
        return matched
    }

    fun normalise(raw: String): String = raw.filter { !it.isWhitespace() && it != ':' && it != '-' }.lowercase()

    private fun digest(algorithm: String, certificate: X509Certificate): ByteArray =
        MessageDigest.getInstance(algorithm).digest(certificate.encoded)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun bytes(hexFingerprint: String): ByteArray {
        val cleaned = normalise(hexFingerprint)
        require(cleaned.length % 2 == 0) { "A hex fingerprint must have an even number of digits" }
        return ByteArray(cleaned.length / 2) { i ->
            val octet = cleaned.substring(i * 2, i * 2 + 2)
            octet.toIntOrNull(radix = 16)?.toByte() ?: throw IllegalArgumentException("Not hex: $octet")
        }
    }
}
