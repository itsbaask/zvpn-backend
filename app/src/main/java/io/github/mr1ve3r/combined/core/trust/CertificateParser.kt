package io.github.mr1ve3r.combined.core.trust

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

object CertificateParser {
    private const val PEM_BEGIN = "-----BEGIN CERTIFICATE-----"
    private const val PEM_END = "-----END CERTIFICATE-----"
    private const val PEM_LINE_LENGTH = 64

    fun parse(bytes: ByteArray): List<X509Certificate> = parse(ByteArrayInputStream(bytes))

    fun parse(input: InputStream): List<X509Certificate> {
        val certificates =
            try {
                CertificateFactory.getInstance("X.509").generateCertificates(input)
            } catch (e: CertificateException) {
                throw CertificateParseException("The file is not a readable X.509 certificate", e)
            }
        if (certificates.isEmpty()) {
            throw CertificateParseException("The file contained no certificates")
        }
        return certificates.filterIsInstance<X509Certificate>().also {
            if (it.isEmpty()) {
                throw CertificateParseException("The file contained certificates, but none of them X.509")
            }
        }
    }

    fun parsePem(text: String): List<X509Certificate> {
        val normalised = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (!normalised.contains(PEM_BEGIN)) {
            throw CertificateParseException("No \"$PEM_BEGIN\" block found in the pasted text")
        }
        return parse(normalised.toByteArray(Charsets.US_ASCII))
    }

    fun toPem(certificate: X509Certificate): String = buildString {
        appendLine(PEM_BEGIN)
        android.util.Base64.encodeToString(certificate.encoded, android.util.Base64.NO_WRAP)
            .chunked(PEM_LINE_LENGTH)
            .forEach(::appendLine)
        appendLine(PEM_END)
    }
}

class CertificateParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
