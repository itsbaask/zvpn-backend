package com.zvpn.crypto

import com.zvpn.platform.currentTimeMillis

data class ZvxMetadata(
    val passwordProtected: Boolean,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val createdAt: Long,
    val expiresAt: Long?,
) {
    val isExpired: Boolean
        get() = expiresAt != null && currentTimeMillis() > expiresAt
}

data class ZvxSealRequest(
    val configPayload: String,
    val nameEn: String,
    val nameFa: String,
    val note: String,
    val expiresAt: Long?,
    val password: String?,
)

sealed class ZvxException(message: String) : Exception(message)
class ZvxVersionException : ZvxException("Unsupported .zvx version")
class ZvxTamperException : ZvxException("File is corrupt or has been tampered with")
class ZvxWrongPasswordException : ZvxException("Incorrect password")
class ZvxExpiredException : ZvxException("This locked config has expired")
class ZvxPasswordRequiredException : ZvxException("A password is required")
class ZvxLegacyException : ZvxException("This locked config was made by an older version of Z VPN")

fun zvxFileName(name: String): String {
    val base = name.trim().ifBlank { "config" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(48)
    return if (base.endsWith(".zvx")) base else "$base.zvx"
}
