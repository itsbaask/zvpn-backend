package io.github.mr1ve3r.combined.engine.sstp

import io.github.mr1ve3r.combined.core.trust.CertificateSummary
import java.security.cert.X509Certificate

interface CertificateSource {
    suspend fun certificatesFor(ids: List<String>): List<X509Certificate>
    suspend fun summariesFor(ids: List<String>): Map<String, CertificateSummary>
    suspend fun allCertificates(): List<X509Certificate> = emptyList()
    suspend fun allSummaries(): Map<String, CertificateSummary> = emptyMap()

    companion object {
        val EMPTY: CertificateSource =
            object : CertificateSource {
                override suspend fun certificatesFor(ids: List<String>): List<X509Certificate> = emptyList()
                override suspend fun summariesFor(ids: List<String>): Map<String, CertificateSummary> = emptyMap()
            }
    }
}
