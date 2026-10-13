package com.zvpn.domain.ads

import com.zvpn.platform.KeyValueStore
import com.zvpn.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

object PointsManager {

    private const val KEY_TOTAL_POINTS = "zvpn_points_total"
    private const val KEY_TODAY_POINTS = "zvpn_points_today"
    private const val KEY_LAST_DAY = "zvpn_points_last_day"
    private const val KEY_STREAK_DAYS = "zvpn_streak_days"
    private const val KEY_LAST_STREAK_TIME = "zvpn_last_streak_time"
    private const val KEY_REFERRAL_CODE = "zvpn_my_referral_code"

    // Consumption rates per minute
    const val RATE_FREE_PER_MIN = 1
    const val RATE_PREMIUM_PER_MIN = 5
    const val RATE_PREMIUM_PLUS_PER_MIN = 10

    // Ad reward
    const val AD_REWARD_POINTS = 10

    // Referral reward
    const val REFERRAL_REWARD_POINTS = 50

    private var store: KeyValueStore? = null

    private val _totalPoints = MutableStateFlow(50) // Initial welcome gift: 50 pts
    val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

    private val _todayPoints = MutableStateFlow(0)
    val todayPoints: StateFlow<Int> = _todayPoints.asStateFlow()

    private val _streakDays = MutableStateFlow(1)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _canClaimDailyStreak = MutableStateFlow(true)
    val canClaimDailyStreak: StateFlow<Boolean> = _canClaimDailyStreak.asStateFlow()

    private val _referralCode = MutableStateFlow("")
    val referralCode: StateFlow<String> = _referralCode.asStateFlow()

    fun init(keyValueStore: KeyValueStore) {
        store = keyValueStore
        _totalPoints.value = keyValueStore.getInt(KEY_TOTAL_POINTS, 50)
        _todayPoints.value = keyValueStore.getInt(KEY_TODAY_POINTS, 0)
        _streakDays.value = keyValueStore.getInt(KEY_STREAK_DAYS, 1)

        val code = keyValueStore.getString(KEY_REFERRAL_CODE) ?: ""
        if (code.isBlank()) {
            val generated = generateReferralCode()
            keyValueStore.putString(KEY_REFERRAL_CODE, generated)
            _referralCode.value = generated
        } else {
            _referralCode.value = code
        }

        checkDailyReset()
    }

    private fun generateReferralCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomStr = (1..6).map { chars.random() }.joinToString("")
        return "ZVPN-$randomStr"
    }

    private fun checkDailyReset() {
        val now = currentTimeMillis()
        val lastDay = store?.getString(KEY_LAST_DAY)?.toLongOrNull() ?: 0L
        val dayInMillis = 86_400_000L

        if (now - lastDay > dayInMillis) {
            _todayPoints.value = 0
            store?.putInt(KEY_TODAY_POINTS, 0)
            store?.putString(KEY_LAST_DAY, now.toString())
        }

        val lastStreak = store?.getString(KEY_LAST_STREAK_TIME)?.toLongOrNull() ?: 0L
        val hoursSinceLastStreak = (now - lastStreak) / (1000 * 60 * 60)

        if (hoursSinceLastStreak >= 24) {
            _canClaimDailyStreak.value = true
            if (hoursSinceLastStreak >= 48) {
                _streakDays.value = 1
                store?.putInt(KEY_STREAK_DAYS, 1)
            }
        } else {
            _canClaimDailyStreak.value = false
        }
    }

    fun addPoints(points: Int) {
        val newTotal = _totalPoints.value + points
        val newToday = _todayPoints.value + points
        _totalPoints.value = newTotal
        _todayPoints.value = newToday

        store?.putInt(KEY_TOTAL_POINTS, newTotal)
        store?.putInt(KEY_TODAY_POINTS, newToday)
    }

    fun consumePoints(points: Int): Boolean {
        if (_totalPoints.value < points) return false
        val newTotal = max(0, _totalPoints.value - points)
        _totalPoints.value = newTotal
        store?.putInt(KEY_TOTAL_POINTS, newTotal)
        return true
    }

    fun consumePerMinute(tierId: String): Boolean {
        val rate = when (tierId.lowercase()) {
            "premium", "pro" -> RATE_PREMIUM_PER_MIN
            "premium_plus", "vip" -> RATE_PREMIUM_PLUS_PER_MIN
            else -> RATE_FREE_PER_MIN
        }
        return consumePoints(rate)
    }

    fun claimDailyStreak(): Int {
        if (!_canClaimDailyStreak.value) return 0
        val currentStreak = _streakDays.value
        val reward = when (currentStreak) {
            1 -> 10
            2 -> 15
            3 -> 20
            4 -> 25
            5 -> 30
            6 -> 40
            else -> 50
        }

        addPoints(reward)

        val nextStreak = if (currentStreak >= 7) 1 else currentStreak + 1
        _streakDays.value = nextStreak
        _canClaimDailyStreak.value = false

        val now = currentTimeMillis()
        store?.putInt(KEY_STREAK_DAYS, nextStreak)
        store?.putString(KEY_LAST_STREAK_TIME, now.toString())

        return reward
    }

    fun redeemReferralCode(code: String): Boolean {
        val clean = code.trim().uppercase()
        if (clean.isBlank() || clean == _referralCode.value) return false

        val keyRedeemed = "zvpn_redeemed_$clean"
        if (store?.getBoolean(keyRedeemed, false) == true) return false

        store?.putBoolean(keyRedeemed, true)
        addPoints(REFERRAL_REWARD_POINTS)
        return true
    }
}
