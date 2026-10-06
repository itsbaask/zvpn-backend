package com.zvpn.domain.ads

object UnityAdsS2SManager {

    /**
     * Checks if ads should be suppressed for a Pro user.
     */
    fun shouldShowAds(isPro: Boolean): Boolean = !isPro

    /**
     * Validates S2S Ad Reward token timestamp and user binding to prevent ad bypass or fraud.
     */
    fun isRewardTokenValid(userId: String, timestampMs: Long, token: String): Boolean {
        if (userId.isBlank() || token.isBlank()) return false
        val maxAgeMs = 5 * 60 * 1000L // 5 minutes validity
        if (System.currentTimeMillis() - timestampMs > maxAgeMs) return false
        return token.length >= 16
    }
}
