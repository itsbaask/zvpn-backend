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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.UserTier
import com.zvpn.shared.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun pingColor(ms: Int?): Color = when {
    ms == null || ms <= 0 -> MaterialTheme.colorScheme.outline
    ms < 70               -> Color(0xFF10B981)  // vibrant green
    ms < 180              -> Color(0xFFF59E0B)  // vibrant amber
    else                  -> Color(0xFFEF4444)  // vibrant red
}

@Composable
fun tierColor(tier: ServerTier): Color = when (tier) {
    ServerTier.PremiumPlus -> Color(0xFFFFD700)  // gold
    ServerTier.Premium     -> Color(0xFF8B5CF6)  // royal purple
    else                   -> Color(0xFF10B981)  // emerald green
}

@Composable
fun PublicServerCard(
    server: PublicServer,
    isSelected: Boolean,
    userTier: UserTier,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locked = !UserTier.canAccess(userTier, server.tier.id)
    val isVip = server.tier != ServerTier.Free

    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            locked     -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else       -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(250),
        label = "card-bg",
    )

    val descText = "${server.countryName}, protocol ${server.protocolLabel}, speed ${server.speedMbps.toInt()} Mbps, ping ${server.latencyMs ?: "untested"}, ${if (locked) "locked" else "available"}"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = descText
            }
            .clickable { if (locked) onLockedClick() else onClick() },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        shadowElevation = if (isSelected) 6.dp else 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isVip && !locked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFD700), Color(0xFF8B5CF6))
                            )
                        )
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Flag Avatar
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = server.flag.ifBlank { "🌐" },
                        fontSize = 28.sp,
                    )
                }

                // Country + Protocol + Speed
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = server.countryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (server.tier != ServerTier.Free) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = tierColor(server.tier).copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = when(server.tier) {
                                        ServerTier.PremiumPlus -> "PREMIUM+"
                                        ServerTier.Premium -> "PREMIUM"
                                        else -> "FREE"
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tierColor(server.tier),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "🔒 " + stringResource(Res.string.privacy_encrypted_tunnel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right: Favorite toggle + Ping status + Lock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check_circle),
                            contentDescription = "Toggle favorite",
                            tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pingColor(server.latencyMs).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(pingColor(server.latencyMs))
                                )
                                Text(
                                    text = if (server.latencyMs != null) "${server.latencyMs} ms" else "—",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = pingColor(server.latencyMs),
                                )
                            }
                        }

                        if (locked) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_lock),
                                contentDescription = "Locked",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
