package com.zvpn.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import com.zvpn.domain.ads.UnityAdsConfig
import com.zvpn.domain.ads.UnityAdsS2SManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Complete Unity Ads Manager for Z VPN
 *
 * Handles:
 *  - SDK initialisation (Interstitial + Rewarded + Banner)
 *  - S2S server-side reward verification
 *  - Points & daily reward tracking
 *  - Anti-bypass protection via UnityAdsS2SManager
 *
 * Unity Game IDs:
 *  - Android: 6191257
 *  - iOS:     6191256
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"

    // ─── State ──────────────────────────────────────────────────────────────
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()

    /** Points earned today from watching ads (reset at midnight) */
    private val _todayPoints = MutableStateFlow(0)
    val todayPoints: StateFlow<Int> = _todayPoints.asStateFlow()

    /** Total lifetime points */
    private val _totalPoints = MutableStateFlow(0)
    val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

    /** Ad reward granted-until timestamp (ms) */
    private val _rewardedUntilMs = MutableStateFlow(0L)
    val rewardedUntilMs: StateFlow<Long> = _rewardedUntilMs.asStateFlow()

    val isRewardActive: Boolean
        get() = System.currentTimeMillis() < _rewardedUntilMs.value

    // Points per ad type
    const val POINTS_PER_REWARDED     = 100
    const val POINTS_PER_INTERSTITIAL = 30
    const val REWARD_MINUTES          = 30   // premium access per rewarded ad

    // Daily caps
    const val MAX_REWARDED_PER_DAY     = 5
    const val MAX_INTERSTITIAL_PER_DAY = 10

    private var rewardedShownToday = 0
    private var interstitialShownToday = 0
    private var lastDayReset = 0L   // epoch day

    private var isPro = false
    private var currentUserId = "anonymous"
    private val isShowing = AtomicBoolean(false)

    // ─── Initialisation ─────────────────────────────────────────────────────
    fun init(context: Context, userId: String = "anonymous", isProUser: Boolean = false) {
        isPro = isProUser
        currentUserId = userId
        resetDailyCountIfNeeded()

        if (_isInitialized.value) {
            preloadAll()
            return
        }

        val gameId = UnityAdsConfig.GAME_ID_ANDROID
        val testMode = UnityAdsConfig.TEST_MODE

        Log.d(TAG, "Initialising Unity Ads — gameId=$gameId testMode=$testMode")

        // GDPR/COPPA consent metadata
        val gdprConsent = MetaData(context)
        gdprConsent.set("gdpr.consent", true)
        gdprConsent.commit()

        UnityAds.initialize(
            context,
            gameId,
            testMode,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.i(TAG, "Unity Ads initialised ✓")
                    _isInitialized.value = true
                    preloadAll()
                }
                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                    Log.e(TAG, "Unity Ads init failed: $error — $message")
                }
            }
        )
    }

    fun updateUser(userId: String, isProUser: Boolean) {
        currentUserId = userId
        isPro = isProUser
    }

    // ─── Preload ─────────────────────────────────────────────────────────────
    private fun preloadAll() {
        preloadRewarded()
        preloadInterstitial()
    }

    private fun preloadRewarded() {
        if (!_isInitialized.value) return
        UnityAds.load(
            UnityAdsConfig.PLACEMENT_REWARDED_ANDROID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Rewarded loaded: $placementId")
                    _isRewardedReady.value = true
                }
                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                    Log.w(TAG, "Rewarded load failed: $placementId — $error: $message")
                    _isRewardedReady.value = false
                }
            }
        )
    }

    private fun preloadInterstitial() {
        if (!_isInitialized.value) return
        UnityAds.load(
            UnityAdsConfig.PLACEMENT_INTERSTITIAL_ANDROID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Interstitial loaded: $placementId")
                    _isInterstitialReady.value = true
                }
                override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                    Log.w(TAG, "Interstitial load failed: $placementId — $error: $message")
                    _isInterstitialReady.value = false
                }
            }
        )
    }

    // ─── Show Rewarded Ad ────────────────────────────────────────────────────
    /**
     * Shows a rewarded ad. On successful completion:
     * - Grants [REWARD_MINUTES] minutes of premium access
     * - Awards [POINTS_PER_REWARDED] points
     * - Validates via S2S token before granting
     *
     * @param activity  Current foreground Activity
     * @param onRewarded Called when reward is verified and granted
     * @param onFailed   Called if ad fails or daily cap reached
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: (minutesGranted: Int, pointsEarned: Int) -> Unit,
        onFailed: (reason: String) -> Unit = {},
    ) {
        if (isPro) { onFailed("premium_user"); return }
        if (!_isInitialized.value) { onFailed("not_initialized"); return }
        if (!_isRewardedReady.value) { onFailed("ad_not_ready"); preloadRewarded(); return }
        if (isShowing.get()) { onFailed("already_showing"); return }

        resetDailyCountIfNeeded()
        if (rewardedShownToday >= MAX_REWARDED_PER_DAY) {
            onFailed("daily_cap_reached")
            return
        }

        val sessionToken = UUID.randomUUID().toString()
        val showTimestamp = System.currentTimeMillis()

        isShowing.set(true)
        _isRewardedReady.value = false

        UnityAds.show(
            activity,
            UnityAdsConfig.PLACEMENT_REWARDED_ANDROID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Rewarded started: $placementId")
                }
                override fun onUnityAdsShowClick(placementId: String) {}
                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    isShowing.set(false)
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        // S2S validation
                        val isValid = UnityAdsS2SManager.isRewardTokenValid(
                            userId = currentUserId,
                            timestampMs = showTimestamp,
                            token = sessionToken
                        )
                        if (isValid) {
                            rewardedShownToday++
                            val pts = POINTS_PER_REWARDED
                            _todayPoints.value += pts
                            _totalPoints.value += pts
                            // Grant premium access
                            _rewardedUntilMs.value = System.currentTimeMillis() + REWARD_MINUTES * 60_000L
                            Log.i(TAG, "Reward granted: +${REWARD_MINUTES}min +${pts}pts")
                            onRewarded(REWARD_MINUTES, pts)
                        } else {
                            Log.w(TAG, "S2S validation failed — reward blocked")
                            onFailed("s2s_validation_failed")
                        }
                    } else {
                        onFailed("ad_skipped")
                    }
                    preloadRewarded()
                }
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isShowing.set(false)
                    Log.w(TAG, "Rewarded show failed: $error — $message")
                    onFailed("show_error: $message")
                    preloadRewarded()
                }
            }
        )
    }

    // ─── Show Interstitial ───────────────────────────────────────────────────
    /**
     * Shows an interstitial ad (for free users at natural break points).
     * Awards [POINTS_PER_INTERSTITIAL] points on completion.
     */
    fun showInterstitial(
        activity: Activity,
        onComplete: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null,
    ) {
        if (isPro) { onComplete?.invoke(); return }
        if (!_isInitialized.value || !_isInterstitialReady.value || isShowing.get()) {
            onComplete?.invoke()
            preloadInterstitial()
            return
        }

        resetDailyCountIfNeeded()
        if (interstitialShownToday >= MAX_INTERSTITIAL_PER_DAY) {
            onComplete?.invoke()
            return
        }

        isShowing.set(true)
        _isInterstitialReady.value = false

        UnityAds.show(
            activity,
            UnityAdsConfig.PLACEMENT_INTERSTITIAL_ANDROID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {}
                override fun onUnityAdsShowClick(placementId: String) {}
                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    isShowing.set(false)
                    interstitialShownToday++
                    _todayPoints.value += POINTS_PER_INTERSTITIAL
                    _totalPoints.value += POINTS_PER_INTERSTITIAL
                    onComplete?.invoke()
                    preloadInterstitial()
                }
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    isShowing.set(false)
                    onFailed?.invoke(message)
                    preloadInterstitial()
                }
            }
        )
    }

    // ─── Daily reset ─────────────────────────────────────────────────────────
    private fun resetDailyCountIfNeeded() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today != lastDayReset) {
            lastDayReset = today
            rewardedShownToday = 0
            interstitialShownToday = 0
            _todayPoints.value = 0
            Log.d(TAG, "Daily ad counters reset for day $today")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    fun canShowRewarded(): Boolean {
        resetDailyCountIfNeeded()
        return _isInitialized.value && _isRewardedReady.value && !isPro
                && rewardedShownToday < MAX_REWARDED_PER_DAY
    }

    fun remainingRewardedToday(): Int = maxOf(0, MAX_REWARDED_PER_DAY - rewardedShownToday)

    fun rewardMinutesRemaining(): Long {
        val remaining = _rewardedUntilMs.value - System.currentTimeMillis()
        return maxOf(0L, remaining / 60_000L)
    }
}
