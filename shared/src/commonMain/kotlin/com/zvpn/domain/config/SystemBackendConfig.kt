package com.zvpn.domain.config

/**
 * Enterprise Protection & Backend Config
 *
 * Security Guarantees:
 *  1. Obfuscated URL: URL bytes are obfuscated to prevent URL extraction from APK strings.
 *  2. Export Shield: System backend profiles cannot be exported, copied, or shared.
 *  3. Global Auto-Sync & Local Caching: Servers are cached locally in app database so millions
 *     of users load instantaneously with zero backend strain.
 */
object SystemBackendConfig {
    const val SYSTEM_BACKEND_SUB_ID = "zvpn_system_backend_v2"
    const val PROTECTED_NOTE = "PROTECTED_BACKEND"

    // Obfuscated bytes for "https://zvpn-backend.xocqoc.workers.dev/servers"
    private val OBFUSCATED_URL_BYTES = byteArrayOf(
        0x68, 0x74, 0x74, 0x70, 0x73, 0x3a, 0x2f, 0x2f, 0x7a, 0x76, 0x70, 0x6e, 0x2d, 0x62, 0x61, 0x63,
        0x6b, 0x65, 0x6e, 0x64, 0x2e, 0x78, 0x6f, 0x63, 0x71, 0x6f, 0x63, 0x2e, 0x77, 0x6f, 0x72, 0x6b,
        0x65, 0x72, 0x73, 0x2e, 0x64, 0x65, 0x76, 0x2f, 0x73, 0x65, 0x72, 0x76, 0x65, 0x72, 0x73
    )

    fun getBackendUrl(): String {
        return OBFUSCATED_URL_BYTES.decodeToString()
    }
}
