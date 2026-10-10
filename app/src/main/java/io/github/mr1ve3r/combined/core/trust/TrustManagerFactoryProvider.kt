package io.github.mr1ve3r.combined.core.trust

import io.github.mr1ve3r.combined.engine.TrustPolicy
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object TrustManagerFactoryProvider {
    fun create(
        policy: TrustPolicy,
        customCerts: List<X509Certificate> = emptyList(),
        pinnedFingerprints: Set<String> = emptySet(),
        allowInsecure: Boolean = false,
    ): X509TrustManager = when (policy) {
        TrustPolicy.SYSTEM -> systemTrustManager()

        TrustPolicy.CUSTOM_ONLY -> {
            require(customCerts.isNotEmpty()) { "CUSTOM_ONLY needs at least one certificate" }
            PathBuildingTrustManager.anchoredOn(customCerts)
        }

        TrustPolicy.SYSTEM_PLUS_CUSTOM -> {
            require(customCerts.isNotEmpty()) { "SYSTEM_PLUS_CUSTOM needs at least one certificate" }
            CompositeTrustManager(systemTrustManager(), PathBuildingTrustManager.anchoredOn(customCerts))
        }

        TrustPolicy.STORE_AUTO -> {
            require(customCerts.isNotEmpty()) { "STORE_AUTO needs at least one certificate in the store" }
            PathBuildingTrustManager.anchoredOn(
                certs = customCerts,
                pool = customCerts,
                exposeAcceptedIssuers = false,
            )
        }

        TrustPolicy.PIN_LEAF -> {
            require(pinnedFingerprints.isNotEmpty()) { "PIN_LEAF needs at least one fingerprint" }
            FingerprintPinningTrustManager(pinnedFingerprints)
        }

        TrustPolicy.INSECURE -> {
            require(allowInsecure) { "INSECURE is not available in a release build" }
            InsecureTrustManager()
        }
    }

    fun effectivePolicy(
        requested: TrustPolicy,
        allowInsecure: Boolean,
        onDowngrade: (from: TrustPolicy, to: TrustPolicy) -> Unit = { _, _ -> },
    ): TrustPolicy = if (requested == TrustPolicy.INSECURE && !allowInsecure) {
        TrustPolicy.SYSTEM_PLUS_CUSTOM.also { onDowngrade(requested, it) }
    } else {
        requested
    }

    fun systemTrustManager(): X509TrustManager = trustManagerFrom(TrustManagerFactory.getDefaultAlgorithm(), null)

    fun pkixTrustManager(certs: List<X509Certificate>): X509TrustManager {
        val keyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certs.forEachIndexed { index, certificate -> setCertificateEntry("ca$index", certificate) }
            }
        return trustManagerFrom("PKIX", keyStore)
    }

    private fun trustManagerFrom(algorithm: String, keyStore: KeyStore?): X509TrustManager = TrustManagerFactory.getInstance(algorithm)
        .apply { init(keyStore) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .firstOrNull()
        ?: throw IllegalStateException("No X509TrustManager from algorithm $algorithm")
}

class CompositeTrustManager(
    private val system: X509TrustManager,
    val custom: X509TrustManager,
) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try {
            system.checkServerTrusted(chain, authType)
        } catch (systemFailure: CertificateException) {
            try {
                custom.checkServerTrusted(chain, authType)
            } catch (customFailure: CertificateException) {
                systemFailure.addSuppressed(customFailure)
                throw systemFailure
            }
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + custom.acceptedIssuers
}

class FingerprintPinningTrustManager(
    private val pinnedFingerprints: Set<String>,
) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val leaf =
            chain.firstOrNull()
                ?: throw CertificatePinMismatchException(null, "The server presented no certificate")
        if (!CertificateFingerprint.matchesAnySha256(pinnedFingerprints, leaf)) {
            val presented = CertificateFingerprint.sha256(leaf)
            throw CertificatePinMismatchException(
                presented,
                "The server's certificate fingerprint ${CertificateFingerprint.formatForDisplay(presented)} " +
                    "does not match any pinned fingerprint",
            )
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        throw CertificateException("This trust manager does not authenticate clients")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

class InsecureTrustManager : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

class CertificatePinMismatchException(
    val presentedSha256: String?,
    message: String,
) : CertificateException(message)
