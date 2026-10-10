package io.github.mr1ve3r.combined.engine.sstp.terminal

import io.github.mr1ve3r.combined.core.trust.CertificateFingerprint
import io.github.mr1ve3r.combined.core.trust.CertificatePinMismatchException
import io.github.mr1ve3r.combined.core.trust.CompositeTrustManager
import io.github.mr1ve3r.combined.core.trust.HostnameVerification
import io.github.mr1ve3r.combined.core.trust.HostnameVerificationResult
import io.github.mr1ve3r.combined.core.trust.PathBuildingTrustManager
import io.github.mr1ve3r.combined.engine.EngineError
import io.github.mr1ve3r.combined.engine.EngineException
import io.github.mr1ve3r.combined.engine.LogLevel
import io.github.mr1ve3r.combined.engine.SocketProtector
import io.github.mr1ve3r.combined.engine.TlsVersion
import io.github.mr1ve3r.combined.engine.sstp.SstpEngineConfig
import io.github.mr1ve3r.combined.engine.sstp.SstpTransport
import io.github.mr1ve3r.combined.engine.sstp.extension.capacityAfterLimit
import io.github.mr1ve3r.combined.engine.sstp.extension.slide
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SslTerminal(
    private val config: SstpEngineConfig,
    private val protector: SocketProtector,
    trustManager: X509TrustManager,
    private val log: (LogLevel, String) -> Unit,
    private val socketFactory: () -> Socket = { Socket() },
) : SstpTransport {
    private val sendMutex = Mutex()
    private val recordingTrustManager = RecordingTrustManager(trustManager)

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Volatile
    private var leafCertificate: X509Certificate? = null

    override val applicationBufferSize: Int get() = APPLICATION_BUFFER_SIZE

    override val serverCertificate: ByteArray
        get() = (leafCertificate ?: error("The TLS handshake has not finished")).encoded

    fun establish(guid: String) {
        val raw = connectTransportSocket()
        val secure = startTls(raw)
        verifyHostname()
        establishHttpLayer(guid)
        secure.soTimeout = READ_TIMEOUT_MS
    }

    internal fun connectTransportSocket(): Socket {
        val proxy = config.proxy
        val host = proxy?.host ?: config.server
        val port = proxy?.port ?: config.port

        val opened = socketFactory()
        try {
            if (!opened.isBound) opened.bind(InetSocketAddress(0))
        } catch (e: IOException) {
            opened.closeQuietly()
            throw EngineException(EngineError.Internal("could not bind the transport socket: ${e.message}"), e)
        }
        if (!protector.protect(opened)) {
            opened.closeQuietly()
            throw EngineException(EngineError.Internal("protect() refused the transport socket"))
        }

        try {
            opened.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            opened.soTimeout = HANDSHAKE_TIMEOUT_MS
        } catch (e: SocketTimeoutException) {
            opened.closeQuietly()
            throw EngineException(EngineError.TimedOut(STAGE_CONNECT, "No answer from $host:$port"), e)
        } catch (e: IOException) {
            opened.closeQuietly()
            throw EngineException(EngineError.NetworkUnreachable("$host:$port: ${e.message}"), e)
        }

        socket = opened
        input = opened.getInputStream()
        output = opened.getOutputStream()
        log(LogLevel.INFO, "Connected to $host:$port" + if (proxy != null) " (HTTP proxy)" else "")

        if (proxy != null) {
            establishProxy()
        }

        return opened
    }

    private fun establishProxy() {
        val proxy = config.proxy ?: return
        val target = "${config.server}:${config.port}"

        val request =
            buildList {
                add("CONNECT $target HTTP/1.1")
                add("Host: $target")
                add("SSTPVERSION: 1.0")
                if (!proxy.username.isNullOrEmpty() || !proxy.password.isNullOrEmpty()) {
                    val credentials = "${proxy.username.orEmpty()}:${proxy.password.orEmpty()}"
                    val encoded = android.util.Base64.encodeToString(credentials.toByteArray(Charsets.US_ASCII), android.util.Base64.NO_WRAP)
                    add("Proxy-Authorization: Basic $encoded")
                }
            }.joinToString(separator = HTTP_DELIMITER, postfix = HTTP_SUFFIX)

        log(LogLevel.DEBUG, "Sending CONNECT for $target to the proxy")
        writeAscii(request)

        val status = statusLineOf(readHttpHead())
        when {
            status.contains(" 200") -> log(LogLevel.INFO, "The proxy opened a tunnel to $target")
            REJECTED_BY_PROXY.any { status.contains(it) } ->
                throw EngineException(EngineError.AuthenticationFailed("The proxy rejected the credentials: $status"))
            else -> throw EngineException(EngineError.NetworkUnreachable("The proxy refused CONNECT: $status"))
        }
    }

    private fun startTls(raw: Socket): SSLSocket {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(recordingTrustManager), null)

        val sni = config.verificationHostname
        val secure =
            (context.socketFactory.createSocket(raw, sni, config.port, true) as SSLSocket).apply {
                useClientMode = true
                enabledProtocols = enabledProtocolsFor(config.minTlsVersion, supportedProtocols)
                sslParameters =
                    sslParameters.also { parameters ->
                        parameters.serverNames = sniOrNull(sni)?.let { listOf(it) } ?: emptyList()
                    }
                soTimeout = HANDSHAKE_TIMEOUT_MS
            }

        try {
            secure.startHandshake()
        } catch (e: Exception) {
            secure.closeQuietly()
            throw handshakeFailure(e)
        }

        socket = secure
        input = secure.inputStream
        output = secure.outputStream
        leafCertificate = secure.session.peerCertificates.firstOrNull() as? X509Certificate
        log(LogLevel.INFO, "TLS established: ${secure.session.protocol} ${secure.session.cipherSuite}")
        logPresentedChain(LogLevel.DEBUG)
        logAnchorThatVouched()

        return secure
    }

    private fun logAnchorThatVouched() {
        val manager = trustManagerOfType<PathBuildingTrustManager>() ?: return
        val anchor = manager.lastAnchor ?: return

        log(
            LogLevel.INFO,
            "The chain was anchored on ${anchor.subjectX500Principal.name} " +
                "sha256=${CertificateFingerprint.formatForDisplay(CertificateFingerprint.sha256(anchor))}",
        )
        manager.lastValidatedPath?.forEachIndexed { index, certificate ->
            log(LogLevel.DEBUG, "  path[$index] subject=${certificate.subjectX500Principal.name}")
        }
    }

    private inline fun <reified T : Any> trustManagerOfType(): T? = when (val delegate = recordingTrustManager.delegate) {
        is T -> delegate
        is CompositeTrustManager -> delegate.custom as? T
        else -> null
    }

    private fun verifyHostname() {
        val leaf =
            leafCertificate
                ?: throw EngineException(EngineError.CertificateRejected(null, "The server presented no certificate"))

        when (val result = HostnameVerification.verify(leaf, config.verificationHostname)) {
            is HostnameVerificationResult.Matched ->
                log(LogLevel.DEBUG, "The certificate covers ${config.verificationHostname}")

            is HostnameVerificationResult.Mismatch ->
                throw EngineException(
                    EngineError.HostnameMismatch(
                        expected = result.expected,
                        presented = result.presented,
                        detail = "The certificate is issued to ${result.presented.joinToString()}" +
                            servedItsOwnAuthorityHint(leaf),
                    ),
                )
        }
    }

    private fun servedItsOwnAuthorityHint(leaf: X509Certificate): String {
        val isCa = leaf.basicConstraints >= 0
        return if (isCa && isSelfSigned(leaf)) {
            ". That certificate is a self-signed certificate authority, not a server certificate: the server " +
                "appears to be configured with its CA instead of the certificate the CA issued"
        } else {
            ""
        }
    }

    private fun establishHttpLayer(guid: String) {
        val request =
            listOf(
                "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1",
                "Content-Length: 18446744073709551615",
                "Host: ${config.server}",
                "SSTPCORRELATIONID: {$guid}",
            ).joinToString(separator = HTTP_DELIMITER, postfix = HTTP_SUFFIX)

        writeAscii(request)

        val status = statusLineOf(readHttpHead())
        if (!status.contains(" 200")) {
            throw EngineException(EngineError.Internal("The server refused SSTP_DUPLEX_POST: $status"))
        }
        log(LogLevel.INFO, "The server accepted the SSTP session")
    }

    override suspend fun send(buffer: ByteBuffer) {
        sendMutex.withLock {
            val stream = output ?: throw EngineException(EngineError.Internal("The transport is closed"))
            val length = buffer.remaining()
            stream.write(buffer.array(), buffer.position(), length)
            stream.flush()
            buffer.position(buffer.position() + length)
        }
    }

    override fun receive(buffer: ByteBuffer) {
        val stream = input ?: throw EngineException(EngineError.Internal("The transport is closed"))
        buffer.slide()

        val readSize =
            try {
                stream.read(buffer.array(), buffer.limit(), buffer.capacityAfterLimit)
            } catch (_: SocketTimeoutException) {
                return
            }

        if (readSize < 0) {
            throw EOFException("The server closed the SSTP connection")
        }

        buffer.limit(buffer.limit() + readSize)
    }

    fun close() {
        socket?.closeQuietly()
        socket = null
        input = null
        output = null
    }

    private fun writeAscii(text: String) {
        val stream = output ?: throw EngineException(EngineError.Internal("The transport is closed"))
        stream.write(text.toByteArray(Charsets.US_ASCII))
        stream.flush()
    }

    private fun readHttpHead(): String {
        val stream = input ?: throw EngineException(EngineError.Internal("The transport is closed"))
        val head = StringBuilder()

        while (!head.endsWith(HTTP_SUFFIX)) {
            if (head.length > MAX_HTTP_HEAD) {
                throw EngineException(EngineError.Internal("The HTTP response head never ended"))
            }

            val next =
                try {
                    stream.read()
                } catch (e: SocketTimeoutException) {
                    throw EngineException(EngineError.TimedOut(STAGE_HTTP, "No HTTP response"), e)
                }

            if (next < 0) {
                throw EngineException(EngineError.NetworkUnreachable("The peer closed the connection mid-response"))
            }

            head.append(next.toChar())
        }

        return head.toString()
    }

    private fun logPresentedChain(level: LogLevel) {
        val chain = recordingTrustManager.lastChain
        if (chain.isNullOrEmpty()) {
            log(level, "The server presented no certificate chain")
            return
        }
        log(level, "The server presented ${chain.size} certificate(s):")
        chain.forEachIndexed { index, certificate ->
            val role = if (index == 0) "leaf" else "issuer $index"
            val ca = if (certificate.basicConstraints >= 0) "CA" else "not a CA"
            val signing = if (isSelfSigned(certificate)) "self-signed" else "signed by another key"
            log(
                level,
                "  [$index] $role, $ca, $signing: subject=${certificate.subjectX500Principal.name} " +
                    "issuer=${certificate.issuerX500Principal.name} " +
                    "sha256=${CertificateFingerprint.formatForDisplay(CertificateFingerprint.sha256(certificate))}",
            )
        }
    }

    private fun isSelfSigned(certificate: X509Certificate): Boolean = runCatching {
        certificate.verify(certificate.publicKey)
    }.isSuccess

    private fun logSuppressedFailures(cause: Throwable) {
        var current: Throwable? = cause
        while (current != null) {
            current.suppressed.forEach { suppressed ->
                log(LogLevel.WARN, "The profile's own certificates rejected it too: ${suppressed.message}")
            }
            current = current.cause.takeIf { it !== current }
        }
    }

    private fun handshakeFailure(cause: Exception): EngineException {
        val presented = recordingTrustManager.lastChain?.firstOrNull()
        val fingerprint = presented?.let(CertificateFingerprint::sha256)
        logPresentedChain(LogLevel.WARN)
        logSuppressedFailures(cause)

        causeOfType<CertificatePinMismatchException>(cause)?.also {
            return EngineException(EngineError.CertificateRejected(it.presentedSha256 ?: fingerprint, it.message), cause)
        }

        causeOfType<CertificateExpiredException>(cause)?.also {
            return EngineException(EngineError.CertificateExpired(presented?.notAfter?.time ?: 0L, it.message), cause)
        }

        causeOfType<CertPathValidatorException>(cause)?.also {
            return EngineException(
                EngineError.CertificateRejected(fingerprint, "The chain could not be validated: ${it.message}"),
                cause,
            )
        }

        causeOfType<CertificateException>(cause)?.also {
            return EngineException(EngineError.CertificateRejected(fingerprint, it.message), cause)
        }

        if (cause is SocketTimeoutException) {
            return EngineException(EngineError.TimedOut(STAGE_TLS, cause.message), cause)
        }

        return EngineException(EngineError.TlsHandshakeFailed("${cause.javaClass.simpleName}: ${cause.message}"), cause)
    }

    companion object {
        private const val HTTP_DELIMITER = "\r\n"
        private const val HTTP_SUFFIX = "\r\n\r\n"
        private const val MAX_HTTP_HEAD = 8 * 1024

        private val REJECTED_BY_PROXY = listOf(" 407", " 401", " 403")

        internal const val STAGE_CONNECT = "tcp_connect"
        internal const val STAGE_TLS = "tls_handshake"
        internal const val STAGE_HTTP = "http_layer"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val HANDSHAKE_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 1_000
        private const val APPLICATION_BUFFER_SIZE = 16_384

        private val IPV4_LITERAL = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        internal fun enabledProtocolsFor(minimum: TlsVersion, supported: Array<String>): Array<String> {
            val allowed =
                when (minimum) {
                    TlsVersion.TLS_1_2 -> setOf(TlsVersion.TLS_1_2.protocolName, TlsVersion.TLS_1_3.protocolName)
                    TlsVersion.TLS_1_3 -> setOf(TlsVersion.TLS_1_3.protocolName)
                }

            return supported.filter { it in allowed }.toTypedArray().ifEmpty { arrayOf(minimum.protocolName) }
        }

        internal fun statusLineOf(head: String): String = head.substringBefore(HTTP_DELIMITER).trim()

        internal fun sniOrNull(host: String): SNIHostName? = try {
            if (host.isBlank() || host.contains(':') || host.matches(IPV4_LITERAL)) null else SNIHostName(host)
        } catch (_: IllegalArgumentException) {
            null
        }

        private inline fun <reified T : Throwable> causeOfType(throwable: Throwable): T? {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is T) return current
                current = current.cause
            }
            return null
        }

        private fun Socket.closeQuietly() {
            try {
                close()
            } catch (_: IOException) {
            }
        }
    }
}

internal class RecordingTrustManager(val delegate: X509TrustManager) : X509TrustManager {
    @Volatile
    var lastChain: List<X509Certificate>? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        lastChain = chain.toList()
        delegate.checkServerTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
