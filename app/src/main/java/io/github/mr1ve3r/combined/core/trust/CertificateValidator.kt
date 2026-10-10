package io.github.mr1ve3r.combined.core.trust

import io.github.mr1ve3r.combined.engine.TrustPolicy
import java.util.concurrent.TimeUnit

object CertificateValidator {
    val EXPIRY_WARNING_WINDOW_MS: Long = TimeUnit.DAYS.toMillis(30)
    const val MINIMUM_RSA_BITS: Int = 2048
    private val WEAK_SIGNATURE_MARKERS = listOf("MD2", "MD5", "SHA1")

    fun validate(
        summary: CertificateSummary,
        now: Long,
        intendedPolicy: TrustPolicy? = null,
        alreadyImported: Boolean = false,
    ): List<CertificateWarning> = buildList {
        when {
            summary.notAfter < now -> add(CertificateWarning.Expired(summary.notAfter))
            summary.notAfter - now < EXPIRY_WARNING_WINDOW_MS ->
                add(CertificateWarning.ExpiringSoon(TimeUnit.MILLISECONDS.toDays(summary.notAfter - now)))
        }
        if (summary.notBefore > now) {
            add(CertificateWarning.NotYetValid(summary.notBefore))
        }
        if (intendedPolicy != null && !summary.isCa && requiresCertificateAuthority(intendedPolicy)) {
            add(CertificateWarning.NotACertificateAuthority(intendedPolicy))
        }
        summary.publicKeyBits?.let { bits ->
            if (bits < MINIMUM_RSA_BITS) {
                add(CertificateWarning.WeakKey(bits))
            }
        }
        if (WEAK_SIGNATURE_MARKERS.any { summary.signatureAlgorithm.uppercase().contains(it) }) {
            add(CertificateWarning.WeakSignature(summary.signatureAlgorithm))
        }
        if (alreadyImported) {
            add(CertificateWarning.AlreadyImported)
        }
    }

    fun consultsSelectedCertificates(policy: TrustPolicy): Boolean =
        policy == TrustPolicy.CUSTOM_ONLY || policy == TrustPolicy.SYSTEM_PLUS_CUSTOM

    fun requiresCertificateAuthority(policy: TrustPolicy): Boolean =
        policy == TrustPolicy.CUSTOM_ONLY || policy == TrustPolicy.SYSTEM_PLUS_CUSTOM

    fun consultsWholeStore(policy: TrustPolicy): Boolean = policy == TrustPolicy.STORE_AUTO

    fun buildsAChain(policy: TrustPolicy): Boolean = requiresCertificateAuthority(policy) || consultsWholeStore(policy)
}

sealed interface CertificateWarning {
    val messageKey: String

    data class Expired(val notAfter: Long) : CertificateWarning {
        override val messageKey: String get() = "trust.warning.expired"
    }

    data class ExpiringSoon(val daysRemaining: Long) : CertificateWarning {
        override val messageKey: String get() = "trust.warning.expiring_soon"
    }

    data class NotYetValid(val notBefore: Long) : CertificateWarning {
        override val messageKey: String get() = "trust.warning.not_yet_valid"
    }

    data class NotACertificateAuthority(val policy: TrustPolicy) : CertificateWarning {
        override val messageKey: String get() = "trust.warning.not_a_ca"
    }

    data class WeakKey(val bits: Int) : CertificateWarning {
        override val messageKey: String get() = "trust.warning.weak_key"
    }

    data class WeakSignature(val algorithm: String) : CertificateWarning {
        override val messageKey: String get() = "trust.warning.weak_signature"
    }

    data object AlreadyImported : CertificateWarning {
        override val messageKey: String get() = "trust.warning.already_imported"
    }
}
