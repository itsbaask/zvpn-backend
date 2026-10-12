@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.UserTier
import com.zvpn.shared.resources.*
import org.jetbrains.compose.resources.painterResource

/*
 * World-Class Modern VPN Server Architecture & Redesign
 *  - Level 1: Continents (Vibrant Regional Badges & Expandable Accordion)
 *  - Level 2: Countries (32dp Flag Badges, Best Ping Pills, Expandable Accordion)
 *  - Level 3: Individual Servers (Protocol Pills, Quality Dot Indicators, Quick Select)
 *  - Pinned Sticky Header: Tier Selector + Complete 18-Protocol Horizontal Picker
 *  - 100% Material 3 Light & Dark Mode Adaptive Aesthetics
 */

private val BRACKET_TAG = Regex("""\s*\[.*?]""")

private fun cleanName(raw: String): String = raw.replace(BRACKET_TAG, "").trim()

private fun tierLabel(tier: ServerTier): String = when (tier) {
    ServerTier.Free -> "Free 🆓"
    ServerTier.Premium -> "Pro ⭐"
    ServerTier.PremiumPlus -> "VIP 👑"
}

private fun continentEmoji(cont: String): String = when (cont.lowercase().trim()) {
    "asia", "asia-pacific" -> "🌏"
    "europe" -> "🇪🇺"
    "americas", "north america", "south america" -> "🌎"
    "middle east", "me/africa", "africa" -> "🌍"
    "oceania" -> "🌏"
    else -> "🌐"
}

private fun pingQualityColor(ping: Int?, scheme: ColorScheme): Color = when {
    ping == null || ping <= 0 -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
    ping < 60 -> Color(0xFF10B981)   // Vibrant Emerald Green
    ping < 150 -> Color(0xFFF59E0B)  // Vibrant Warm Amber
    else -> scheme.outlineVariant
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
    var activeTierTab by remember { mutableStateOf(ServerTier.Free) }
    var selectedProtocol by remember { mutableStateOf(ProtocolFilter.All) }
    var selectedContinent by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.Recommended) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Expanded continents and countries sets
    var expandedContinents by remember { mutableStateOf(setOf<String>()) }
    var expandedCountries by remember { mutableStateOf(setOf<String>()) }

    // Automatically expand the continent & country of the selected server on first launch
    LaunchedEffect(Unit) {
        val current = servers.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        if (UserTier.canAccess(userTier, current.tier.id)) activeTierTab = current.tier
        expandedContinents = expandedContinents + current.continent
        expandedCountries = expandedCountries + current.countryName.ifBlank { "Global" }
    }

    val tierCounts = remember(servers) { servers.groupingBy { it.tier }.eachCount() }

    val protoCounts = remember(servers, activeTierTab) {
        servers.filter { it.tier == activeTierTab }
            .groupingBy { ProtocolFilter.fromId(it.protocolLabel) }
            .eachCount()
    }

    val filteredServers = remember(servers, activeTierTab, selectedProtocol, selectedContinent, searchQuery, currentSort) {
        val base = servers
            .filter { it.tier == activeTierTab }
            .filter { selectedProtocol == ProtocolFilter.All || it.protocolLabel.equals(selectedProtocol.label, ignoreCase = true) || (selectedProtocol == ProtocolFilter.VLESS && it.protocolLabel.contains("vless", ignoreCase = true)) }
            .filter { selectedContinent == "All" || it.continent.equals(selectedContinent, ignoreCase = true) }
            .filter { searchQuery.isBlank() || it.countryName.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) || it.protocolLabel.contains(searchQuery, ignoreCase = true) }

        when (currentSort) {
            SortOption.Speed -> base.sortedByDescending { it.speedMbps }
            SortOption.Ping -> base.sortedBy { it.latencyMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE }
            SortOption.Name -> base.sortedBy { it.countryName }
            SortOption.Favorites, SortOption.Recommended -> base.sortedWith(
                compareBy<PublicServer> { it.countryName }.thenBy { it.latencyMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE },
            )
        }
    }

    // Group 1: Continent -> Group 2: Country -> List of Servers
    val continentGroups = remember(filteredServers) {
        filteredServers.groupBy { it.continent.ifBlank { "Global" } }
            .mapValues { (_, sList) -> sList.groupBy { it.countryName.ifBlank { "Global" } } }
    }

    val bestAutoServer = remember(servers, userTier) {
        val accessible = servers.filter { UserTier.canAccess(userTier, it.tier.id) }
        accessible.filter { (it.latencyMs ?: 0) > 0 }.minByOrNull { it.latencyMs ?: Int.MAX_VALUE }
            ?: accessible.firstOrNull()
    }

    val hasActiveFilters = searchQuery.isNotBlank() || selectedProtocol != ProtocolFilter.All || selectedContinent != "All"
    val listState = rememberLazyListState()

    if (showFilterSheet) {
        FilterBottomSheet(
            selectedProtocol = selectedProtocol,
            onProtocolSelect = { selectedProtocol = it },
            selectedContinent = selectedContinent,
            onContinentSelect = { selectedContinent = it },
            currentSort = currentSort,
            onSortSelect = { currentSort = it },
            protoCounts = protoCounts,
            onDismiss = { showFilterSheet = false }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Auto-Select Hero Banner
        if (bestAutoServer != null) {
            item(key = "auto-banner") {
                AutoSelectBanner(server = bestAutoServer, onClick = { onSelect(bestAutoServer) })
            }
        }

        // 2. Search & Filter Bar
        item(key = "search") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country, protocol, server...", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = "Clear")
                            }
                        }
                    },
                )

                FilledTonalIconButton(
                    onClick = { showFilterSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (hasActiveFilters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tune),
                        contentDescription = "Filters",
                        tint = if (hasActiveFilters) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. PINNED STICKY HEADER: Tier Tabs + Complete Protocol Selection Row
        stickyHeader(key = "sticky-controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tier Selector
                TierSelector(
                    active = activeTierTab,
                    userTier = userTier,
                    counts = tierCounts,
                    onSelect = { activeTierTab = it; selectedProtocol = ProtocolFilter.All },
                    onLocked = { tier -> servers.firstOrNull { it.tier == tier }?.let(onLockedClick) },
                )

                // Complete Protocol Selector Bar (ALL 18 Protocols)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProtocolFilter.entries.forEach { proto ->
                        val count = if (proto == ProtocolFilter.All) filteredServers.size else (protoCounts[proto] ?: 0)
                        val isSelected = selectedProtocol == proto
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedProtocol = proto },
                            label = {
                                Text(
                                    if (proto == ProtocolFilter.All) "⚡ All Protocols ($count)" else "${proto.label} ($count)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = CircleShape,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // 4. Summary Row
        if (continentGroups.isNotEmpty()) {
            item(key = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${continentGroups.size} Continents · ${filteredServers.size} Servers Available",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 5. Level 1: Continent Accordion Cards
        items(items = continentGroups.keys.toList(), key = { it }) { contName ->
            val countryMap = continentGroups[contName] ?: emptyMap()
            val contServers = countryMap.values.flatten()
            val isContExpanded = contName in expandedContinents || selectedContinent != "All" || searchQuery.isNotBlank()

            ContinentAccordionCard(
                modifier = Modifier.animateItem(),
                continentName = contName,
                emoji = continentEmoji(contName),
                countryMap = countryMap,
                totalServers = contServers.size,
                selectedId = selectedId,
                userTier = userTier,
                isExpanded = isContExpanded,
                expandedCountries = expandedCountries,
                onToggleExpand = {
                    expandedContinents = if (isContExpanded) expandedContinents - contName else expandedContinents + contName
                },
                onToggleCountryExpand = { country ->
                    expandedCountries = if (country in expandedCountries) expandedCountries - country else expandedCountries + country
                },
                onSelectServer = onSelect,
                onLockedClick = onLockedClick,
            )
        }

        // 6. Empty Results
        if (continentGroups.isEmpty()) {
            item(key = "empty") {
                EmptyResults(
                    message = if (searchQuery.isNotEmpty()) "No servers found for \"$searchQuery\""
                    else "No servers available in ${tierLabel(activeTierTab)}",
                    showReset = hasActiveFilters,
                    onReset = {
                        searchQuery = ""
                        selectedProtocol = ProtocolFilter.All
                        selectedContinent = "All"
                    },
                )
            }
        }
    }
}

// ───────────────────────────── Hero AutoSelect Banner ─────────────────────────────

@Composable
private fun AutoSelectBanner(server: PublicServer, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val ping = server.latencyMs?.takeIf { it > 0 }
    val subtitle = if (ping != null) "${cleanName(server.displayName)} · $ping ms" else cleanName(server.displayName)

    val gradient = remember(scheme.primary, scheme.secondaryContainer) {
        Brush.horizontalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = scheme.primary.copy(alpha = 0.15f), spotColor = scheme.primary.copy(alpha = 0.25f))
            .clickable(onClick = onClick),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.background(gradient).padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(scheme.primary, CircleShape)
                        .shadow(4.dp, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Fastest Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────────── Level 1: Continent Card ─────────────────────────────

@Composable
private fun ContinentAccordionCard(
    continentName: String,
    emoji: String,
    countryMap: Map<String, List<PublicServer>>,
    totalServers: Int,
    selectedId: String?,
    userTier: UserTier,
    isExpanded: Boolean,
    expandedCountries: Set<String>,
    onToggleExpand: () -> Unit,
    onToggleCountryExpand: (String) -> Unit,
    onSelectServer: (PublicServer) -> Unit,
    onLockedClick: (PublicServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val chevron by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevronCont")

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(scheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 26.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = continentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${countryMap.size} Countries · $totalServers Servers",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp).rotate(chevron),
                )
            }

            // Expanded Level 2: Countries
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.3f))

                    countryMap.keys.forEach { cName ->
                        val cServers = countryMap[cName] ?: emptyList()
                        val isCountryExpanded = cName in expandedCountries

                        CountryAccordionCard(
                            countryName = cName,
                            flag = cServers.firstOrNull()?.flag ?: "🌐",
                            servers = cServers,
                            selectedId = selectedId,
                            userTier = userTier,
                            isExpanded = isCountryExpanded,
                            onToggleExpand = { onToggleCountryExpand(cName) },
                            onSelectServer = onSelectServer,
                            onLockedClick = onLockedClick,
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Level 2: Country Card ─────────────────────────────

@Composable
private fun CountryAccordionCard(
    countryName: String,
    flag: String,
    servers: List<PublicServer>,
    selectedId: String?,
    userTier: UserTier,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectServer: (PublicServer) -> Unit,
    onLockedClick: (PublicServer) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bestPing = remember(servers) { servers.mapNotNull { it.latencyMs }.filter { it > 0 }.minOrNull() }
    val containsSelected = remember(servers, selectedId) { servers.any { it.id == selectedId } }
    val chevron by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevronCountry")

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(if (containsSelected) 2.dp else 1.dp, if (containsSelected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = flag.ifBlank { "🌐" }, fontSize = 28.sp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = countryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "${servers.size} Locations Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                if (bestPing != null) {
                    val pColor = pingQualityColor(bestPing, scheme)
                    Surface(
                        shape = CircleShape,
                        color = pColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "⚡ $bestPing ms",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = pColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(chevron),
                )
            }

            // Expanded Level 3: Individual Servers
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    servers.forEach { server ->
                        ServerRowItem(
                            server = server,
                            isSelected = server.id == selectedId,
                            userTier = userTier,
                            onClick = { onSelectServer(server) },
                            onLockedClick = { onLockedClick(server) },
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Level 3: Individual Server Item ─────────────────────────────

@Composable
private fun ServerRowItem(
    server: PublicServer,
    isSelected: Boolean,
    userTier: UserTier,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val locked = !UserTier.canAccess(userTier, server.tier.id)
    val name = remember(server.displayName) { cleanName(server.displayName) }
    val ping = server.latencyMs?.takeIf { it > 0 }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (locked) onLockedClick() else onClick() },
        color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (isSelected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                enabled = !locked,
                colors = RadioButtonDefaults.colors(selectedColor = scheme.primary)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (server.protocolLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = scheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = server.protocolLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (ping != null) {
                val qColor = pingQualityColor(ping, scheme)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(qColor, CircleShape))
                    Text(
                        text = "$ping ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = qColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (locked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = "Locked",
                    tint = scheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ───────────────────────────── Tier Selector ─────────────────────────────

@Composable
private fun TierSelector(
    active: ServerTier,
    userTier: UserTier,
    counts: Map<ServerTier, Int>,
    onSelect: (ServerTier) -> Unit,
    onLocked: (ServerTier) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ServerTier.entries.forEach { tier ->
                val selected = active == tier
                val locked = !UserTier.canAccess(userTier, tier.id)
                Surface(
                    onClick = { if (locked) onLocked(tier) else onSelect(tier) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tierLabel(tier),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        if (locked) {
                            Icon(painterResource(Res.drawable.ic_lock), null, modifier = Modifier.size(14.dp))
                        } else {
                            Text(
                                "${counts[tier] ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.alpha(0.85f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Filter BottomSheet ─────────────────────────────

@Composable
private fun FilterBottomSheet(
    selectedProtocol: ProtocolFilter,
    onProtocolSelect: (ProtocolFilter) -> Unit,
    selectedContinent: String,
    onContinentSelect: (String) -> Unit,
    currentSort: SortOption,
    onSortSelect: (SortOption) -> Unit,
    protoCounts: Map<ProtocolFilter, Int>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Filter VPN Protocols & Regions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("Select VPN Protocol", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            
            // Protocol Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtocolFilter.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { proto ->
                            val count = protoCounts[proto] ?: 0
                            val isSelected = selectedProtocol == proto
                            Surface(
                                onClick = { onProtocolSelect(proto); onDismiss() },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        proto.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (proto != ProtocolFilter.All) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "$count",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text("Select Region", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Asia", "Europe", "North America", "Middle East", "South America", "Africa", "Oceania").forEach { cont ->
                    FilterChip(
                        selected = selectedContinent == cont,
                        onClick = { onContinentSelect(cont) },
                        label = { Text(cont) }
                    )
                }
            }

            Text("Sort Order", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortOption.entries.forEach { sort ->
                    FilterChip(
                        selected = currentSort == sort,
                        onClick = { onSortSelect(sort) },
                        label = { Text(sort.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Apply Filters", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ───────────────────────────── Empty Results ─────────────────────────────

@Composable
private fun EmptyResults(message: String, showReset: Boolean, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showReset) {
            FilledTonalButton(onClick = onReset, shape = RoundedCornerShape(18.dp)) {
                Text("Reset filters", fontWeight = FontWeight.Bold)
            }
        }
    }
}
