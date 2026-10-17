@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.ads

import android.app.Activity
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.zvpn.ads.UnityAdsManager
import com.zvpn.billing.BillingManager
import com.zvpn.domain.ads.PointsManager
import com.zvpn.domain.billing.UserTier
import com.zvpn.ui.premium.AdRewardSheet
import com.zvpn.ui.premium.PaywallScreen
import com.zvpn.ui.premium.PointsWalletScreen

/**
 * AdRewardHost — Android-only Composable
 *
 * Coordinates:
 *  - Unity Ads (rewarded + interstitial)
 *  - PointsManager state
 *  - BillingManager (subscription tier)
 *  - Shows AdRewardSheet / PointsWalletScreen / PaywallScreen as ModalBottomSheet
 *
 * Usage: Place once at the top of your screen tree (in MainActivity or MainScaffold).
 * Call [AdRewardHostState.showAdSheet] from anywhere to trigger.
 */

sealed class AdSheetDestination {
    object Reward : AdSheetDestination()       // compact Ad + Points sheet
    object Wallet : AdSheetDestination()       // full Points Wallet
    object Paywall : AdSheetDestination()      // Premium subscription paywall
}

class AdRewardHostState {
    var destination by mutableStateOf<AdSheetDestination?>(null)
        private set

    fun showAdSheet() { destination = AdSheetDestination.Reward }
    fun showWallet() { destination = AdSheetDestination.Wallet }
    fun showPaywall() { destination = AdSheetDestination.Paywall }
    fun dismiss() { destination = null }
}

@Composable
fun rememberAdRewardHostState(): AdRewardHostState = remember { AdRewardHostState() }

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AdRewardHost(
    state: AdRewardHostState,
    billingManager: BillingManager,
    onShareReferral: () -> Unit = {},
    onRedeemCode: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val userTier by billingManager.userTier.collectAsState()
    val isRewardedReady by UnityAdsManager.isRewardedReady.collectAsState()
    val rewardedUntilMs by PointsManager.rewardedUntilMs.collectAsState()

    val rewardMinutesRemaining = remember(rewardedUntilMs) {
        PointsManager.rewardMinutesRemaining()
    }

    var isAdLoading by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val destination = state.destination ?: return

    ModalBottomSheet(
        onDismissRequest = { state.dismiss() },
        sheetState = sheetState,
        dragHandle = null,
    ) {
        when (destination) {
            // ── Ad Rewards compact sheet ───────────────────────────────────────
            AdSheetDestination.Reward -> AdRewardSheet(
                todayPoints = PointsManager.todayPoints.collectAsState().value,
                totalPoints = PointsManager.totalPoints.collectAsState().value,
                remainingToday = UnityAdsManager.remainingRewardedToday(),
                maxPerDay = UnityAdsManager.MAX_REWARDED_PER_DAY,
                rewardMinutesRemaining = rewardMinutesRemaining,
                isAdReady = isRewardedReady,
                isLoading = isAdLoading,
                onWatchAd = {
                    if (isAdLoading) return@AdRewardSheet
                    isAdLoading = true
                    UnityAdsManager.showRewarded(
                        activity = activity,
                        onRewarded = { minutes, pts ->
                            isAdLoading = false
                            // Grant access in BillingManager so tier updates
                            billingManager.grantAdReward(minutes)
                        },
                        onFailed = { reason ->
                            isAdLoading = false
                        },
                    )
                },
                onClaimStreak = { /* PointsManager handles internally */ },
                onShareReferral = {
                    state.dismiss()
                    onShareReferral()
                },
                onOpenWallet = { state.showWallet() },
                onUpgrade = { state.showPaywall() },
                onDismiss = { state.dismiss() },
            )

            // ── Full Points Wallet ─────────────────────────────────────────────
            AdSheetDestination.Wallet -> PointsWalletScreen(
                userTier = userTier,
                isAdReady = isRewardedReady,
                isAdLoading = isAdLoading,
                rewardMinutesRemaining = rewardMinutesRemaining,
                rewardedWatchedToday = UnityAdsManager.rewardedWatchedToday(),
                maxRewardedPerDay = UnityAdsManager.MAX_REWARDED_PER_DAY,
                onWatchAd = {
                    if (isAdLoading) return@PointsWalletScreen
                    isAdLoading = true
                    UnityAdsManager.showRewarded(
                        activity = activity,
                        onRewarded = { minutes, _ ->
                            isAdLoading = false
                            billingManager.grantAdReward(minutes)
                        },
                        onFailed = { isAdLoading = false },
                    )
                },
                onUpgrade = { state.showPaywall() },
                onShareReferral = {
                    state.dismiss()
                    onShareReferral()
                },
                onRedeemCode = { code ->
                    val ok = PointsManager.redeemReferralCode(code)
                    onRedeemCode(code)
                },
                onDismiss = { state.dismiss() },
            )

            // ── Premium Paywall ────────────────────────────────────────────────
            AdSheetDestination.Paywall -> PaywallScreen(
                onPlanSelected = { productId ->
                    // Find and launch billing for the selected plan
                    val products = billingManager.products.value
                    val product = products.firstOrNull { it.productId == productId }
                    if (product != null) {
                        billingManager.launchBilling(activity, product)
                    }
                    state.dismiss()
                },
                onDismiss = { state.dismiss() },
                onWatchAd = if (userTier == UserTier.Free) ({
                    if (isAdLoading) return@PaywallScreen
                    isAdLoading = true
                    UnityAdsManager.showRewarded(
                        activity = activity,
                        onRewarded = { minutes, _ ->
                            isAdLoading = false
                            billingManager.grantAdReward(minutes)
                            state.dismiss()
                        },
                        onFailed = { reason ->
                            isAdLoading = false
                            if (reason == "ad_not_ready") {
                                android.widget.Toast.makeText(
                                    activity,
                                    "Loading ad... Please try again in a moment",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                    )
                }) else null,
                isLoading = isAdLoading,
            )
        }
    }
}
