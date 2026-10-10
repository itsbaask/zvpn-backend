package io.github.mr1ve3r.combined.core.trust

import io.github.mr1ve3r.combined.engine.TrustPolicy

object TrustPreflight {
    fun check(
        policy: TrustPolicy,
        selectedCertificateIds: List<String>,
        availableCertificates: Map<String, CertificateSummary>,
        pinnedFingerprints: Set<String>,
        now: Long,
        storeSize: Int = Int.MAX_VALUE,
    ): PreflightReport {
        val blocking = mutableListOf<PreflightProblem>()
        val confirmations = mutableListOf<PreflightProblem>()

        val missing = selectedCertificateIds.filterNot(availableCertificates::containsKey)
        if (missing.isNotEmpty()) {
            blocking += PreflightProblem.CertificatesMissing(missing)
        }

        if (CertificateValidator.requiresCertificateAuthority(policy) && selectedCertificateIds.isEmpty()) {
            blocking += PreflightProblem.NoCertificatesSelected(policy)
        }

        if (CertificateValidator.consultsWholeStore(policy) && storeSize == 0) {
            blocking += PreflightProblem.StoreIsEmpty
        }

        if (policy == TrustPolicy.PIN_LEAF && pinnedFingerprints.isEmpty()) {
            blocking += PreflightProblem.NoPinsConfigured
        }

        if (!CertificateValidator.consultsSelectedCertificates(policy) &&
            !CertificateValidator.consultsWholeStore(policy) &&
            selectedCertificateIds.isNotEmpty()
        ) {
            confirmations += PreflightProblem.CertificatesIgnoredByPolicy(policy, selectedCertificateIds)
        }

        if (CertificateValidator.consultsWholeStore(policy) && storeSize > 0 && storeSize != Int.MAX_VALUE) {
            confirmations += PreflightProblem.WholeStoreIsTrusted(storeSize)
        }

        pinnedFingerprints
            .mapNotNull { availableCertificates[CertificateFingerprint.normalise(it)] }
            .filter { it.isCa }
            .forEach { confirmations += PreflightProblem.PinnedCertificateIsCa(it.id, it.subjectCn) }

        if (CertificateValidator.requiresCertificateAuthority(policy)) {
            selectedCertificateIds
                .mapNotNull(availableCertificates::get)
                .filterNot { it.isCa }
                .forEach { confirmations += PreflightProblem.AnchorIsNotACertificateAuthority(it.id, it.subjectCn) }
        }

        selectedCertificateIds
            .mapNotNull(availableCertificates::get)
            .filter { it.notAfter < now }
            .forEach { confirmations += PreflightProblem.CertificateExpired(it.id, it.subjectCn, it.notAfter) }

        return PreflightReport(blocking = blocking, confirmations = confirmations)
    }
}

data class PreflightReport(
    val blocking: List<PreflightProblem>,
    val confirmations: List<PreflightProblem>,
) {
    val canConnect: Boolean get() = blocking.isEmpty()
    val needsConfirmation: Boolean get() = confirmations.isNotEmpty()
    val isClean: Boolean get() = canConnect && !needsConfirmation
}

sealed interface PreflightProblem {
    val messageKey: String

    data class CertificatesMissing(val ids: List<String>) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.certificates_missing"
    }

    data class NoCertificatesSelected(val policy: TrustPolicy) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.no_certificates_selected"
    }

    data class CertificatesIgnoredByPolicy(val policy: TrustPolicy, val ids: List<String>) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.certificates_ignored_by_policy"
    }

    data class PinnedCertificateIsCa(val id: String, val subjectCn: String?) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.pinned_certificate_is_ca"
    }

    data class AnchorIsNotACertificateAuthority(val id: String, val subjectCn: String?) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.anchor_is_not_a_ca"
    }

    data object NoPinsConfigured : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.no_pins_configured"
    }

    data object StoreIsEmpty : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.store_is_empty"
    }

    data class WholeStoreIsTrusted(val certificateCount: Int) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.whole_store_is_trusted"
    }

    data class CertificateExpired(
        val id: String,
        val subjectCn: String?,
        val notAfter: Long,
    ) : PreflightProblem {
        override val messageKey: String get() = "trust.preflight.certificate_expired"
    }
}
