@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.servers

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.UserTier
import com.zvpn.shared.resources.*
import org.jetbrains.compose.resources.painterResource

/**
 * Data class for displaying a public server (from backend servers.json).
 * Matches the clean backend fields: display_name, country_code, flag, protocol_label,
 * speed_mbps, latency_ms, tier.
 */
data class PublicServer(
    val id: String,
    val displayName: String,       // e.g. "Japan — VLESS"
    val countryCode: String,       // e.g. "JP"
    val countryName: String,       // e.g. "Japan"
    val flag: String,              // e.g. "🇯🇵"
    val protocolLabel: String,     // e.g. "VLESS"
    val speedMbps: Float,
    val latencyMs: Int,
    val tier: String,              // "free" | "premium" | "premium_plus"
    val profileUrl: String = "",
    val configUri: String = "",
    val engine: String = "OPENVPN",
)

/** Helper to detect light theme */
@Composable
fun isLightTheme(): Boolean {
    val surface = MaterialTheme.colorScheme.surface
    // Calculate relative luminance: simple approximation
    return (surface.red * 0.299f + surface.green * 0.587f + surface.blue * 0.114f) > 0.5f
}

/** Color for a ping value - theme-aware */
@Composable
fun pingColor(ms: Int): Color {
    val isLight = isLightTheme()
    return when {
        ms <= 0   -> MaterialTheme.colorScheme.outline
        ms < 80   -> if (isLight) Color(0xFF2E7D32) else Color(0xFF66BB6A)  // green
        ms < 200  -> if (isLight) Color(0xFFF57C00) else Color(0xFFFFB74D)  // orange
        else      -> if (isLight) Color(0xFFC62828) else Color(0xFFEF5350)  // red
    }
}

/** Tier badge color - theme-aware */
@Composable
fun tierColor(tier: String): Color {
    val isLight = isLightTheme()
    return when (tier.lowercase()) {
        "premium_plus" -> if (isLight) Color(0xFFF57F17) else Color(0xFFFFD54F)  // gold
        "premium"      -> if (isLight) Color(0xFF5E35B1) else Color(0xFF9575CD)  // purple
        else           -> if (isLight) Color(0xFF2E7D32) else Color(0xFF66BB6A)  // green (free)
    }
}

fun tierLabel(tier: String): String = when (tier.lowercase()) {
    "premium_plus" -> "PLUS"
    "premium"      -> "PRO"
    else           -> "FREE"
}

@Composable
fun PublicServerCard(
    server: PublicServer,
    isSelected: Boolean,
    userTier: UserTier,
    onClick: () -> Unit,
    onLockedClick: (() -> Unit)? = null,   // called when locked server tapped
    modifier: Modifier = Modifier,
) {
    val locked = !UserTier.canAccess(userTier, server.tier)
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            locked     -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else       -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(200),
        label = "card-bg",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { if (locked) onLockedClick?.invoke() else onClick() },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 1.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Flag ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = server.flag.ifBlank { "🌐" },
                    fontSize = 24.sp,
                )
            }

            // ── Country + Protocol ──────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.countryName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Protocol badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = server.protocolLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    // Speed
                    if (server.speedMbps > 0f) {
                        Text(
                            text = "${server.speedMbps.toInt()} Mbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Right: Ping + Tier badge ─────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Ping indicator
                if (server.latencyMs > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pingColor(server.latencyMs))
                        )
                        Text(
                            text = "${server.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = pingColor(server.latencyMs),
                        )
                    }
                }

                // Tier badge
                if (server.tier != "free") {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tierColor(server.tier).copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = tierLabel(server.tier),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor(server.tier),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Lock icon if inaccessible
                if (locked) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_lock),
                        contentDescription = "Locked",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
