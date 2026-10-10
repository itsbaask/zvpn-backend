package com.zvpn.core

import android.content.Context
import com.zvpn.R

enum class FailureReason(val stringResId: Int) {
    NoNetwork(R.string.fail_no_network),
    AuthFailed(R.string.fail_auth),
    Timeout(R.string.fail_timeout),
    ServerUnreachable(R.string.fail_unreachable),
    PermissionRevoked(R.string.fail_permission),
    Unknown(R.string.fail_unknown);

    fun localizedMessage(context: Context): String {
        return runCatching { context.getString(stringResId) }.getOrDefault(context.getString(R.string.fail_unknown))
    }

    companion object {
        fun parse(raw: String?): FailureReason {
            if (raw.isNullOrBlank()) return Unknown
            val lower = raw.lowercase()
            return when {
                lower.contains("network") || lower.contains("connect") || lower.contains("internet") -> NoNetwork
                lower.contains("auth") || lower.contains("credential") || lower.contains("password") || lower.contains("login") -> AuthFailed
                lower.contains("timeout") || lower.contains("timed out") -> Timeout
                lower.contains("unreachable") || lower.contains("refused") || lower.contains("host") -> ServerUnreachable
                lower.contains("permission") || lower.contains("revoke") || lower.contains("denied") -> PermissionRevoked
                else -> Unknown
            }
        }
    }
}
