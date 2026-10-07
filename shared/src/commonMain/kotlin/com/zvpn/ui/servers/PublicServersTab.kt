@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zvpn.domain.billing.UserTier

enum class ServerTab(val label: String, val emoji: String) {
    Free("Free", "🆓"),
    Premium("Premium", "⭐"),
    PremiumPlus("Premium+", "👑"),
}

@Composable
fun PublicServersTab(
    servers: List<PublicServer>,
    selectedId: String?,
    userTier: UserTier,
    onSelect: (PublicServer) -> Unit,
    onLockedClick: (PublicServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(ServerTab.Free) }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(servers, activeTab, searchQuery) {
        servers
            .filter { it.tier == activeTab.name.lowercase().replace("premiumplus", "premium_plus") }
            .filter { searchQuery.isBlank() || it.countryName.contains(searchQuery, ignoreCase = true) || it.protocolLabel.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Search Bar ───────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by country or protocol…") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            leadingIcon = {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
        )

        // ── Tabs ─────────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = activeTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ServerTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(tab.emoji, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Stats row ────────────────────────────────────────────────────────
        Text(
            text = "${filtered.size} servers available",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // ── Server List ──────────────────────────────────────────────────────
        val listState = rememberLazyListState()
        AnimatedContent(targetState = activeTab, label = "tab-anim") { tab ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = filtered,
                    key = { it.id },
                ) { server ->
                    PublicServerCard(
                        server = server,
                        isSelected = server.id == selectedId,
                        userTier = userTier,
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
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\"" else "No ${tab.label} servers available",
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
