@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.premium

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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

/**
 * Ad Reward Screen — shown to free users to get temporary premium access.
 *
 * Features:
 *  - Shows remaining free ad slots today (e.g. "3 of 5 remaining")
 *  - Points progress bar
 *  - Watch ad button → triggers [onWatchAd]
 *  - Upgrade button → triggers [onUpgrade]
 *  - Countdown when reward is active
 */
@Composable
fun AdRewardSheet(
    todayPoints: Int,
    totalPoints: Int,
    remainingToday: Int,          // remaining rewarded ads today
    maxPerDay: Int = 5,
    rewardMinutesRemaining: Long, // 0 if no active reward
    isAdReady: Boolean,
    isLoading: Boolean = false,
    onWatchAd: () -> Unit,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            )
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
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    Text(
                        text = "Premium active — ${rewardMinutesRemaining}min remaining",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Title ──────────────────────────────────────────────────────────
        Text("🎬", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Free Premium Access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Watch a short ad → get ${30} minutes premium",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // ── Daily progress ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Today's ads",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                text = "$remainingToday of $maxPerDay remaining",
                style = MaterialTheme.typography.labelMedium,
                color = if (remainingToday > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (maxPerDay - remainingToday).toFloat() / maxPerDay },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF6C63FF),
            trackColor = Color.White.copy(alpha = 0.1f),
        )

        Spacer(Modifier.height(20.dp))

        // ── Points ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PointsStat(label = "Today", value = "$todayPoints pts", color = Color(0xFFFFD700))
            PointsStat(label = "Total", value = "$totalPoints pts", color = Color(0xFF6C63FF))
        }

        Spacer(Modifier.height(28.dp))

        // ── Watch Ad button ────────────────────────────────────────────────
        Button(
            onClick = onWatchAd,
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
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Text(
                            text = if (remainingToday > 0) "Watch Ad (+100 pts)" else "Daily limit reached",
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
                text = "⭐ Upgrade to Premium — no ads, forever",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Maybe later", color = Color.White.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PointsStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}
