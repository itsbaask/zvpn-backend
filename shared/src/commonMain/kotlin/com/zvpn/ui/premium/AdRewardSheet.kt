@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.premium

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import org.jetbrains.compose.resources.painterResource
import com.zvpn.shared.resources.*
import com.zvpn.domain.ads.PointsManager

/**
 * Ad Reward & Points Monetization Sheet.
 *
 * Features:
 *  - Watch Rewarded Ad -> +10 Points
 *  - Consumption rates: Free (1 pt/min), Premium (5 pts/min), Premium+ (10 pts/min)
 *  - Daily Streak Bonus Claim (+10 to +50 pts)
 *  - Viral Referral Program (+50 pts for share)
 */
@Composable
fun AdRewardSheet(
    todayPoints: Int,
    totalPoints: Int,
    remainingToday: Int,          // remaining rewarded ads today
    maxPerDay: Int = 10,
    rewardMinutesRemaining: Long, // 0 if no active reward
    isAdReady: Boolean,
    isLoading: Boolean = false,
    onWatchAd: () -> Unit,
    onClaimStreak: () -> Unit = {},
    onShareReferral: () -> Unit = {},
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalPts by PointsManager.totalPoints.collectAsState()
    val canClaimStreak by PointsManager.canClaimDailyStreak.collectAsState()
    val streakDays by PointsManager.streakDays.collectAsState()
    val myRefCode by PointsManager.referralCode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Handle ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        )
        Spacer(Modifier.height(20.dp))

        // ── Active reward countdown ────────────────────────────────────────
        if (rewardMinutesRemaining > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Premium Active — ${rewardMinutesRemaining} min remaining",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Title ──────────────────────────────────────────────────────────
        Text("⚡", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ad Rewards & Points",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Earn points from ads, daily login & referrals to unlock all paid features!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // ── Points Balance ─────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$totalPts Points",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                )
                Text(
                    text = "Current Points Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Time Rates Per Minute:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Text("🟢 Free: 1 pt/min", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Text("⚡ Premium: 5 pts/min", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6C63FF))
                    Text("👑 Premium+: 10 pts/min", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD700))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Daily Streak Login Bonus Card ──────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF6C63FF).copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📅 Daily Login Streak: Day $streakDays",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (canClaimStreak) "Claim your daily bonus points!" else "Already claimed today! Return tomorrow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Button(
                    onClick = {
                        val reward = PointsManager.claimDailyStreak()
                        if (reward > 0) onClaimStreak()
                    },
                    enabled = canClaimStreak,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                ) {
                    Text(if (canClaimStreak) "Claim" else "Claimed", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Viral Referral Program Card ────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFF9800).copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎁 Viral Referral Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Code: $myRefCode (+50 pts for each friend)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                FilledTonalButton(
                    onClick = {
                        PointsManager.addPoints(50)
                        onShareReferral()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFF9800), contentColor = Color.White),
                ) {
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Watch Rewarded Ad button ───────────────────────────────────────
        Button(
            onClick = {
                PointsManager.addPoints(10)
                onWatchAd()
            },
            enabled = isAdReady && remainingToday > 0 && !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6C63FF),
                disabledContainerColor = Color.White.copy(alpha = 0.1f),
            ),
        ) {
            AnimatedContent(isLoading, label = "btn") { loading ->
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_bolt),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (remainingToday > 0) "Watch Ad (+10 Points)" else "Daily ad limit reached",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Upgrade button ─────────────────────────────────────────────────
        OutlinedButton(
            onClick = onUpgrade,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD700)),
        ) {
            Text(
                text = "⚡ Upgrade to Premium — Unlimited Access, No Ads",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Close", color = Color.White.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(8.dp))
    }
}
