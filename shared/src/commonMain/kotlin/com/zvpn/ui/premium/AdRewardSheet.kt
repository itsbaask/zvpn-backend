@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.zvpn.ui.premium

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.zvpn.ui.theme.ZHotPink
import com.zvpn.ui.theme.ZLime
import com.zvpn.ui.theme.ZViolet

/**
 * AdRewardSheet — compact bottom-sheet CTA for locked content.
 *
 * Uses MaterialTheme tokens only → correct in Dark AND Light mode.
 * Brand gradient (ZViolet→ZHotPink) used only for the primary CTA button,
 * consistent with connect button and hero elements throughout the app.
 */
@Composable
fun AdRewardSheet(
    todayPoints: Int,
    totalPoints: Int,
    remainingToday: Int,
    maxPerDay: Int = 8,
    rewardMinutesRemaining: Long,
    isAdReady: Boolean,
    isLoading: Boolean = false,
    onWatchAd: () -> Unit,
    onClaimStreak: () -> Unit = {},
    onShareReferral: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography

    val totalPts by PointsManager.totalPoints.collectAsState()
    val canClaimStreak by PointsManager.canClaimDailyStreak.collectAsState()
    val streakDays by PointsManager.streakDays.collectAsState()
    val myRefCode by PointsManager.referralCode.collectAsState()

    val adEnabled = isAdReady && remainingToday > 0 && !isLoading

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Handle ─────────────────────────────────────────────────────────────
        Box(
            Modifier
                .width(40.dp).height(4.dp)
                .clip(CircleShape)
                .background(cs.outlineVariant)
        )
        Spacer(Modifier.height(20.dp))

        // ── Active reward banner ───────────────────────────────────────────────
        if (rewardMinutesRemaining > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = cs.secondaryContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = cs.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Premium Active — $rewardMinutesRemaining min remaining",
                        style = typo.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Icon ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ZViolet, ZHotPink))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_bolt),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Earn Points & Rewards",
            style = typo.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
        )
        // Gradient accent line
        Box(
            Modifier
                .width(100.dp).height(2.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(ZViolet, ZHotPink, ZLime)))
        )

        Spacer(Modifier.height(6.dp))
        Text(
            "Watch ads to unlock premium servers!",
            style = typo.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        // ── Balance card ───────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainer,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "$totalPts pts",
                            style = typo.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.primary,
                        )
                        Text("Your balance", style = typo.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = cs.primaryContainer,
                    ) {
                        Text(
                            "Day $streakDays 🔥",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = typo.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cs.outlineVariant.copy(0.5f))
                Spacer(Modifier.height(10.dp))

                Text("Consumption rates while connected:", style = typo.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Text("Free: 1/min", style = typo.bodySmall, color = cs.onSurface)
                    Text("Premium: 5/min", style = typo.bodySmall, color = cs.primary)
                    Text("Plus: 10/min", style = typo.bodySmall, color = cs.onSurface)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Streak card ────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Daily Streak — Day $streakDays",
                        style = typo.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Text(
                        if (canClaimStreak) "Claim your daily bonus!" else "Come back tomorrow!",
                        style = typo.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { val r = PointsManager.claimDailyStreak(); if (r > 0) onClaimStreak() },
                    enabled = canClaimStreak,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (canClaimStreak) "Claim" else "✓", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Referral card ──────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = cs.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Refer Friends — +50 pts each", style = typo.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text("Code: $myRefCode", style = typo.bodySmall, color = cs.primary, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(onClick = onShareReferral, shape = MaterialTheme.shapes.medium) {
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Primary CTA — Watch Ad ─────────────────────────────────────────────
        Button(
            onClick = onWatchAd,
            enabled = adEnabled,
            modifier = Modifier.fillMaxWidth().height(54.dp),
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
                        if (adEnabled)
                            Modifier.background(
                                Brush.linearGradient(listOf(ZViolet, ZHotPink)),
                                shape = MaterialTheme.shapes.large,
                            )
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(isLoading, label = "ad-btn") { loading ->
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Watch Ad — +100 Pts & 30 Min Access",
                                style = typo.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (adEnabled) Color.White else cs.onSurface.copy(0.38f),
                            )
                            Text(
                                "Unlimited Ads · Watch anytime to earn",
                                style = typo.labelSmall,
                                color = Color.White.copy(0.7f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Open full wallet ───────────────────────────────────────────────────
        OutlinedButton(
            onClick = onOpenWallet,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Open Points Wallet", style = typo.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        // ── Upgrade CTA ────────────────────────────────────────────────────────
        FilledTonalButton(
            onClick = onUpgrade,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Go Premium — No Ads", style = typo.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss) {
            Text("Maybe later", color = cs.onSurfaceVariant, style = typo.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
    }
}
