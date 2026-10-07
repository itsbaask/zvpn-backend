package com.zvpn.domain.ads

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unity Ads Server-Side (S2S) Reward Validation
 *
 * Prevents ad reward bypass, replay attacks, and fraud:
 *  1. Token freshness check (max 5 minutes old)
 *  2. HMAC-SHA256 signature verification
 *  3. User-binding check (token must include userId)
 *  4. One-use nonce tracking (prevents replay)
 *
 * The backend (Cloudflare Worker /v1/user/ad-reward) also validates
 * using the same UNITY_S2S_SECRET before granting premium access.
 */
object UnityAdsS2SManager {

    // Shared secret — must match UNITY_S2S_SECRET in Cloudflare Worker
    // In production, load from BuildConfig or encrypted storage
    private const val S2S_SECRET = "zvpn-unity-s2s-secret-2024"
    private const val MAX_AGE_MS = 5 * 60 * 1000L   // 5 minutes

    // Nonce cache to prevent replay attacks (in-memory, cleared on restart)
    private val usedNonces = LinkedHashMap<String, Long>(100, 0.75f, true)
    private const val NONCE_CACHE_SIZE = 500

    /**
     * Returns true if ads should be shown (user is NOT pro).
     */
    fun shouldShowAds(isPro: Boolean): Boolean = !isPro

    /**
     * Validates an ad reward token before granting premium access.
     *
     * @param userId       The authenticated user's ID (from Firebase)
     * @param timestampMs  When the ad was shown (System.currentTimeMillis())
     * @param token        The token generated at ad-show time
     * @param nonce        Unique per-impression ID (prevents replay)
     * @return true if the reward is legitimate and should be granted
     */
    fun isRewardTokenValid(
        userId: String,
        timestampMs: Long,
        token: String,
        nonce: String = "",
    ): Boolean {
        // 1. Basic checks
        if (userId.isBlank() || token.isBlank()) return false
        if (token.length < 16) return false

        // 2. Freshness check — token must be recent
        val age = System.currentTimeMillis() - timestampMs
        if (age > MAX_AGE_MS || age < 0) return false

        // 3. Replay protection — check nonce hasn't been used
        if (nonce.isNotBlank()) {
            if (usedNonces.containsKey(nonce)) return false
            // Add to used nonces (evict old entries)
            if (usedNonces.size >= NONCE_CACHE_SIZE) {
                val oldest = usedNonces.keys.firstOrNull()
                if (oldest != null) usedNonces.remove(oldest)
            }
            usedNonces[nonce] = timestampMs
        }

        // 4. HMAC-SHA256 signature check
        return try {
            val expected = computeHmac(userId, timestampMs, nonce)
            constantTimeEquals(expected, token)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates the HMAC-SHA256 token for an ad impression.
     * Call this when an ad STARTS showing, pass the result to [isRewardTokenValid].
     */
    fun generateToken(userId: String, timestampMs: Long, nonce: String = ""): String {
        return computeHmac(userId, timestampMs, nonce)
    }

    private fun computeHmac(userId: String, timestampMs: Long, nonce: String): String {
        val message = "$userId:$timestampMs:$nonce"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(S2S_SECRET.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Constant-time string comparison to prevent timing attacks */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
