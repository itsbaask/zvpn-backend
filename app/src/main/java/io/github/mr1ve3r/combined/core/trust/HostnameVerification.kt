package io.github.mr1ve3r.combined.core.trust

import java.security.cert.X509Certificate

object HostnameVerification {
    private const val DNS_PREFIX = "DNS:"
    private const val IP_PREFIX = "IP:"

    fun verify(certificate: X509Certificate, expectedHostname: String): HostnameVerificationResult {
        val expected = normalise(expectedHostname)
        val presented = CertificateSummary.subjectAltNamesOf(certificate)
        val candidates = presented.ifEmpty { commonNameFallback(certificate) }

        val matched =
            candidates.any { candidate ->
                when {
                    candidate.startsWith(DNS_PREFIX) -> dnsMatches(candidate.removePrefix(DNS_PREFIX), expected)
                    candidate.startsWith(IP_PREFIX) -> normalise(candidate.removePrefix(IP_PREFIX)) == expected
                    else -> dnsMatches(candidate, expected)
                }
            }

        return if (matched) {
            HostnameVerificationResult.Matched
        } else {
            HostnameVerificationResult.Mismatch(expected = expectedHostname, presented = candidates)
        }
    }

    internal fun dnsMatches(certificateName: String, expected: String): Boolean {
        val name = normalise(certificateName)
        if (name == expected) {
            return true
        }
        if (!name.startsWith("*.")) {
            return false
        }
        val suffix = name.removePrefix("*.")
        if (suffix.isEmpty() || !expected.endsWith(".$suffix")) {
            return false
        }
        val label = expected.dropLast(suffix.length + 1)
        return label.isNotEmpty() && !label.contains('.')
    }

    private fun commonNameFallback(certificate: X509Certificate): List<String> =
        CertificateSummary.of(certificate).subjectCn?.let { listOf("DNS:$it") }.orEmpty()

    private fun normalise(host: String): String = host.trim().trimEnd('.').lowercase()
}

sealed interface HostnameVerificationResult {
    data object Matched : HostnameVerificationResult

    data class Mismatch(
        val expected: String,
        val presented: List<String>,
    ) : HostnameVerificationResult
}
