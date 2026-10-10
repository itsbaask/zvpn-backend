package io.github.mr1ve3r.combined.core.trust

import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

data class CertificateSummary(
    val id: String,
    val subjectCn: String?,
    val subjectDn: String,
    val issuerDn: String,
    val serialNumber: String,
    val notBefore: Long,
    val notAfter: Long,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val isCa: Boolean,
    val keyUsage: String?,
    val subjectAltNames: List<String>,
    val publicKeyBits: Int?,
    val signatureAlgorithm: String,
) {
    val isSelfSigned: Boolean get() = subjectDn == issuerDn

    companion object {
        fun of(certificate: X509Certificate): CertificateSummary {
            val sha256 = CertificateFingerprint.sha256(certificate)
            return CertificateSummary(
                id = sha256,
                subjectCn = commonNameOf(certificate.subjectX500Principal.name),
                subjectDn = certificate.subjectX500Principal.name,
                issuerDn = certificate.issuerX500Principal.name,
                serialNumber = certificate.serialNumber.toString(SERIAL_RADIX),
                notBefore = certificate.notBefore.time,
                notAfter = certificate.notAfter.time,
                sha256Fingerprint = sha256,
                sha1Fingerprint = CertificateFingerprint.sha1(certificate),
                isCa = certificate.basicConstraints != NOT_A_CA,
                keyUsage = keyUsageOf(certificate),
                subjectAltNames = subjectAltNamesOf(certificate),
                publicKeyBits = publicKeyBitsOf(certificate),
                signatureAlgorithm = certificate.sigAlgName,
            )
        }

        fun subjectAltNamesOf(certificate: X509Certificate): List<String> = runCatching { certificate.subjectAlternativeNames }
            .getOrNull()
            .orEmpty()
            .mapNotNull { entry ->
                val type = entry.getOrNull(0) as? Int ?: return@mapNotNull null
                val value = entry.getOrNull(1)?.toString() ?: return@mapNotNull null
                when (type) {
                    SAN_DNS -> "DNS:$value"
                    SAN_IP -> "IP:$value"
                    else -> null
                }
            }

        private fun commonNameOf(distinguishedName: String): String? = distinguishedName
            .split(',')
            .map(String::trim)
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)

        private fun keyUsageOf(certificate: X509Certificate): String? {
            val usage = certificate.keyUsage ?: return null
            return KEY_USAGE_NAMES
                .filterIndexed { index, _ -> index < usage.size && usage[index] }
                .takeIf(List<String>::isNotEmpty)
                ?.joinToString(", ")
        }

        private fun publicKeyBitsOf(certificate: X509Certificate): Int? = when (val key = certificate.publicKey) {
            is RSAPublicKey -> key.modulus.bitLength()
            else -> null
        }

        private const val SERIAL_RADIX = 16
        private const val NOT_A_CA = -1
        private const val SAN_DNS = 2
        private const val SAN_IP = 7

        private val KEY_USAGE_NAMES =
            listOf(
                "digitalSignature",
                "nonRepudiation",
                "keyEncipherment",
                "dataEncipherment",
                "keyAgreement",
                "keyCertSign",
                "cRLSign",
                "encipherOnly",
                "decipherOnly",
            )
    }
}
