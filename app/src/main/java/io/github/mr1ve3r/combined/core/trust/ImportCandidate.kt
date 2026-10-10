package io.github.mr1ve3r.combined.core.trust

import java.security.cert.X509Certificate

data class ImportCandidate(
    val summary: CertificateSummary,
    val pem: String,
    val warnings: List<CertificateWarning>,
    val chainPosition: Int? = null,
) {
    companion object {
        fun of(
            certificates: List<X509Certificate>,
            now: Long,
            alreadyImportedIds: Set<String> = emptySet(),
            withChainPositions: Boolean = false,
        ): List<ImportCandidate> = certificates.mapIndexed { index, certificate ->
            val summary = CertificateSummary.of(certificate)
            ImportCandidate(
                summary = summary,
                pem = CertificateParser.toPem(certificate),
                warnings = CertificateValidator.validate(
                    summary = summary,
                    now = now,
                    alreadyImported = summary.id in alreadyImportedIds,
                ),
                chainPosition = index.takeIf { withChainPositions },
            )
        }
    }
}
