@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.zvpn.ui.premium

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.ads.PointsManager
import com.zvpn.domain.billing.UserTier
import com.zvpn.ui.theme.ZHotPink
import com.zvpn.ui.theme.ZLime
import com.zvpn.ui.theme.ZViolet
import org.jetbrains.compose.resources.painterResource
import com.zvpn.shared.resources.*

/**
 * PointsWalletScreen — styled 100% with MaterialTheme tokens.
 * Works correctly in both Dark and Light mode.
 * Only ZViolet / ZHotPink / ZLime accents are brand-specific (same as the connect orb).
 */
@Composable
fun PointsWalletScreen(
    userTier: UserTier,
    isAdReady: Boolean,
    isAdLoading: Boolean,
    rewardMinutesRemaining: Long,
    rewardedWatchedToday: Int,
    maxRewardedPerDay: Int,
    onWatchAd: () -> Unit,
    onUpgrade: () -> Unit,
    onShareReferral: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    val totalPts by PointsManager.totalPoints.collectAsState()
    val todayPts by PointsManager.todayPoints.collectAsState()
    val canClaimStreak by PointsManager.canClaimDailyStreak.collectAsState()
    val streakDays by PointsManager.streakDays.collectAsState()
    val myRefCode by PointsManager.referralCode.collectAsState()
    val isSignedIn = PointsManager.isGoogleSignedIn()

    var showRedeemDialog by remember { mutableStateOf(false) }
    var redeemInput by remember { mutableStateOf("") }

    val isPremium = userTier == UserTier.Premium || userTier == UserTier.PremiumPlus
    val adBtnEnabled = isAdReady && rewardedWatchedToday < maxRewardedPerDay && !isAdLoading
    val remainingAds = maxOf(0, maxRewardedPerDay - rewardedWatchedToday)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Drag handle ────────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(44.dp).height(4.dp)
                .clip(CircleShape)
                .background(cs.outlineVariant)
        )
        Spacer(Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Points Wallet",
                    style = typo.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onBackground,
                )
                // Gradient underline accent
                Box(
                    Modifier
                        .width(80.dp).height(2.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ZViolet, ZHotPink)))
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = "Close",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Active reward banner ───────────────────────────────────────────────
        if (rewardMinutesRemaining > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = MaterialTheme.shapes.medium,
                color = cs.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(cs.secondary)
                    )
                    Text(
                        "Premium Active — $rewardMinutesRemaining min remaining",
                        style = typo.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Balance card (gradient hero) ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(listOf(ZViolet, ZHotPink)))
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Tier chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = when (userTier) {
                            UserTier.PremiumPlus -> "👑 PREMIUM+"
                            UserTier.Premium -> "⚡ PREMIUM"
                            UserTier.AdRewarded -> "🎬 AD REWARDED"
                            else -> "🆓 FREE TIER"
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        style = typo.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "$totalPts",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Text(
                    "POINTS",
                    style = typo.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(0.7f),
                    letterSpacing = 4.sp,
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(0.2f))
                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    WalletStat("Today", "+$todayPts pts")
                    WalletStat("Streak", "Day $streakDays 🔥")
                    WalletStat(
                        "Rate",
                        when (userTier) {
                            UserTier.PremiumPlus -> "10/min"
                            UserTier.Premium, UserTier.AdRewarded -> "5/min"
                            else -> "1/min"
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ══ EARN SECTION ══════════════════════════════════════════════════════
        SectionLabel("Earn Points")
        Spacer(Modifier.height(10.dp))

        // ── Watch Ad CTA ───────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Button(
                onClick = onWatchAd,
                enabled = adBtnEnabled,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = cs.onSurface.copy(0.08f),
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (adBtnEnabled)
                                Modifier.background(
                                    Brush.linearGradient(listOf(ZViolet, ZHotPink)),
                                    shape = MaterialTheme.shapes.large,
                                )
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(isAdLoading, label = "ad-btn") { loading ->
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (remainingAds > 0) "🎬  Watch Ad — +100 pts + 30 min"
                                    else "Daily limit reached ($maxRewardedPerDay/$maxRewardedPerDay)",
                                    style = typo.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (adBtnEnabled) Color.White else cs.onSurface.copy(0.38f),
                                )
                                if (remainingAds > 0) {
                                    Text(
                                        "$remainingAds of $maxRewardedPerDay ads remaining today",
                                        style = typo.labelSmall,
                                        color = Color.White.copy(0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress badge
            if (rewardedWatchedToday > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-8).dp),
                    shape = CircleShape,
                    color = cs.primaryContainer,
                ) {
                    Text(
                        "$rewardedWatchedToday/$maxRewardedPerDay",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = typo.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onPrimaryContainer,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Daily streak ───────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "📅 Daily Login Streak",
                            style = typo.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                        )
                        Text(
                            if (canClaimStreak && isSignedIn) "Claim Day $streakDays bonus!"
                            else if (!isSignedIn) "Sign in with Google to earn"
                            else "Return tomorrow for Day ${streakDays % 7 + 1}",
                            style = typo.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { PointsManager.claimDailyStreak() },
                        enabled = canClaimStreak && isSignedIn,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            disabledContainerColor = cs.onSurface.copy(0.08f),
                        ),
                    ) {
                        Text(
                            if (canClaimStreak && isSignedIn) "Claim" else "✓ Done",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 7-day streak grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val rewards = listOf(10, 15, 20, 25, 30, 40, 50)
                    rewards.forEachIndexed { idx, pts ->
                        val dayNum = idx + 1
                        StreakCell(
                            day = dayNum,
                            pts = pts,
                            isDone = dayNum < streakDays,
                            isToday = dayNum == streakDays,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Referral card ──────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "🎁 Refer Friends",
                            style = typo.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                        )
                        Text(
                            "Your code: $myRefCode  (+50 pts each)",
                            style = typo.bodySmall,
                            color = cs.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Your friend also gets +50 pts on join!", style = typo.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onShareReferral,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    ) {
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = cs.outlineVariant.copy(0.5f))
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Have a referral code?", style = typo.bodySmall, color = cs.onSurfaceVariant)
                    TextButton(onClick = { showRedeemDialog = true }) {
                        Text("Redeem code", style = typo.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ══ HOW TO SPEND ══════════════════════════════════════════════════════
        SectionLabel("How Points Are Used")
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainer,
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                val spendRows = listOf(
                    Triple(ZLime, "🌍 Free Servers", "1 pt/min — 100 pts = 1h 40min"),
                    Triple(ZViolet, "⚡ Premium Servers", "5 pts/min — 100 pts = 20 min"),
                    Triple(ZHotPink, "👑 Premium+ Servers", "10 pts/min — 100 pts = 10 min"),
                )
                spendRows.forEachIndexed { i, (accent, title, desc) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Column {
                            Text(title, style = typo.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                            Text(desc, style = typo.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                    if (i < spendRows.lastIndex)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant.copy(0.4f), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Upgrade CTA ────────────────────────────────────────────────────────
        if (!isPremium) {
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(ZViolet, ZHotPink)),
                            shape = MaterialTheme.shapes.large,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "👑  Upgrade to Premium — No Ads",
                        style = typo.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Cancel anytime · All servers · All protocols",
                style = typo.labelSmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = MaterialTheme.shapes.large,
                color = cs.secondaryContainer,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✅", fontSize = 26.sp)
                    Column {
                        Text("You're ${userTier.label}", style = typo.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer)
                        Text("No ads · Unlimited · Thank you! 🙏", style = typo.bodySmall, color = cs.onSecondaryContainer.copy(0.7f))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Redeem dialog ──────────────────────────────────────────────────────────
    if (showRedeemDialog) {
        AlertDialog(
            onDismissRequest = { showRedeemDialog = false; redeemInput = "" },
            title = { Text("🎁 Redeem Referral Code", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a friend's code to earn +50 bonus points!", style = typo.bodyMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = redeemInput,
                        onValueChange = { redeemInput = it.uppercase() },
                        label = { Text("Code (e.g. ZVPN-ABC123)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRedeemCode(redeemInput.trim()); showRedeemDialog = false; redeemInput = "" },
                    enabled = redeemInput.length >= 6,
                ) { Text("Redeem", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRedeemDialog = false; redeemInput = "" }) { Text("Cancel") }
            },
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(16.dp).clip(CircleShape).background(ZViolet))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun WalletStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.65f))
    }
}

@Composable
private fun StreakCell(day: Int, pts: Int, isDone: Boolean, isToday: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(
        targetValue = when {
            isDone -> cs.primary.copy(0.5f)
            isToday -> cs.primary
            else -> cs.surfaceContainerHighest
        },
        animationSpec = tween(250),
        label = "streak",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .then(
                    if (isToday) Modifier.border(2.dp, cs.primary, RoundedCornerShape(10.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isDone) "✓" else "$day",
                color = if (isDone || isToday) cs.onPrimary else cs.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        Text(
            "+$pts",
            fontSize = 9.sp,
            color = if (isToday) cs.primary else cs.onSurfaceVariant.copy(0.6f),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
