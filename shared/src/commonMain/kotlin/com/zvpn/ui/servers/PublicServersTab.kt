@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
 * Layout notes
 *  - The whole screen is ONE LazyColumn: banner, search and filters scroll away,
 *    only the tier selector stays pinned. The server list gets the full screen height.
 *  - 8-pt grid, theme colours only (no hard-coded palette), soft primary-tinted shadow
 *    on the single hero element (the banner).
 */

private val BRACKET_TAG = Regex("""\s*\[.*?]""")

private fun cleanName(raw: String): String = raw.replace(BRACKET_TAG, "").trim()

private fun tierLabel(tier: ServerTier): String = when (tier) {
    ServerTier.Free -> "Free"
    ServerTier.Premium -> "Pro"
    ServerTier.PremiumPlus -> "VIP"
}

private fun continentLabel(cont: String): String = when (cont) {
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

    // Open the tier + country of the currently selected server on first show.
    LaunchedEffect(Unit) {
        val current = servers.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        if (UserTier.canAccess(userTier, current.tier.id)) activeTierTab = current.tier
        expandedCountries = expandedCountries + current.countryName.ifBlank { "Global" }
    }

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

    val tierCounts = remember(servers) { servers.groupingBy { it.tier }.eachCount() }

    val filteredServers = remember(servers, activeTierTab, selectedProtocol, selectedContinent, searchQuery, currentSort) {
        val base = servers
            .filter { it.tier == activeTierTab }
            .filter { selectedProtocol == ProtocolFilter.All || it.protocolLabel.equals(selectedProtocol.label, ignoreCase = true) }
            .filter { selectedContinent == "All" || it.continent.equals(selectedContinent, ignoreCase = true) }
            .filter { searchQuery.isBlank() || it.countryName.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) }

        // Servers without a valid ping always go last when sorting by ping.
        when (currentSort) {
            SortOption.Speed -> base.sortedByDescending { it.speedMbps }
            SortOption.Ping -> base.sortedBy { it.latencyMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE }
            SortOption.Name -> base.sortedBy { it.countryName }
            SortOption.Favorites, SortOption.Recommended -> base.sortedWith(
                compareBy<PublicServer> { it.countryName }.thenBy { it.latencyMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE },
            )
        }
    }

    // Group servers by Country Name
    val countryGroups = remember(filteredServers) {
        filteredServers.groupBy { it.countryName.ifBlank { "Global" } }
    }
    val countryNames = remember(countryGroups) { countryGroups.keys.toList() }

    // Fastest *measured* server the user can access; falls back to the first accessible one.
    val bestAutoServer = remember(servers, userTier) {
        val accessible = servers.filter { UserTier.canAccess(userTier, it.tier.id) }
        accessible.filter { (it.latencyMs ?: 0) > 0 }.minByOrNull { it.latencyMs ?: Int.MAX_VALUE }
            ?: accessible.firstOrNull()
    }

    val hasActiveFilters = searchQuery.isNotBlank() || selectedProtocol != ProtocolFilter.All || selectedContinent != "All"
    val allExpanded = countryNames.isNotEmpty() && countryNames.all { it in expandedCountries }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Hero: auto-select fastest
        if (bestAutoServer != null) {
            item(key = "auto-banner") {
                AutoSelectBanner(server = bestAutoServer, onClick = { onSelect(bestAutoServer) })
            }
        }

        // 2. Search + sort
        item(key = "search") {
            SearchSortRow(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                currentSort = currentSort,
                onSortChange = { currentSort = it },
            )
        }

        // 3. Tier selector (pinned)
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

        // 4. Filters (only when there is something to filter)
        if (continents.size > 2 || protocols.size > 2) {
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (continents.size > 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            continents.forEach { cont ->
                                FilterChip(
                                    selected = selectedContinent == cont,
                                    onClick = { selectedContinent = cont },
                                    label = { Text(continentLabel(cont)) },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                    if (protocols.size > 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            protocols.forEach { proto ->
                                FilterChip(
                                    selected = selectedProtocol == proto,
                                    onClick = { selectedProtocol = proto },
                                    label = { Text(proto.label) },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Summary + expand/collapse all
        if (countryNames.isNotEmpty()) {
            item(key = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${countryNames.size} countries · ${filteredServers.size} servers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = {
                            expandedCountries = if (allExpanded) emptySet() else countryNames.toSet()
                        },
                    ) {
                        Text(
                            if (allExpanded) "Collapse all" else "Expand all",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // 6. Country accordion cards
        items(items = countryNames, key = { it }) { countryName ->
            val countryServers = countryGroups[countryName].orEmpty()
            val firstServer = countryServers.firstOrNull()
            val isExpanded = countryName in expandedCountries

            CountryAccordionCard(
                modifier = Modifier.animateItem(),
                countryName = countryName,
                flag = firstServer?.flag ?: "🌐",
                continent = firstServer?.continent ?: "Global",
                servers = countryServers,
                selectedId = selectedId,
                userTier = userTier,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedCountries = if (isExpanded) expandedCountries - countryName else expandedCountries + countryName
                },
                onSelectServer = onSelect,
                onLockedClick = onLockedClick,
            )
        }

        // 7. Empty result
        if (countryNames.isEmpty()) {
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

// ───────────────────────────── Banner ─────────────────────────────

@Composable
private fun AutoSelectBanner(server: PublicServer, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val brush = remember(scheme.primary, scheme.tertiary) {
        Brush.linearGradient(listOf(scheme.primary, lerp(scheme.primary, scheme.tertiary, 0.35f)))
    }
    val ping = server.latencyMs?.takeIf { it > 0 }
    val subtitle = if (ping != null) "${cleanName(server.displayName)} · $ping ms" else cleanName(server.displayName)

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = scheme.primary.copy(alpha = 0.20f),
                spotColor = scheme.primary.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(scheme.onPrimary.copy(alpha = 0.18f), CircleShape),
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
                    "Fastest server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onPrimary, contentColor = scheme.primary),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ───────────────────────────── Search + sort ─────────────────────────────

@Composable
private fun SearchSortRow(
    query: String,
    onQueryChange: (String) -> Unit,
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search country, location...") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = "Clear")
                    }
                }
            },
        )

        var sortExpanded by remember { mutableStateOf(false) }
        Box {
            FilledTonalIconButton(
                onClick = { sortExpanded = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_tune),
                    contentDescription = "Sort: ${currentSort.label}",
                )
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
                shape = MaterialTheme.shapes.largeIncreased,
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontWeight = if (option == currentSort) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingIcon = {
                            if (option == currentSort) {
                                Icon(
                                    painterResource(Res.drawable.ic_check_circle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = {
                            onSortChange(option)
                            sortExpanded = false
                        },
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Tier selector ─────────────────────────────

/** Segmented control: Free / Pro / VIP, with server counts and a lock on tiers the user can't access. */
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ServerTier.entries.forEach { tier ->
                val selected = active == tier
                val locked = !UserTier.canAccess(userTier, tier.id)
                val container by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    label = "tierContainer",
                )
                val content by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tierContent",
                )
                Surface(
                    onClick = { if (locked) onLocked(tier) else onSelect(tier) },
                    shape = RoundedCornerShape(16.dp),
                    color = container,
                    contentColor = content,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tierLabel(tier),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (locked) {
                            Icon(painterResource(Res.drawable.ic_lock), null, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                "${counts[tier] ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.alpha(0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Country card ─────────────────────────────

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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bestPing = remember(servers) { servers.mapNotNull { it.latencyMs }.filter { it > 0 }.minOrNull() }
    val bestPingColor = pingColor(bestPing)
    val containsSelected = remember(servers, selectedId) { servers.any { it.id == selectedId } }
    val chevron by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        border = if (containsSelected) BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.5f)) else null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(scheme.primary.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = flag.ifBlank { "🌐" }, fontSize = 24.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = countryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (containsSelected) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(8.dp).background(scheme.primary, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${servers.size} locations · $continent",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (bestPing != null) {
                    Surface(
                        shape = CircleShape,
                        color = bestPingColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "$bestPing ms",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = bestPingColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(chevron),
                )
            }

            AnimatedVisibility(visible = isExpanded) {
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

// ───────────────────────────── Server row ─────────────────────────────

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
    val textColor = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface
    val subtleColor = if (isSelected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.6f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (locked) onLockedClick() else onClick() },
        color = if (isSelected) scheme.primaryContainer else scheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Signal dot: instantly shows quality before the user reads the number.
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (ping != null) pingColor(ping) else scheme.outlineVariant, CircleShape),
            )

            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (server.protocolLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = textColor.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = server.protocolLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtleColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }

            if (ping != null) {
                Text(
                    text = "$ping ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = subtleColor,
                )
            }

            when {
                locked -> Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = "Locked",
                    tint = scheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                isSelected -> Icon(
                    painter = painterResource(Res.drawable.ic_check_circle),
                    contentDescription = "Selected",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ───────────────────────────── Empty result ─────────────────────────────

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