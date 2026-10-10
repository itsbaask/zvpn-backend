@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.UserTier
import com.zvpn.shared.resources.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun PublicServersTab(
    servers: List<PublicServer>,
    selectedId: String?,
    userTier: UserTier,
    onSelect: (PublicServer) -> Unit,
    onLockedClick: (PublicServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTierTab by remember { mutableStateOf(ServerTier.Free) }
    var selectedProtocol by remember { mutableStateOf(ProtocolFilter.All) }
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.Recommended) }
    var favoriteIds by remember { mutableStateOf(setOf<String>()) }

    val protocols = remember(servers, activeTierTab) {
        listOf(ProtocolFilter.All) + servers
            .filter { it.tier == activeTierTab }
            .map { ProtocolFilter.fromId(it.protocolLabel.lowercase()) }
            .distinct()
            .sortedBy { it.label }
    }

    val filtered = remember(servers, activeTierTab, selectedProtocol, searchQuery, currentSort, favoriteIds) {
        val base = servers
            .filter { it.tier == activeTierTab }
            .filter { selectedProtocol == ProtocolFilter.All || it.protocolLabel.equals(selectedProtocol.label, ignoreCase = true) }
            .filter { searchQuery.isBlank() || it.countryName.contains(searchQuery, ignoreCase = true) || it.protocolLabel.contains(searchQuery, ignoreCase = true) }

        when (currentSort) {
            SortOption.Speed -> base.sortedByDescending { it.speedMbps }
            SortOption.Ping -> base.sortedBy { it.latencyMs ?: Int.MAX_VALUE }
            SortOption.Name -> base.sortedBy { it.countryName }
            SortOption.Favorites -> base.sortedByDescending { favoriteIds.contains(it.id) }
            SortOption.Recommended -> base.sortedWith(compareByDescending<PublicServer> { favoriteIds.contains(it.id) }.thenBy { it.latencyMs ?: Int.MAX_VALUE })
        }
    }

    val bestAutoServer = remember(servers) {
        servers.filter { UserTier.canAccess(userTier, it.tier.id) }
            .minByOrNull { it.latencyMs ?: 999 }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        if (bestAutoServer != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(bestAutoServer) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6),
                                    Color(0xFFEC4899)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🚀", fontSize = 28.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-Select Fastest",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${bestAutoServer.flag} ${bestAutoServer.countryName} · ${bestAutoServer.protocolLabel} · ${bestAutoServer.latencyMs ?: 0}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.95f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Button(
                            onClick = { onSelect(bestAutoServer) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search location or protocol…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
            )

            var sortExpanded by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(
                    onClick = { sortExpanded = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Text("Sort: ${currentSort.label}", style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                currentSort = option
                                sortExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = activeTierTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ServerTier.entries.forEach { tier ->
                val selected = activeTierTab == tier
                val locked = !UserTier.canAccess(userTier, tier.id)
                Tab(
                    selected = selected,
                    onClick = { if (locked) onLockedClick(servers.firstOrNull { it.tier == tier } ?: return@Tab) else activeTierTab = tier },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(when(tier) {
                                ServerTier.Free -> "🆓"
                                ServerTier.Premium -> "⭐"
                                ServerTier.PremiumPlus -> "👑"
                            })
                            Text(
                                when(tier) {
                                    ServerTier.Free -> "Free"
                                    ServerTier.Premium -> "Pro"
                                    ServerTier.PremiumPlus -> "VIP"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (locked) {
                                Icon(painterResource(Res.drawable.ic_lock), null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            protocols.forEach { proto ->
                FilterChip(
                    selected = selectedProtocol == proto,
                    onClick = { selectedProtocol = proto },
                    label = { Text(proto.label) },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filtered.size} nodes available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Sorted by: ${currentSort.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(4.dp))

        val listState = rememberLazyListState()
        AnimatedContent(targetState = activeTierTab, label = "tier-anim") { tier ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = filtered,
                    key = { server -> server.id },
                ) { server ->
                    val isFavorite = favoriteIds.contains(server.id)
                    PublicServerCard(
                        server = server,
                        isSelected = server.id == selectedId,
                        userTier = userTier,
                        isFavorite = isFavorite,
                        onToggleFavorite = {
                            favoriteIds = if (isFavorite) favoriteIds - server.id else favoriteIds + server.id
                        },
                        onClick = { onSelect(server) },
                        onLockedClick = { onLockedClick(server) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\"" else "No servers available in ${tier.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
