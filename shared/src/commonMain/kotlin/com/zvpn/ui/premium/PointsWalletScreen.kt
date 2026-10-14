@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.premium

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import org.jetbrains.compose.resources.painterResource
import com.zvpn.shared.resources.*

/**
 * Full-screen Points Wallet — Revenue-Maximizing Design
 *
 * Layout:
 *  1. Hero Balance card (current pts + tier badge)
 *  2. Earn section: Watch Ad button (primary CTA)
 *  3. Daily Streak ladder (7-day visual grid)
 *  4. Referral card (viral growth)
 *  5. Upgrade to Premium CTA
 *  6. How-to-use section (spending guide)
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
    val totalPts by PointsManager.totalPoints.collectAsState()
    val todayPts by PointsManager.todayPoints.collectAsState()
    val canClaimStreak by PointsManager.canClaimDailyStreak.collectAsState()
    val streakDays by PointsManager.streakDays.collectAsState()
    val myRefCode by PointsManager.referralCode.collectAsState()
    val isSignedIn = PointsManager.isGoogleSignedIn()

    var showRedeemDialog by remember { mutableStateOf(false) }
    var redeemInput by remember { mutableStateOf("") }

    val isPremium = userTier == UserTier.Premium || userTier == UserTier.PremiumPlus

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F0F23), Color(0xFF1A1040), Color(0xFF0F0F23))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Drag Handle ────────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .width(44.dp).height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
        )
        Spacer(Modifier.height(20.dp))

        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚡ Points Wallet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_close),
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Active Reward Banner ───────────────────────────────────────────────
        if (rewardMinutesRemaining > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF00C853).copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape)
                            .background(Color(0xFF00C853))
                    )
                    Text(
                        text = "✅ Premium Active — $rewardMinutesRemaining min remaining",
                        color = Color(0xFF00C853),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Main Balance Card ──────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6C63FF), Color(0xFF9C27B0), Color(0xFF3F51B5))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(28.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Tier badge
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "$totalPts",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD700),
                    )
                    Text(
                        text = "POINTS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 4.sp,
                    )

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        BalanceStat("Today", "+$todayPts pts", Color(0xFF69F0AE))
                        BalanceStat("Streak", "Day $streakDays 🔥", Color(0xFFFFD740))
                        BalanceStat(
                            "Tier Rate",
                            when (userTier) {
                                UserTier.PremiumPlus -> "10 pts/min"
                                UserTier.Premium, UserTier.AdRewarded -> "5 pts/min"
                                else -> "1 pt/min"
                            },
                            Color(0xFF82B1FF),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ══════════════════════ EARN SECTION ══════════════════════
        SectionHeader("🎯 Earn Points", Color(0xFF69F0AE))
        Spacer(Modifier.height(12.dp))

        // ── Rewarded Ad CTA (Primary Revenue Driver) ──────────────────────────
        val adBtnEnabled = isAdReady && rewardedWatchedToday < maxRewardedPerDay && !isAdLoading
        val remainingAds = maxOf(0, maxRewardedPerDay - rewardedWatchedToday)

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Button(
                onClick = onWatchAd,
                enabled = adBtnEnabled,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF),
                    disabledContainerColor = Color.White.copy(alpha = 0.07f),
                ),
            ) {
                AnimatedContent(isAdLoading, label = "ad-btn") { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (remainingAds > 0)
                                    "🎬  Watch Ad — +100 Points + 30 min"
                                else
                                    "📵 Daily limit reached ($maxRewardedPerDay/$maxRewardedPerDay)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (adBtnEnabled) Color.White else Color.White.copy(0.4f),
                            )
                            if (remainingAds > 0) {
                                Text(
                                    text = "$remainingAds ads remaining today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(0.65f),
                                )
                            }
                        }
                    }
                }
            }

            // Progress badge
            if (rewardedWatchedToday > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-8).dp),
                    shape = CircleShape,
                    color = Color(0xFFFFD700),
                ) {
                    Text(
                        text = "$rewardedWatchedToday/$maxRewardedPerDay",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A1A),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Daily Streak Card ──────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF6C63FF).copy(alpha = 0.12f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "📅 Daily Login Streak",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            if (canClaimStreak && isSignedIn)
                                "Claim your Day $streakDays bonus!"
                            else if (!isSignedIn)
                                "Sign in with Google to earn streak bonuses"
                            else
                                "Come back tomorrow! Day ${streakDays % 7 + 1} next",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(0.65f),
                        )
                    }
                    Button(
                        onClick = {
                            val reward = PointsManager.claimDailyStreak()
                        },
                        enabled = canClaimStreak && isSignedIn,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6C63FF),
                            disabledContainerColor = Color.White.copy(0.08f),
                        ),
                    ) {
                        Text(
                            if (canClaimStreak && isSignedIn) "Claim" else "Claimed",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 7-day streak grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val streakRewards = listOf(10, 15, 20, 25, 30, 40, 50)
                    streakRewards.forEachIndexed { idx, pts ->
                        val dayNum = idx + 1
                        val isDone = dayNum < streakDays
                        val isToday = dayNum == streakDays
                        StreakDayCell(
                            day = dayNum,
                            pts = pts,
                            isDone = isDone,
                            isToday = isToday,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Referral Card ──────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFF9800).copy(alpha = 0.10f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "🎁 Refer Friends — +50 pts each",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "Your code: $myRefCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFCC80),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Friend also gets +50 pts on join!",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.55f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onShareReferral,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFF9800),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Share 🚀", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(0.08f))
                Spacer(Modifier.height(10.dp))

                // Redeem a friend's code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Have a referral code?",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.6f),
                    )
                    TextButton(onClick = { showRedeemDialog = true }) {
                        Text(
                            "Redeem code",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ══════════════════════ SPEND SECTION ══════════════════════
        SectionHeader("💎 How to Use Points", Color(0xFF82B1FF))
        Spacer(Modifier.height(12.dp))

        val spendItems = listOf(
            Triple("🌍", "Free Servers", "1 pt/min — 100 pts = 1h 40min"),
            Triple("⚡", "Premium Servers", "5 pts/min — 100 pts = 20 min"),
            Triple("👑", "Premium+ Servers", "10 pts/min — 100 pts = 10 min"),
        )

        spendItems.forEach { (icon, title, desc) ->
            SpendRow(icon = icon, title = title, desc = desc)
        }

        Spacer(Modifier.height(20.dp))

        // ── Upgrade to Premium CTA ─────────────────────────────────────────────
        if (!isPremium) {
            Button(
                onClick = onUpgrade,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color(0xFF1A1A1A),
                ),
            ) {
                Text(
                    "👑 Upgrade to Premium — Unlimited Access",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "No ads • All servers • All protocols • Cancel anytime",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.45f),
                textAlign = TextAlign.Center,
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF00C853).copy(0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✅", fontSize = 28.sp)
                    Column {
                        Text(
                            "You're ${userTier.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF69F0AE),
                        )
                        Text(
                            "No ads • Unlimited access • Thank you! 🙏",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(0.6f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Redeem Code Dialog ─────────────────────────────────────────────────────
    if (showRedeemDialog) {
        AlertDialog(
            onDismissRequest = { showRedeemDialog = false; redeemInput = "" },
            title = {
                Text("🎁 Redeem Referral Code", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Enter a friend's code to earn +50 bonus points!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = redeemInput,
                        onValueChange = { redeemInput = it.uppercase() },
                        label = { Text("Code (e.g. ZVPN-ABC123)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRedeemCode(redeemInput.trim())
                        showRedeemDialog = false
                        redeemInput = ""
                    },
                    enabled = redeemInput.length >= 6,
                ) {
                    Text("Redeem", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRedeemDialog = false; redeemInput = "" }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
    }
}

@Composable
private fun BalanceStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.55f))
    }
}

@Composable
private fun StreakDayCell(day: Int, pts: Int, isDone: Boolean, isToday: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isDone -> Color(0xFF6C63FF).copy(0.5f)
            isToday -> Color(0xFF6C63FF)
            else -> Color.White.copy(0.06f)
        },
        label = "streak-bg",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .then(
                    if (isToday) Modifier.border(2.dp, Color(0xFFFFD700), RoundedCornerShape(10.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isDone) {
                Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            } else {
                Text(
                    "$day",
                    color = if (isToday) Color.White else Color.White.copy(0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Text(
            "+$pts",
            fontSize = 9.sp,
            color = if (isToday) Color(0xFFFFD700) else Color.White.copy(0.45f),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SpendRow(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 22.sp)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
        }
    }
}
