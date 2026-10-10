package io.github.mr1ve3r.combined.core.trust

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerChainFetcher(private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS) {
    suspend fun fetch(host: String, port: Int): List<X509Certificate> = withContext(Dispatchers.IO) {
        val captured = CapturingTrustManager()
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(captured), null) }
        Socket().use { plain ->
            plain.connect(InetSocketAddress(host, port), timeoutMillis)
            plain.soTimeout = timeoutMillis
            val socket = context.socketFactory.createSocket(plain, host, port, false) as SSLSocket
            socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
            try {
                socket.startHandshake()
            } catch (e: IOException) {
                if (captured.chain.isEmpty()) throw e
            } finally {
                runCatching { socket.close() }
            }
            captured.chain.ifEmpty { throw IOException("$host:$port completed the handshake without presenting a certificate") }
        }
    }

    private class CapturingTrustManager : X509TrustManager {
        var chain: List<X509Certificate> = emptyList()
            private set

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            this.chain = chain.toList()
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Int = 10_000
    }
}
