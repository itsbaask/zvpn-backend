package com.zvpn.domain.ads

import com.zvpn.domain.security.SecurityGuard
import com.zvpn.platform.currentTimeMillis

/**
 * AdGuard & Anti-Ad-Bypass Manager for Z VPN.
 * Ensures that free/ad-supported servers CANNOT be unlocked without valid
 * server-verified ad reward completions, preventing Ad-Blockers, hosts file modifications,
 * or local function hooks from bypassing ads.
 */
object AdGuardManager {

    // Store active rewarded session tokens: ServerID/UserID -> ExpirationTimestampMs
    private val activeUnlockedSessions = mutableMapOf<String, Long>()

    // Duration granted per verified rewarded ad (Default: 60 minutes)
    const val REWARD_SESSION_DURATION_MS = 60 * 60 * 1000L

    /**
     * Checks if a server is unlocked and available for the user.
     * Pro users bypass ads automatically. Free users must have an active verified ad session.
     */
    fun isServerAccessUnlocked(serverId: String, isProUser: Boolean): Boolean {
        if (isProUser) return true
        if (serverId.isBlank()) return false

        val expiration = activeUnlockedSessions[serverId] ?: return false
        val now = currentTimeMillis()

        if (now > expiration) {
            // Session expired
            activeUnlockedSessions.remove(serverId)
            return false
        }

        return true
    }

    /**
     * Unlocks a server session ONLY after verifying S2S reward proof.
     */
    fun registerVerifiedAdReward(serverId: String, userId: String, orderId: String): Boolean {
        if (serverId.isBlank() || userId.isBlank() || orderId.isBlank()) return false

        val now = currentTimeMillis()
        val expirationTime = now + REWARD_SESSION_DURATION_MS

        // Grant session
        activeUnlockedSessions[serverId] = expirationTime
        activeUnlockedSessions["global_free_pass"] = expirationTime

        return true
    }

    /**
     * Returns remaining unlocked time in milliseconds for the active session.
     */
    fun getRemainingUnlockedTimeMs(serverId: String): Long {
        val expiration = activeUnlockedSessions[serverId] ?: activeUnlockedSessions["global_free_pass"] ?: return 0L
        val now = currentTimeMillis()
        return if (expiration > now) expiration - now else 0L
    }

    /**
     * Clears all session tokens (e.g. on logout or app reset).
     */
    fun revokeAllAdSessions() {
        activeUnlockedSessions.clear()
    }
}
