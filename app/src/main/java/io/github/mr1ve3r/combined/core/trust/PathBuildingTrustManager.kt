package io.github.mr1ve3r.combined.core.trust

import java.security.InvalidAlgorithmParameterException
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertStore
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathBuilderResult
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.X509TrustManager

class PathBuildingTrustManager(
    private val anchors: Set<TrustAnchor>,
    private val extraIntermediates: List<X509Certificate> = emptyList(),
    private val exposeAcceptedIssuers: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
) : X509TrustManager {
    init {
        require(anchors.isNotEmpty()) { "Path building needs at least one trust anchor" }
    }

    @Volatile
    var lastValidatedPath: List<X509Certificate>? = null
        private set

    @Volatile
    var lastAnchor: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val endEntity =
            chain.firstOrNull()
                ?: throw CertificateException("The server presented no certificate")

        endEntity.checkValidity(Date(clock()))
        requireUsableForServerAuth(endEntity)

        anchorMatching(endEntity)?.let { anchor ->
            lastValidatedPath = listOf(endEntity)
            lastAnchor = anchor
            return
        }

        val candidates = (chain.toList() + extraIntermediates).filterNot(::isAnchor)

        val parameters =
            PKIXBuilderParameters(anchors, X509CertSelector().apply { certificate = endEntity }).apply {
                isRevocationEnabled = false
                date = Date(clock())
                addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(candidates)))
            }

        val result =
            try {
                buildOnADeepStack(parameters)
            } catch (e: CertPathBuilderException) {
                throw translate(e)
            } catch (e: InvalidAlgorithmParameterException) {
                throw CertificateException("The trust anchors are unusable: ${e.message}", e)
            }

        val path = result.certPath.certificates.filterIsInstance<X509Certificate>()
        val anchor = result.trustAnchor.trustedCert
        requireAnchorMayIssue(anchor, path)
        requireStrongSignatures(path)

        lastValidatedPath = path
        lastAnchor = anchor
    }

    private fun requireUsableForServerAuth(endEntity: X509Certificate) {
        val usages =
            try {
                endEntity.extendedKeyUsage
            } catch (e: java.security.cert.CertificateParsingException) {
                throw CertificateException("The server's certificate has an unreadable extended key usage", e)
            } ?: return

        if (usages.none { it == SERVER_AUTH_OID || it == ANY_EXTENDED_KEY_USAGE_OID }) {
            throw CertificateException(
                "The server's certificate is not valid for authenticating a TLS server: its extended key usage is " +
                    usages.joinToString(),
            )
        }
    }

    private fun requireStrongSignatures(path: List<X509Certificate>) {
        path.forEach { certificate ->
            val algorithm = certificate.sigAlgName.uppercase()
            if (WEAK_SIGNATURE_MARKERS.any(algorithm::contains)) {
                throw CertificateException(
                    "${certificate.subjectX500Principal.name} is signed with ${certificate.sigAlgName}, " +
                        "which is too weak to establish who issued it",
                )
            }
        }
    }

    private fun requireAnchorMayIssue(anchor: X509Certificate?, path: List<X509Certificate>) {
        if (anchor == null || path.isEmpty()) return
        if (anchor.basicConstraints >= 0) return

        throw CertificateException(
            "The chain ends at ${anchor.subjectX500Principal.name}, which is not marked as a certificate " +
                "authority and so cannot vouch for another certificate",
        )
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String): Unit =
        throw CertificateException("This trust manager does not authenticate clients")

    override fun getAcceptedIssuers(): Array<X509Certificate> = if (exposeAcceptedIssuers) {
        anchors.mapNotNull(TrustAnchor::getTrustedCert).toTypedArray()
    } else {
        emptyArray()
    }

    private fun buildOnADeepStack(parameters: PKIXBuilderParameters): PKIXCertPathBuilderResult {
        var result: PKIXCertPathBuilderResult? = null
        var failure: Throwable? = null

        val worker =
            Thread(null, {
                try {
                    result = CertPathBuilder.getInstance("PKIX").build(parameters) as PKIXCertPathBuilderResult
                } catch (e: StackOverflowError) {
                    failure = CertificateException(
                        "The certificates available could not be searched for a path to a trusted one; " +
                            "the server's certificate is not accepted",
                        e,
                    )
                } catch (e: Throwable) {
                    failure = e
                }
            }, "cert-path-builder", SEARCH_STACK_BYTES)

        worker.start()
        try {
            worker.join()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CertificateException("Interrupted while checking the server's certificate", e)
        }

        failure?.let { throw it }
        return result ?: throw CertificateException("The certificate path search produced no result")
    }

    private fun isAnchor(certificate: X509Certificate): Boolean = anchorMatching(certificate) != null

    private fun anchorMatching(certificate: X509Certificate): X509Certificate? = anchors
        .mapNotNull(TrustAnchor::getTrustedCert)
        .firstOrNull { it == certificate }

    private fun translate(failure: CertPathBuilderException): CertificateException {
        val validatorFailure = rootValidatorFailure(failure)
        val reason = validatorFailure?.reason

        if (reason == CertPathValidatorException.BasicReason.EXPIRED) {
            return CertificateExpiredException(detailOf(failure, validatorFailure))
                .also { it.initCause(failure) }
        }

        return CertificateException(detailOf(failure, validatorFailure), failure)
    }

    private fun detailOf(failure: CertPathBuilderException, validatorFailure: CertPathValidatorException?): String =
        validatorFailure?.message?.takeIf(String::isNotBlank)
            ?: failure.message
            ?: "no certification path could be built"

    private fun rootValidatorFailure(failure: Throwable): CertPathValidatorException? {
        var current: Throwable? = failure
        while (current != null) {
            if (current is CertPathValidatorException) return current
            current = current.cause.takeIf { it !== current }
        }
        return null
    }

    companion object {
        private const val SEARCH_STACK_BYTES = 8L * 1024 * 1024
        private const val SERVER_AUTH_OID = "1.3.6.1.5.5.7.3.1"
        private const val ANY_EXTENDED_KEY_USAGE_OID = "2.5.29.37.0"
        private val WEAK_SIGNATURE_MARKERS = listOf("MD2", "MD4", "MD5", "SHA1")

        fun anchoredOn(
            certs: List<X509Certificate>,
            pool: List<X509Certificate> = emptyList(),
            exposeAcceptedIssuers: Boolean = true,
            clock: () -> Long = System::currentTimeMillis,
        ): PathBuildingTrustManager = PathBuildingTrustManager(
            anchors = certs.mapTo(mutableSetOf()) { TrustAnchor(it, null) },
            extraIntermediates = pool,
            exposeAcceptedIssuers = exposeAcceptedIssuers,
            clock = clock,
        )
    }
}
