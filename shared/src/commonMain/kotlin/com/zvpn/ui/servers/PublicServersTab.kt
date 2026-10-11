@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.style.TextOverflow
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
    var selectedContinent by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.Recommended) }
    
    // Set of expanded country names
    var expandedCountries by remember { mutableStateOf(setOf<String>()) }

    val continents = remember(servers) {
        listOf("All") + servers.map { it.continent }.filter { it.isNotBlank() && it != "Global" }.distinct().sorted()
    }

    val protocols = remember(servers, activeTierTab) {
        listOf(ProtocolFilter.All) + servers
            .filter { it.tier == activeTierTab }
            .map { ProtocolFilter.fromId(it.protocolLabel.lowercase()) }
            .distinct()
            .sortedBy { it.label }
    }

    val filteredServers = remember(servers, activeTierTab, selectedProtocol, selectedContinent, searchQuery, currentSort) {
        val base = servers
            .filter { it.tier == activeTierTab }
            .filter { selectedProtocol == ProtocolFilter.All || it.protocolLabel.equals(selectedProtocol.label, ignoreCase = true) }
            .filter { selectedContinent == "All" || it.continent.equals(selectedContinent, ignoreCase = true) }
            .filter { searchQuery.isBlank() || it.countryName.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) }

        when (currentSort) {
            SortOption.Speed -> base.sortedByDescending { it.speedMbps }
            SortOption.Ping -> base.sortedBy { it.latencyMs ?: Int.MAX_VALUE }
            SortOption.Name -> base.sortedBy { it.countryName }
            SortOption.Favorites, SortOption.Recommended -> base.sortedWith(compareBy<PublicServer> { it.countryName }.thenBy { it.latencyMs ?: Int.MAX_VALUE })
        }
    }

    // Group servers by Country Name
    val countryGroups = remember(filteredServers) {
        filteredServers.groupBy { it.countryName.ifBlank { "Global" } }
    }

    val bestAutoServer = remember(servers) {
        servers.filter { UserTier.canAccess(userTier, it.tier.id) }
            .minByOrNull { it.latencyMs ?: 999 }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        // 1. Auto-Select Quick Banner
        if (bestAutoServer != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(bestAutoServer) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp,
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
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 24.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-Select Fastest Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.height(2.dp))
                            val cleanName = bestAutoServer.displayName.replace(Regex("""\s*\[.*?]"""), "").trim()
                            Text(
                                "$cleanName · ${bestAutoServer.latencyMs ?: 0}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.95f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Button(
                            onClick = { onSelect(bestAutoServer) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 2. Search & Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search country, location...") },
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(currentSort.label, style = MaterialTheme.typography.labelMedium)
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

        Spacer(Modifier.height(10.dp))

        // 3. Tier Tabs (Free, Pro, VIP)
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

        Spacer(Modifier.height(10.dp))

        // 4. Continent Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            continents.forEach { cont ->
                FilterChip(
                    selected = selectedContinent == cont,
                    onClick = { selectedContinent = cont },
                    label = {
                        Text(
                            when(cont) {
                                "All" -> "🌍 All Regions"
                                "Asia" -> "🌏 Asia"
                                "Europe" -> "🇪🇺 Europe"
                                "North America" -> "🌎 N. America"
                                "Middle East" -> "🇦🇪 Middle East"
                                "South America" -> "🌎 S. America"
                                "Africa" -> "🌍 Africa"
                                "Oceania" -> "🌏 Oceania"
                                else -> cont
                            }
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // 5. Protocol Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
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
                text = "${countryGroups.size} countries (${filteredServers.size} servers)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap country to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 6. Country Accordion Cards List
        val listState = rememberLazyListState()
        AnimatedContent(targetState = activeTierTab, label = "tier-anim") { tier ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = countryGroups.keys.toList(),
                    key = { countryName -> countryName },
                ) { countryName ->
                    val countryServers = countryGroups[countryName] ?: emptyList()
                    val firstServer = countryServers.firstOrNull()
                    val flag = firstServer?.flag ?: "🌐"
                    val continent = firstServer?.continent ?: "Global"
                    val isExpanded = expandedCountries.contains(countryName)

                    CountryAccordionCard(
                        countryName = countryName,
                        flag = flag,
                        continent = continent,
                        servers = countryServers,
                        selectedId = selectedId,
                        userTier = userTier,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedCountries = if (isExpanded) {
                                expandedCountries - countryName
                            } else {
                                expandedCountries + countryName
                            }
                        },
                        onSelectServer = onSelect,
                        onLockedClick = onLockedClick,
                    )
                }

                if (countryGroups.isEmpty()) {
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

@Composable
private fun CountryAccordionCard(
    countryName: String,
    flag: String,
    continent: String,
    servers: List<PublicServer>,
    selectedId: String?,
    userTier: UserTier,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectServer: (PublicServer) -> Unit,
    onLockedClick: (PublicServer) -> Unit,
) {
    val bestPing = remember(servers) { servers.mapNotNull { it.latencyMs }.filter { it > 0 }.minOrNull() }
    val bestPingColor = pingColor(bestPing)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = flag.ifBlank { "🌐" }, fontSize = 26.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = countryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${servers.size} locations · $continent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (bestPing != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = bestPingColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "$bestPing ms",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = bestPingColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Icon(
                    painter = painterResource(
                        if (isExpanded) Res.drawable.ic_keyboard_arrow_down
                        else Res.drawable.ic_chevron_right
                    ),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    servers.forEach { server ->
                        ServerRowItem(
                            server = server,
                            isSelected = server.id == selectedId,
                            userTier = userTier,
                            onClick = { onSelectServer(server) },
                            onLockedClick = { onLockedClick(server) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRowItem(
    server: PublicServer,
    isSelected: Boolean,
    userTier: UserTier,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    val locked = !UserTier.canAccess(userTier, server.tier.id)
    val cleanDisplayName = remember(server.displayName) {
        server.displayName.replace(Regex("""\s*\[.*?\]"""), "").trim()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { if (locked) onLockedClick() else onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                enabled = !locked,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )

            Text(
                text = cleanDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            server.latencyMs?.let { ms ->
                if (ms > 0) {
                    Text(
                        text = "$ms ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = pingColor(ms),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (locked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
