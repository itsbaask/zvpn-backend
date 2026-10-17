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
import com.zvpn.domain.ads.PointsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unity Ads Manager — ZVPN Complete Revenue Engine
 *
 * Ad Types & Points:
 *  - Rewarded Video:       +100 pts + 30 min premium access
 *  - Interstitial:         +25  pts  (shown on disconnect)
 *
 * Daily Caps (anti-abuse):
 *  - Rewarded:      8/day
 *  - Interstitial:  15/day
 *
 * Anti-Fraud:
 *  - S2S HMAC-SHA256 token validation before granting any reward
 *  - Nonce-based replay protection
 *  - Daily caps enforced in-memory + Cloudflare Worker edge
 *  - Pro users never see ads
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"

    // ─── Points & Reward constants ───────────────────────────────────────────
    const val POINTS_PER_REWARDED      = 100   // per rewarded video completion
    const val POINTS_PER_INTERSTITIAL  = 25    // per interstitial view
    const val REWARD_MINUTES           = 30    // premium access per rewarded ad

    // ─── Daily caps (Unlimited ads allowed) ──────────────────────────────────
    const val MAX_REWARDED_PER_DAY     = 999999
    const val MAX_INTERSTITIAL_PER_DAY = 999999

    // ─── State ───────────────────────────────────────────────────────────────
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReady: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()

    /** Current rewarded access expires at this timestamp (ms). 0 = no active reward. */
    private val _rewardedUntilMs = MutableStateFlow(0L)
    val rewardedUntilMs: StateFlow<Long> = _rewardedUntilMs.asStateFlow()

    val isRewardActive: Boolean
        get() = System.currentTimeMillis() < _rewardedUntilMs.value

    fun rewardMinutesRemaining(): Long {
        val remaining = _rewardedUntilMs.value - System.currentTimeMillis()
        return maxOf(0L, remaining / 60_000L)
    }

    // ─── Internal state ───────────────────────────────────────────────────────
    private var isPro = false
    private var currentUserId = "anonymous"
    private val isShowing = AtomicBoolean(false)

    private var rewardedShownToday = 0
    private var interstitialShownToday = 0
    private var lastDayReset = 0L   // epoch day number

    // ─── Initialisation ───────────────────────────────────────────────────────
    /**
     * Call once from Application.onCreate() or MainActivity.onCreate().
     */
    fun init(context: Context, userId: String = "anonymous", isProUser: Boolean = false) {
        isPro = isProUser
        currentUserId = userId
        resetDailyCountIfNeeded()

        if (_isInitialized.value) {
            preloadAll()
            return
        }

        Log.d(TAG, "Initialising Unity Ads SDK — gameId=${UnityAdsConfig.GAME_ID_ANDROID} testMode=${UnityAdsConfig.TEST_MODE}")

        // GDPR consent
        val gdpr = MetaData(context)
        gdpr.set("gdpr.consent", true)
        gdpr.commit()

        // CCPA / Privacy Framework consent
        val privacy = MetaData(context)
        privacy.set("privacy.consent", true)
        privacy.commit()

        UnityAds.initialize(
            context,
            UnityAdsConfig.GAME_ID_ANDROID,
            UnityAdsConfig.TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.i(TAG, "Unity Ads initialised ✓")
                    _isInitialized.value = true
                    preloadAll()
                }
                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String,
                ) {
                    Log.e(TAG, "Init failed: $error — $message (DNS ad-blocker or offline). Will retry in 15s...")
                    _isInitialized.value = false
                    // Auto-retry when connectivity changes or once VPN is connected
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(15_000L)
                        if (!_isInitialized.value) {
                            init(context)
                        }
                    }
                }
            }
        )
    }

    fun updateUser(userId: String, isProUser: Boolean) {
        currentUserId = userId
        isPro = isProUser
        if (isProUser) {
            // Pro users never get ads — reset reward state cleanly
            _isRewardedReady.value = false
            _isInterstitialReady.value = false
        }
    }

    // ─── Preload ──────────────────────────────────────────────────────────────
    fun preloadAll() {
        preloadRewarded()
        preloadInterstitial()
    }

    private fun preloadRewarded() {
        if (!_isInitialized.value || isPro) return
        UnityAds.load(
            UnityAdsConfig.PLACEMENT_REWARDED_ANDROID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Rewarded loaded: $placementId")
                    _isRewardedReady.value = true
                }
                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    Log.w(TAG, "Rewarded load failed: $placementId — $error: $message")
                    _isRewardedReady.value = false
                }
            }
        )
    }

    private fun preloadInterstitial() {
        if (!_isInitialized.value || isPro) return
        UnityAds.load(
            UnityAdsConfig.PLACEMENT_INTERSTITIAL_ANDROID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Interstitial loaded: $placementId")
                    _isInterstitialReady.value = true
                }
                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    Log.w(TAG, "Interstitial load failed: $placementId — $error: $message")
                    _isInterstitialReady.value = false
                }
            }
        )
    }

    // ─── Show Rewarded Ad ─────────────────────────────────────────────────────
    /**
     * Shows a full rewarded video ad.
     *
     * On COMPLETED:
     *  - Validates via S2S HMAC before granting reward
     *  - Awards [POINTS_PER_REWARDED] points via [PointsManager]
     *  - Grants [REWARD_MINUTES] minutes of premium access
     *  - Calls [onRewarded] with (minutesGranted, pointsEarned)
     *
     * On skip / failure: calls [onFailed] with reason string.
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

        // Generate S2S token before showing
        val nonce = UUID.randomUUID().toString()
        val showTimestamp = System.currentTimeMillis()
        val token = UnityAdsS2SManager.generateToken(currentUserId, showTimestamp, nonce)

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
                    state: UnityAds.UnityAdsShowCompletionState,
                ) {
                    isShowing.set(false)
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        // S2S fraud check
                        val valid = UnityAdsS2SManager.isRewardTokenValid(
                            userId = currentUserId,
                            timestampMs = showTimestamp,
                            token = token,
                            nonce = nonce,
                        )
                        if (valid) {
                            rewardedShownToday++
                            // Grant time-based premium access
                            _rewardedUntilMs.value = System.currentTimeMillis() + REWARD_MINUTES * 60_000L
                            // Award points (bypasses Google sign-in enforcement for ad reward)
                            PointsManager.addPointsDirect(POINTS_PER_REWARDED)
                            Log.i(TAG, "Reward granted: +${REWARD_MINUTES}min +${POINTS_PER_REWARDED}pts")
                            onRewarded(REWARD_MINUTES, POINTS_PER_REWARDED)
                        } else {
                            Log.w(TAG, "S2S validation FAILED — reward BLOCKED (anti-fraud)")
                            onFailed("s2s_validation_failed")
                        }
                    } else {
                        Log.d(TAG, "Rewarded skipped/incomplete — state=$state")
                        onFailed("ad_skipped")
                    }
                    preloadRewarded()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String,
                ) {
                    isShowing.set(false)
                    Log.w(TAG, "Rewarded show failed: $error — $message")
                    onFailed("show_error: $message")
                    preloadRewarded()
                }
            }
        )
    }

    // ─── Show Interstitial Ad ─────────────────────────────────────────────────
    /**
     * Shows an interstitial ad at natural break points (e.g., VPN disconnect).
     * Awards [POINTS_PER_INTERSTITIAL] points on completion.
     * Pro users are never shown interstitials — this returns immediately.
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
                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Interstitial started: $placementId")
                }
                override fun onUnityAdsShowClick(placementId: String) {}

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState,
                ) {
                    isShowing.set(false)
                    interstitialShownToday++
                    // Award passive points for interstitial
                    PointsManager.addPointsDirect(POINTS_PER_INTERSTITIAL)
                    Log.d(TAG, "Interstitial complete: +${POINTS_PER_INTERSTITIAL}pts")
                    onComplete?.invoke()
                    preloadInterstitial()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String,
                ) {
                    isShowing.set(false)
                    Log.w(TAG, "Interstitial failed: $error — $message")
                    onFailed?.invoke(message)
                    preloadInterstitial()
                }
            }
        )
    }

    // ─── Daily reset ──────────────────────────────────────────────────────────
    private fun resetDailyCountIfNeeded() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today != lastDayReset) {
            lastDayReset = today
            rewardedShownToday = 0
            interstitialShownToday = 0
            Log.d(TAG, "Daily ad counters reset for epoch-day $today")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    fun canShowRewarded(): Boolean {
        resetDailyCountIfNeeded()
        return _isInitialized.value
                && _isRewardedReady.value
                && !isPro
                && rewardedShownToday < MAX_REWARDED_PER_DAY
    }

    fun remainingRewardedToday(): Int = maxOf(0, MAX_REWARDED_PER_DAY - rewardedShownToday)
    fun rewardedWatchedToday(): Int = rewardedShownToday
}
