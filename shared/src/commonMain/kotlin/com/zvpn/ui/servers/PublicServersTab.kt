@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zvpn.domain.billing.UserTier
import com.zvpn.shared.resources.*
import org.jetbrains.compose.resources.painterResource

/*
 * World-Class 3-Level Accordion Server Architecture (Material 3 Theme-Adaptive)
 *  - Level 1: Continents (Expandable)
 *  - Level 2: Countries (Expandable)
 *  - Level 3: Servers
 *  - 100% Material 3 Theme Color Compliance (Light & Dark Mode)
 */

private val BRACKET_TAG = Regex("""\s*\[.*?]""")

private fun cleanName(raw: String): String = raw.replace(BRACKET_TAG, "").trim()

private fun tierLabel(tier: ServerTier): String = when (tier) {
    ServerTier.Free -> "Free"
    ServerTier.Premium -> "Pro"
    ServerTier.PremiumPlus -> "VIP"
}

private fun continentEmoji(cont: String): String = when (cont.lowercase()) {
    "asia" -> "🌏"
    "europe" -> "🇪🇺"
    "north america" -> "🌎"
    "middle east" -> "🇦🇪"
    "south america" -> "🌎"
    "africa" -> "🌍"
    "oceania" -> "🌏"
    else -> "🌍"
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

    val filteredServers = remember(servers, activeTierTab, selectedProtocol, selectedContinent, searchQuery, currentSort) {
        val base = servers
            .filter { it.tier == activeTierTab }
            .filter { selectedProtocol == ProtocolFilter.All || it.protocolLabel.equals(selectedProtocol.label, ignoreCase = true) }
            .filter { selectedContinent == "All" || it.continent.equals(selectedContinent, ignoreCase = true) }
            .filter { searchQuery.isBlank() || it.countryName.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) }

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
            onDismiss = { showFilterSheet = false }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Auto-Select Banner
        if (bestAutoServer != null) {
            item(key = "auto-banner") {
                AutoSelectBanner(server = bestAutoServer, onClick = { onSelect(bestAutoServer) })
            }
        }

        // 2. Search & Filter Bar
        item(key = "search") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country or server...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    leadingIcon = {
                        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = null)
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (hasActiveFilters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
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

        // 3. Tier Tabs (Pinned Sticky Header)
        stickyHeader(key = "tiers") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 4.dp),
            ) {
                TierSelector(
                    active = activeTierTab,
                    userTier = userTier,
                    counts = tierCounts,
                    onSelect = { activeTierTab = it; selectedProtocol = ProtocolFilter.All },
                    onLocked = { tier -> servers.firstOrNull { it.tier == tier }?.let(onLockedClick) },
                )
            }
        }

        // 4. Quick Protocol Chips
        item(key = "protocols") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProtocolFilter.entries.take(7).forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = { selectedProtocol = proto },
                        label = { Text(proto.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // 5. Summary Row
        if (continentGroups.isNotEmpty()) {
            item(key = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${continentGroups.size} Continents · ${filteredServers.size} Servers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // 6. Level 1: Continent Accordion Cards
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

        // 7. Empty Results
        if (continentGroups.isEmpty()) {
            item(key = "empty") {
                EmptyResults(
                    message = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\""
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

// ───────────────────────────── AutoSelect Banner ─────────────────────────────

@Composable
private fun AutoSelectBanner(server: PublicServer, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val ping = server.latencyMs?.takeIf { it > 0 }
    val subtitle = if (ping != null) "${cleanName(server.displayName)} · $ping ms" else cleanName(server.displayName)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = shape,
        color = scheme.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_bolt),
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Fastest Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Connect", fontWeight = FontWeight.Bold)
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
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(scheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 24.sp)
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
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))

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
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainer,
        border = if (containsSelected) BorderStroke(1.5.dp, scheme.primary) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = flag.ifBlank { "🌐" }, fontSize = 22.sp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = countryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "${servers.size} locations",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                if (bestPing != null) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.secondaryContainer,
                    ) {
                        Text(
                            text = "$bestPing ms",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSecondaryContainer,
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

            // Expanded Level 3: Servers
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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

// ───────────────────────────── Level 3: Server Row Item ─────────────────────────────

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
            .clip(RoundedCornerShape(14.dp))
            .clickable { if (locked) onLockedClick() else onClick() },
        color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                enabled = !locked,
                colors = RadioButtonDefaults.colors(selectedColor = scheme.primary)
            )

            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (ping != null) {
                Text(
                    text = "$ping ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (locked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = "Locked",
                    tint = scheme.outline,
                    modifier = Modifier.size(16.dp),
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).height(44.dp),
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
                                modifier = Modifier.alpha(0.8f),
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Server Filters & Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("Select Protocol", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProtocolFilter.entries.forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = { onProtocolSelect(proto) },
                        label = { Text(proto.label) }
                    )
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Apply & Close")
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
            FilledTonalButton(onClick = onReset, shape = RoundedCornerShape(16.dp)) {
                Text("Reset filters")
            }
        }
    }
}
