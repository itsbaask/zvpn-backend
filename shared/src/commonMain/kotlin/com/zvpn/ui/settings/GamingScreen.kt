@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.gaming.GamingBoostManager
import com.zvpn.shared.resources.*
import org.jetbrains.compose.resources.painterResource

data class GamePreset(
    val id: String,
    val name: String,
    val packageName: String,
    val enabled: Boolean = true,
)

data class GamePingCluster(
    val region: String,
    val serverHost: String,
    val pingMs: Int,
)

@Composable
fun GamingScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val isEnabled by GamingBoostManager.isGamingBoostEnabled.collectAsState()
    val activeMtu by GamingBoostManager.activeMtu.collectAsState()

    var customMtu by remember { mutableStateOf(activeMtu.toFloat()) }
    var udpDirectEnabled by remember { mutableStateOf(true) }
    var jitterSmoothingEnabled by remember { mutableStateOf(true) }
    var selectedDns by remember { mutableStateOf("1.1.1.1 (Cloudflare Gaming)") }

    var gamesList by remember {
        mutableStateOf(
            listOf(
                GamePreset("pubg", "PUBG Mobile", "com.tencent.ig"),
                GamePreset("cod", "Call of Duty: Mobile", "com.activision.callofduty.shooter"),
                GamePreset("freefire", "Free Fire", "com.dts.freefireth"),
                GamePreset("roblox", "Roblox", "com.roblox.client"),
                GamePreset("wildrift", "League of Legends: Wild Rift", "com.riotgames.league.wildrift"),
                GamePreset("genshin", "Genshin Impact", "com.miHoYo.GenshinImpact"),
            )
        )
    }

    val pingClusters = listOf(
        GamePingCluster("Middle East (Riyadh)", "157.241.0.1", 24),
        GamePingCluster("Europe (Frankfurt)", "185.255.179.1", 58),
        GamePingCluster("Asia (Singapore)", "103.169.142.1", 82),
        GamePingCluster("US East (Virginia)", "146.70.244.1", 110),
    )

    SettingsPageScaffold(
        title = "Gaming Acceleration & Optimization",
        subtitle = "Comprehensive UDP fast-path, low-latency MTU tuning & game proxy routing",
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        // ── Main Master Toggle Card ─────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { GamingBoostManager.setGamingBoostEnabled(!isEnabled) }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gaming Acceleration Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isEnabled) "Active — High UDP Priority & Anti-Jitter Packet Routing" else "Disabled — Click to enable gaming packet optimization",
                        style = MaterialTheme.typography.bodySmall,
                        color = (if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f),
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { GamingBoostManager.setGamingBoostEnabled(it) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(visible = isEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // ── Game App Selector Section ─────────────────────────────────────
                SettingsGroup(title = "Selected Games for Proxy Acceleration") {
                    gamesList.forEach { game ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = game.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = game.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = game.enabled,
                                onCheckedChange = { checked ->
                                    gamesList = gamesList.map { if (it.id == game.id) it.copy(enabled = checked) else it }
                                },
                            )
                        }
                    }
                }

                // ── Network & Packet Optimization Section ────────────────────────
                SettingsGroup(title = "Network & Proxy Packet Tuning") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UDP Direct Fast-Path", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Bypasses TCP encapsulation bottlenecks for real-time game packets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = udpDirectEnabled, onCheckedChange = { udpDirectEnabled = it })
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Jitter & Packet Loss Minimizer", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Anti-bufferbloat smoothing for Wi-Fi and 4G/5G connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = jitterSmoothingEnabled, onCheckedChange = { jitterSmoothingEnabled = it })
                    }

                    // MTU Tuning Slider
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Gaming MTU Packet Size", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("${customMtu.toInt()} bytes", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("1380 bytes recommended for LTE/5G • 1420 bytes for Fiber/Wi-Fi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = customMtu,
                            onValueChange = { customMtu = it },
                            valueRange = 1200f..1492f,
                            steps = 29,
                        )
                    }
                }

                // ── Regional Game Cluster Ping Telemetry ─────────────────────────
                SettingsGroup(title = "Regional Game Cluster Ping Telemetry") {
                    pingClusters.forEach { cluster ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(cluster.region, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(cluster.serverHost, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "${cluster.pingMs} ms",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
