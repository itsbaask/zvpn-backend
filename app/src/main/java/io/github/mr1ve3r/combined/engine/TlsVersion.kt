package io.github.mr1ve3r.combined.engine

enum class TlsVersion(val protocolName: String) {
    TLS_1_2("TLSv1.2"),
    TLS_1_3("TLSv1.3"),
    ;

    companion object {
        val DEFAULT: TlsVersion = TLS_1_2
    }
}
