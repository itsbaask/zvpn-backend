package com.zvpn.domain.ads

import com.zvpn.domain.auth.UserAuthManager
import com.zvpn.domain.billing.BillingConfig
import com.zvpn.domain.billing.UserTier
import com.zvpn.platform.KeyValueStore
import com.zvpn.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class PointTransaction(
    val id: String,
    val title: String,
    val amount: Int, // positive = earned, negative = spent
    val timestamp: Long,
    val isCredit: Boolean,
)

object PointsManager {

    // ── Persistence Keys ──────────────────────────────────────────────────────
    private const val KEY_TOTAL_POINTS      = "zvpn_points_total"
    private const val KEY_TODAY_POINTS      = "zvpn_points_today"
    private const val KEY_TODAY_ADS         = "zvpn_ads_today"           // ads watched today
    private const val KEY_LAST_DAY          = "zvpn_points_last_day"
    private const val KEY_STREAK_DAYS       = "zvpn_streak_days"
    private const val KEY_LAST_STREAK_TIME  = "zvpn_last_streak_time"
    private const val KEY_REFERRAL_CODE     = "zvpn_my_referral_code"
    private const val KEY_HARDWARE_MAC      = "zvpn_hw_mac"
    private const val KEY_NETWORK_ID        = "zvpn_network_id"
    private const val KEY_REWARDED_UNTIL    = "zvpn_rewarded_until_ms"
    private const val KEY_TRANSACTIONS      = "zvpn_points_tx_history"

    // ── Public Economy Constants (delegates to BillingConfig) ─────────────────
    val AD_REWARD_POINTS          get() = BillingConfig.AD_REWARD_POINTS
    val INTERSTITIAL_REWARD_POINTS get() = BillingConfig.INTERSTITIAL_REWARD_POINTS
    val REFERRAL_REWARD_POINTS    = 100

    // ── State Flows ───────────────────────────────────────────────────────────
    private var store: KeyValueStore? = null

    private val _totalPoints = MutableStateFlow(BillingConfig.WELCOME_GIFT_POINTS)
    val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

    private val _todayPoints = MutableStateFlow(0)
    val todayPoints: StateFlow<Int> = _todayPoints.asStateFlow()

    private val _todayAds = MutableStateFlow(0)
    val todayAds: StateFlow<Int> = _todayAds.asStateFlow()

    private val _streakDays = MutableStateFlow(1)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _canClaimDailyStreak = MutableStateFlow(true)
    val canClaimDailyStreak: StateFlow<Boolean> = _canClaimDailyStreak.asStateFlow()

    private val _referralCode = MutableStateFlow("")
    val referralCode: StateFlow<String> = _referralCode.asStateFlow()

    /** Timestamp (ms) until which ad-rewarded premium access is active. 0 = no active reward. */
    private val _rewardedUntilMs = MutableStateFlow(0L)
    val rewardedUntilMs: StateFlow<Long> = _rewardedUntilMs.asStateFlow()

    private val _transactions = MutableStateFlow<List<PointTransaction>>(emptyList())
    val transactions: StateFlow<List<PointTransaction>> = _transactions.asStateFlow()

    // ── Derived Properties ────────────────────────────────────────────────────
    val isAdRewardActive: Boolean get() = currentTimeMillis() < _rewardedUntilMs.value

    fun rewardMinutesRemaining(): Long {
        val rem = _rewardedUntilMs.value - currentTimeMillis()
        return maxOf(0L, rem / 60_000L)
    }

    fun isGoogleSignedIn(): Boolean = UserAuthManager.session.value.isSignedIn

    /** True if the user can still watch an ad today (not yet at daily cap) */
    val canWatchAdToday: Boolean get() = _todayAds.value < BillingConfig.FREE_DAILY_AD_CAP

    /** How many more ads the user can watch today */
    val adsRemainingToday: Int get() = (BillingConfig.FREE_DAILY_AD_CAP - _todayAds.value).coerceAtLeast(0)

    // ── Initialisation ────────────────────────────────────────────────────────
    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        _totalPoints.value  = keyValueStore.getInt(KEY_TOTAL_POINTS, BillingConfig.WELCOME_GIFT_POINTS)
        _todayPoints.value  = keyValueStore.getInt(KEY_TODAY_POINTS, 0)
        _todayAds.value     = keyValueStore.getInt(KEY_TODAY_ADS, 0)
        _streakDays.value   = keyValueStore.getInt(KEY_STREAK_DAYS, 1)

        val code = keyValueStore.getString(KEY_REFERRAL_CODE) ?: ""
        _referralCode.value = if (code.isBlank()) {
            val generated = generateReferralCode()
            keyValueStore.putString(KEY_REFERRAL_CODE, generated)
            generated
        } else code

        // Restore reward expiry — survives app restarts
        val savedReward = keyValueStore.getString(KEY_REWARDED_UNTIL)?.toLongOrNull() ?: 0L
        _rewardedUntilMs.value = savedReward

        checkDailyReset()
    }

    // ── Hardware / Network ID ─────────────────────────────────────────────────
    fun getHardwareMac(): String {
        var mac = store?.getString(KEY_HARDWARE_MAC) ?: ""
        if (mac.isBlank()) {
            val chars = "0123456789ABCDEF"
            mac = (1..6).map { "${chars.random()}${chars.random()}" }.joinToString(":")
            store?.putString(KEY_HARDWARE_MAC, mac)
        }
        return mac
    }

    fun getNetworkId(): String {
        var netId = store?.getString(KEY_NETWORK_ID) ?: ""
        if (netId.isBlank()) {
            val chars = "0123456789abcdef"
            netId = "net_" + (1..8).map { chars.random() }.joinToString("")
            store?.putString(KEY_NETWORK_ID, netId)
        }
        return netId
    }

    private fun generateReferralCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "ZVPN-" + (1..6).map { chars.random() }.joinToString("")
    }

    // ── Daily Reset ───────────────────────────────────────────────────────────
    private fun checkDailyReset() {
        val now = currentTimeMillis()
        val lastDay = store?.getString(KEY_LAST_DAY)?.toLongOrNull() ?: 0L
        val dayInMillis = 86_400_000L

        if (now - lastDay > dayInMillis) {
            _todayPoints.value = 0
            _todayAds.value    = 0
            store?.putInt(KEY_TODAY_POINTS, 0)
            store?.putInt(KEY_TODAY_ADS, 0)
            store?.putString(KEY_LAST_DAY, now.toString())
        }

        val lastStreak = store?.getString(KEY_LAST_STREAK_TIME)?.toLongOrNull() ?: 0L
        val hoursSinceLastStreak = (now - lastStreak) / (1000 * 60 * 60)

        if (hoursSinceLastStreak >= 24) {
            _canClaimDailyStreak.value = true
            if (hoursSinceLastStreak >= 48) {
                // Streak broken
                _streakDays.value = 1
                store?.putInt(KEY_STREAK_DAYS, 1)
            }
        } else {
            _canClaimDailyStreak.value = false
        }
    }

    // ── Points Addition ───────────────────────────────────────────────────────
    /** Add points; requires Google Sign-In. Use for manual actions (referral, streak). */
    fun addPoints(points: Int) {
        if (!isGoogleSignedIn()) return
        addPointsInternal(points)
    }

    /**
     * Add points without requiring Google Sign-In.
     * ONLY to be called after successful Unity Ads S2S validation.
     */
    fun addPointsDirect(points: Int) {
        addPointsInternal(points)
    }

    private fun addPointsInternal(pts: Int, title: String = "Points Earned") {
        val todayAlready = _todayPoints.value
        // Clamp to daily max
        val remaining = BillingConfig.MAX_DAILY_EARNED_POINTS - todayAlready
        val actual = pts.coerceAtMost(remaining.coerceAtLeast(0))
        if (actual <= 0) return

        val newTotal = _totalPoints.value + actual
        val newToday = todayAlready + actual
        _totalPoints.value = newTotal
        _todayPoints.value = newToday
        store?.putInt(KEY_TOTAL_POINTS, newTotal)
        store?.putInt(KEY_TODAY_POINTS, newToday)

        logTransaction(title, actual, isCredit = true)
    }

    private fun logTransaction(title: String, amount: Int, isCredit: Boolean) {
        val tx = PointTransaction(
            id = currentTimeMillis().toString(),
            title = title,
            amount = amount,
            timestamp = currentTimeMillis(),
            isCredit = isCredit,
        )
        val updated = (listOf(tx) + _transactions.value).take(20)
        _transactions.value = updated
    }

    // ── Ad Reward ─────────────────────────────────────────────────────────────
    /**
     * Record a successfully watched rewarded ad.
     * Grants [AD_REWARD_POINTS] points and increments daily ad counter.
     * Returns false if the daily ad cap has been reached.
     */
    fun recordRewardedAdWatched(): Boolean {
        if (!canWatchAdToday) return false
        val newCount = _todayAds.value + 1
        _todayAds.value = newCount
        store?.putInt(KEY_TODAY_ADS, newCount)
        addPointsInternal(BillingConfig.AD_REWARD_POINTS, "Watched Rewarded Ad")
        return true
    }

    /**
     * Record a passively-shown interstitial ad (smaller reward, no user cap check).
     */
    fun recordInterstitialAd() {
        addPointsInternal(BillingConfig.INTERSTITIAL_REWARD_POINTS, "Bonus Interstitial Ad")
    }

    /**
     * Grant time-based premium access after a verified rewarded ad (legacy flow).
     * Persists expiry so the reward survives app restarts.
     */
    fun grantAdReward(minutes: Int) {
        val expiryMs = currentTimeMillis() + minutes * 60_000L
        _rewardedUntilMs.value = expiryMs
        store?.putString(KEY_REWARDED_UNTIL, expiryMs.toString())
    }

    // ── Points Consumption ────────────────────────────────────────────────────
    /** Deduct [points] from the balance. Returns false if insufficient balance or not signed in. */
    fun consumePoints(points: Int, title: String = "VPN Connection"): Boolean {
        if (points <= 0) return true
        if (!isGoogleSignedIn() || _totalPoints.value < points) return false
        val newTotal = max(0, _totalPoints.value - points)
        _totalPoints.value = newTotal
        store?.putInt(KEY_TOTAL_POINTS, newTotal)
        logTransaction(title, points, isCredit = false)
        return true
    }

    /**
     * Called every 60 seconds while a VPN tunnel is active.
     *
     * @param serverTier  "free" / "premium" / "premium_plus"
     * @param protocol    e.g. "wireguard", "vless", "openvpn"
     * @param userTier    the current [UserTier] (paid subscribers are exempt)
     * @return  true  = points deducted OK (or user is exempt)
     *          false = insufficient balance → caller should disconnect and prompt user
     */
    fun consumePerMinute(serverTier: String, protocol: String, userTier: UserTier): Boolean {
        val cost = UserTier.effectiveCostPerMin(userTier, serverTier, protocol)
        if (cost == 0) return true  // paid subscriber — no deduction
        return consumePoints(cost)
    }

    /**
     * Preview cost per minute without deducting anything (for UI display).
     */
    fun previewCostPerMin(serverTier: String, protocol: String, userTier: UserTier): Int =
        UserTier.effectiveCostPerMin(userTier, serverTier, protocol)

    // ── Premium Feature Unlock ────────────────────────────────────────────────
    /** Spend points to unlock a one-session premium feature. Returns false if insufficient. */
    fun unlockFeature(featureCost: Int): Boolean = consumePoints(featureCost)

    // ── Daily Streak ──────────────────────────────────────────────────────────
    fun claimDailyStreak(): Int {
        if (!isGoogleSignedIn() || !_canClaimDailyStreak.value) return 0
        val currentStreak = _streakDays.value
        val reward = when (currentStreak) {
            1    -> 15
            2    -> 20
            3    -> 30
            4    -> 40
            5    -> 50
            6    -> 75
            else -> 100   // Day 7+: double century
        }

        addPointsInternal(reward, "Daily Check-in Day $currentStreak")

        val nextStreak = if (currentStreak >= 7) 1 else currentStreak + 1
        _streakDays.value = nextStreak
        _canClaimDailyStreak.value = false

        val now = currentTimeMillis()
        store?.putInt(KEY_STREAK_DAYS, nextStreak)
        store?.putString(KEY_LAST_STREAK_TIME, now.toString())

        return reward
    }

    // ── Referral ──────────────────────────────────────────────────────────────
    fun redeemReferralCode(code: String): Boolean {
        if (!isGoogleSignedIn()) return false
        val clean = code.trim().uppercase()
        if (clean.isBlank() || clean == _referralCode.value) return false

        val keyRedeemed = "zvpn_redeemed_$clean"
        if (store?.getBoolean(keyRedeemed, false) == true) return false

        store?.putBoolean(keyRedeemed, true)
        addPointsInternal(REFERRAL_REWARD_POINTS, "Friend Referral Bonus ($clean)")
        return true
    }
}
