@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zvpn.data.net.PingCoordinator
import com.zvpn.domain.billing.UserTier
import com.zvpn.shared.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/*
 * Fast server list.
 *
 *  - ONE flat LazyColumn. Each continent is drawn as a single "card" by giving its rows matching corner
 *    shapes (top / middle / bottom) instead of nesting Cards, so nothing is composed off-screen.
 *  - Filtering, grouping, counting and sorting run on Dispatchers.Default.
 *  - Random servers (null/empty/XX countryCode) are numbered on full list and grouped at the very end.
 */

// ───────────────────────────── Helpers ─────────────────────────────

private fun tierLabel(tier: ServerTier): String = when (tier) {
    ServerTier.Free -> "Free"
    ServerTier.Premium -> "Premium"
    ServerTier.PremiumPlus -> "Premium+"
}

private fun continentEmoji(cont: String): String = when (cont.lowercase().trim()) {
    "asia", "asia-pacific", "oceania" -> "🌏"
    "europe" -> "🇪🇺"
    "americas", "north america", "south america" -> "🌎"
    "middle east", "me/africa", "africa" -> "🌍"
    else -> "🌐"
}

private fun pingQualityColor(ping: Int?, scheme: ColorScheme): Color = when {
    ping == null || ping <= 0 -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
    ping < 60 -> Color(0xFF10B981)
    ping < 150 -> Color(0xFFF59E0B)
    else -> scheme.outline
}

private fun countryKey(continent: String, country: String) = "$continent/$country"

private val REGION_FILTERS = listOf("All", "Americas", "Europe", "Asia-Pacific", "ME/Africa")

// Corner shapes are constants: no allocation while scrolling.
private val CardRadius = 20.dp
private val ShapeSingle = RoundedCornerShape(CardRadius)
private val ShapeTop = RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
private val ShapeBottom = RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)
private val ShapeMiddle = RoundedCornerShape(0.dp)
private val ShapeInner = RoundedCornerShape(12.dp)

private fun cardShape(top: Boolean, bottom: Boolean): RoundedCornerShape = when {
    top && bottom -> ShapeSingle
    top -> ShapeTop
    bottom -> ShapeBottom
    else -> ShapeMiddle
}

// ───────────────────────────── List model (built off the main thread) ─────────────────────────────

@Immutable
data class CountryGroup(
    val name: String,
    val flag: String,
    val servers: List<PublicServer>,
    val bestPing: Int?,
)

@Immutable
internal class ContinentGroup(
    val name: String,
    val countries: List<CountryGroup>,
    val serverCount: Int,
)

@Immutable
private class ListModel(
    val groups: List<ContinentGroup>,
    val tierCounts: Map<ServerTier, Int>,
    val protoCounts: Map<ProtocolFilter, Int>,
    val bestAuto: PublicServer?,
    val byId: Map<String, PublicServer>,
)

object RandomServerGrouping {
    val isRandomServer: (PublicServer) -> Boolean = { s ->
        !s.isSystem && (s.countryCode.isBlank() || s.countryCode == "XX")
    }

    fun computeRandomDisplayNameMap(servers: List<PublicServer>): Map<String, String> {
        val fullRandomServers = servers.filter(isRandomServer).sortedBy { it.id }
        return fullRandomServers.mapIndexed { idx, s ->
            s.id to "Random ${idx + 1}"
        }.toMap()
    }

    fun buildRandomCountryGroups(
        randomBase: List<PublicServer>,
        displayNameMap: Map<String, String>
    ): List<CountryGroup> {
        if (randomBase.isEmpty()) return emptyList()
        val totalRandom = randomBase.size
        return if (totalRandom <= 100) {
            val serversWithNumberedNames = randomBase.map { server ->
                server.copy(displayName = displayNameMap[server.id] ?: server.displayName)
            }
            listOf(
                CountryGroup(
                    name = "Random",
                    flag = "🌐",
                    servers = serversWithNumberedNames,
                    bestPing = serversWithNumberedNames.mapNotNull { it.latencyMs }.filter { it > 0 }.minOrNull(),
                )
            )
        } else {
            randomBase.chunked(100).mapIndexed { chunkIdx, chunk ->
                val startNum = chunkIdx * 100 + 1
                val endNum = startNum + chunk.size - 1
                val groupTitle = "Random $startNum-$endNum"
                val serversWithNumberedNames = chunk.map { server ->
                    server.copy(displayName = displayNameMap[server.id] ?: server.displayName)
                }
                CountryGroup(
                    name = groupTitle,
                    flag = "🌐",
                    servers = serversWithNumberedNames,
                    bestPing = serversWithNumberedNames.mapNotNull { it.latencyMs }.filter { it > 0 }.minOrNull(),
                )
            }
        }
    }
}

private sealed class Entry(val key: String, val type: Int) {
    /** True for the last row of a continent card (gets the rounded bottom corners). */
    var last: Boolean = false
}

private class ContinentEntry(val group: ContinentGroup, val expanded: Boolean) : Entry("c:${group.name}", 0)
private class CountryEntry(val ckey: String, val group: CountryGroup, val expanded: Boolean) : Entry("k:$ckey", 1)
private class ServerEntry(val server: PublicServer, key: String) : Entry(key, 2)

private fun matchesProtocol(s: PublicServer, p: ProtocolFilter): Boolean {
    if (p == ProtocolFilter.All) return true
    val sProto = s.protocolLabel.trim().lowercase()
    val pId = p.id.lowercase()
    val pLabel = p.label.trim().lowercase()

    return when (p) {
        ProtocolFilter.Hysteria2 -> sProto.contains("hysteria") || sProto.contains("hy2")
        ProtocolFilter.Shadowsocks -> sProto == "shadowsocks" || sProto == "ss" || sProto.contains("shadow")
        ProtocolFilter.WireGuard -> sProto == "wireguard" || sProto == "wg" || sProto.contains("amnezia")
        ProtocolFilter.SOCKS5 -> sProto.contains("socks")
        ProtocolFilter.HTTP -> sProto.contains("http")
        ProtocolFilter.VLESS -> sProto.contains("vless")
        ProtocolFilter.VMess -> sProto.contains("vmess")
        ProtocolFilter.DNSTunnel -> sProto.contains("dns") || sProto.contains("master")
        ProtocolFilter.SNISpoof -> sProto.contains("sni") || sProto.contains("spoof")
        ProtocolFilter.Chain -> sProto.contains("chain")
        ProtocolFilter.CrossChain -> sProto.contains("cross")
        ProtocolFilter.SingBox -> sProto.contains("sing")
        else -> sProto == pId || sProto == pLabel || sProto.contains(pId)
    }
}

private fun buildModel(
    servers: List<PublicServer>,
    userTier: UserTier,
    tier: ServerTier,
    protocol: ProtocolFilter,
    continentFilter: String,
    query: String,
    sort: SortOption,
): ListModel {
    val tierCounts = servers.groupingBy { it.tier }.eachCount()
    val inTier = servers.filter { it.tier == tier }
    val protoCounts = inTier.groupingBy { ProtocolFilter.fromId(it.protocolLabel) }.eachCount()

    val randomDisplayNameMap = RandomServerGrouping.computeRandomDisplayNameMap(servers)

    val q = query.trim()
    val base = inTier.filter { s ->
        (protocol == ProtocolFilter.All || matchesProtocol(s, protocol)) &&
            (continentFilter == "All" || s.continent.equals(continentFilter, ignoreCase = true)) &&
            (q.isEmpty() ||
                s.countryName.contains(q, ignoreCase = true) ||
                s.displayName.contains(q, ignoreCase = true) ||
                s.protocolLabel.contains(q, ignoreCase = true))
    }

    val regularBase = base.filterNot(RandomServerGrouping.isRandomServer)
    val randomBase = base.filter(RandomServerGrouping.isRandomServer).sortedBy { it.id }

    val sortedRegular = when (sort) {
        SortOption.Speed -> regularBase.sortedByDescending { it.speedMbps }
        SortOption.Ping -> regularBase.sortedBy { it.latencyMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE }
        SortOption.Name -> regularBase.sortedBy { it.countryName }
        SortOption.Favorites, SortOption.Recommended -> regularBase.sortedWith(
            compareBy<PublicServer> { it.countryName }
                .thenBy { it.latencyMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE },
        )
    }

    // Grouping by countryCode as requested
    val regularGroups = sortedRegular.groupBy { it.continent.ifBlank { "Other" } }.map { (cont, list) ->
        val countries = list.groupBy { it.countryCode.ifBlank { "XX" } }.map { (_, cl) ->
            val first = cl.first()
            CountryGroup(
                name = first.countryName.ifBlank { "Unknown" },
                flag = first.flag.ifBlank { "🌐" },
                servers = cl,
                bestPing = cl.mapNotNull { it.latencyMs }.filter { it > 0 }.minOrNull(),
            )
        }
        ContinentGroup(cont, countries, list.size)
    }

    // Construct Random Section at the very end calling shared RandomServerGrouping
    val randomCountryGroups = RandomServerGrouping.buildRandomCountryGroups(randomBase, randomDisplayNameMap)
    val randomContinentGroup = if (randomCountryGroups.isNotEmpty()) {
        ContinentGroup("Random", randomCountryGroups, randomBase.size)
    } else null

    val groups = if (randomContinentGroup != null) {
        regularGroups + randomContinentGroup
    } else {
        regularGroups
    }

    var best: PublicServer? = null
    var firstAccessible: PublicServer? = null
    for (s in servers) {
        if (!UserTier.canAccess(userTier, s.tier.id)) continue
        if (firstAccessible == null) firstAccessible = s
        val ms = s.latencyMs ?: 0
        if (ms > 0 && (best == null || ms < (best.latencyMs ?: Int.MAX_VALUE))) best = s
    }

    return ListModel(groups, tierCounts, protoCounts, best ?: firstAccessible, servers.associateBy { it.id })
}

private fun flatten(
    groups: List<ContinentGroup>?,
    expandedContinents: Set<String>,
    expandedCountries: Set<String>,
    forceExpand: Boolean,
    singleContinent: Boolean,
): List<Entry> {
    if (groups == null) return emptyList()
    val out = ArrayList<Entry>(groups.size * 4)
    val seen = HashSet<String>()
    for (cont in groups) {
        val contOpen = forceExpand || singleContinent || cont.name in expandedContinents
        out += ContinentEntry(cont, contOpen)
        if (contOpen) {
            for (country in cont.countries) {
                val ck = countryKey(cont.name, country.name)
                val countryOpen = forceExpand || ck in expandedCountries
                out += CountryEntry(ck, country, countryOpen)
                if (!countryOpen) continue
                for (s in country.servers) {
                    val key = if (seen.add(s.id)) "s:${s.id}" else "s:${s.id}#${out.size}"
                    out += ServerEntry(s, key)
                }
            }
        }
        out[out.size - 1].last = true
    }
    return out
}

// ───────────────────────────── Ping helpers ─────────────────────────────

@Composable
internal fun PingProgressHost() {
    val p by PingCoordinator.progress.collectAsStateWithLifecycle()
    p?.let { PingProgressCard(done = it.done, total = it.total, real = it.real) }
}

@Composable
internal fun rememberIsTesting(): Boolean {
    val initial = remember { PingCoordinator.progress.value != null }
    val flow = remember { PingCoordinator.progress.map { it != null }.distinctUntilChanged() }
    val testing by flow.collectAsStateWithLifecycle(initialValue = initial)
    return testing
}

// ───────────────────────────── Screen ─────────────────────────────

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
    var debouncedQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.Recommended) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var expandedContinents by remember { mutableStateOf(setOf<String>()) }
    var expandedCountries by remember { mutableStateOf(setOf<String>()) }

    val latestServers by rememberUpdatedState(servers)
    var stableServers by remember { mutableStateOf(servers) }
    LaunchedEffect(Unit) {
        snapshotFlow { latestServers }.collect {
            stableServers = it
            if (it.isNotEmpty()) delay(250)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) delay(250)
        debouncedQuery = searchQuery
    }

    LaunchedEffect(servers.isNotEmpty()) {
        val current = servers.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        if (UserTier.canAccess(userTier, current.tier.id)) activeTierTab = current.tier
        val cont = current.continent.ifBlank { "Global" }
        expandedContinents = expandedContinents + cont
        expandedCountries = expandedCountries + countryKey(cont, current.countryName.ifBlank { "Global" })
    }

    val model by produceState<ListModel?>(
        null, stableServers, userTier, activeTierTab, selectedProtocol, selectedContinent, debouncedQuery, currentSort,
    ) {
        value = withContext(Dispatchers.Default) {
            buildModel(stableServers, userTier, activeTierTab, selectedProtocol, selectedContinent, debouncedQuery, currentSort)
        }
    }

    val groups = model?.groups
    val tierCounts = model?.tierCounts ?: emptyMap()
    val protoCounts = model?.protoCounts ?: emptyMap()
    val bestAutoServer = model?.bestAuto

    val visibleProtocols = remember(protoCounts, selectedProtocol) {
        ProtocolFilter.entries.filter {
            it == ProtocolFilter.All || it == selectedProtocol || (protoCounts[it] ?: 0) > 0
        }
    }

    val forceExpand = debouncedQuery.isNotBlank()
    val entries = remember(groups, expandedContinents, expandedCountries, forceExpand, selectedContinent) {
        flatten(groups, expandedContinents, expandedCountries, forceExpand, selectedContinent != "All")
    }

    val selectedCountryKey = remember(model, selectedId) {
        selectedId?.let { model?.byId?.get(it) }?.let {
            countryKey(it.continent.ifBlank { "Global" }, it.countryName.ifBlank { "Global" })
        }
    }

    val hasActiveFilters = searchQuery.isNotBlank() || selectedProtocol != ProtocolFilter.All || selectedContinent != "All"

    if (showFilterSheet) {
        FilterBottomSheet(
            selectedProtocol = selectedProtocol,
            onProtocolSelect = { selectedProtocol = it },
            selectedContinent = selectedContinent,
            onContinentSelect = { selectedContinent = it },
            currentSort = currentSort,
            onSortSelect = { currentSort = it },
            protoCounts = protoCounts,
            onDismiss = { showFilterSheet = false },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchRow(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            hasActiveFilters = hasActiveFilters,
            onFilterClick = { showFilterSheet = true },
        )

        TierTabs(
            active = activeTierTab,
            userTier = userTier,
            counts = tierCounts,
            onSelect = { activeTierTab = it; selectedProtocol = ProtocolFilter.All },
            onLocked = { tier -> servers.firstOrNull { it.tier == tier }?.let(onLockedClick) },
        )

        LazyRow(
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleProtocols, key = { it.name }) { proto ->
                val count = if (proto == ProtocolFilter.All) protoCounts.values.sum() else (protoCounts[proto] ?: 0)
                ProtocolChip(
                    label = if (proto == ProtocolFilter.All) "All ($count)" else "${proto.label} ($count)",
                    selected = selectedProtocol == proto,
                    onClick = { selectedProtocol = proto },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        ) {
            if (bestAutoServer != null) {
                item(key = "auto-banner", contentType = "banner") {
                    AutoSelectBanner(server = bestAutoServer, onClick = { onSelect(bestAutoServer) })
                }
            }

            if (groups != null && groups.isNotEmpty()) {
                item(key = "summary", contentType = "summary") {
                    Text(
                        text = "${groups.size} regions · ${groups.sumOf { it.serverCount }} servers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }

            items(entries, key = { it.key }, contentType = { it.type }) { entry ->
                when (entry) {
                    is ContinentEntry -> ContinentHeader(
                        name = entry.group.name,
                        emoji = continentEmoji(entry.group.name),
                        countries = entry.group.countries.size,
                        servers = entry.group.serverCount,
                        expanded = entry.expanded,
                        shape = cardShape(top = true, bottom = !entry.expanded),
                        onClick = {
                            val n = entry.group.name
                            expandedContinents = if (n in expandedContinents) expandedContinents - n else expandedContinents + n
                        },
                    )

                    is CountryEntry -> CountryRow(
                        name = entry.group.name,
                        flag = entry.group.flag,
                        serverCount = entry.group.servers.size,
                        bestPing = entry.group.bestPing,
                        expanded = entry.expanded,
                        containsSelected = entry.ckey == selectedCountryKey,
                        shape = cardShape(top = false, bottom = entry.last),
                        onClick = {
                            val k = entry.ckey
                            expandedCountries = if (k in expandedCountries) expandedCountries - k else expandedCountries + k
                        },
                    )

                    is ServerEntry -> {
                        val s = entry.server
                        val locked = !UserTier.canAccess(userTier, s.tier.id)
                        ServerRow(
                            server = s,
                            selected = s.id == selectedId,
                            locked = locked,
                            last = entry.last,
                            onClick = { if (locked) onLockedClick(s) else onSelect(s) },
                        )
                    }
                }
            }

            if (groups != null && groups.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyResults(
                        message = if (debouncedQuery.isNotEmpty()) "No servers found for \"$debouncedQuery\""
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
}

// ───────────────────────────── Controls ─────────────────────────────

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    hasActiveFilters: Boolean,
    onFilterClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search country or server", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainerHigh,
                unfocusedContainerColor = scheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            leadingIcon = {
                Icon(painterResource(Res.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
        )

        IconButton(
            onClick = onFilterClick,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (hasActiveFilters) scheme.primaryContainer else scheme.surfaceContainer),
        ) {
            Icon(
                painterResource(Res.drawable.ic_tune),
                contentDescription = "Filters",
                tint = if (hasActiveFilters) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierTabs(
    active: ServerTier,
    userTier: UserTier,
    counts: Map<ServerTier, Int>,
    onSelect: (ServerTier) -> Unit,
    onLocked: (ServerTier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ServerTier.entries.forEach { tier ->
            val selected = active == tier
            val locked = !UserTier.canAccess(userTier, tier.id)
            val content = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) scheme.primary else Color.Transparent)
                    .clickable { if (locked) onLocked(tier) else onSelect(tier) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tierLabel(tier), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content)
                    Text("${counts[tier] ?: 0}", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun ProtocolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ───────────────────────────── Rows ─────────────────────────────

@Composable
private fun AutoSelectBanner(server: PublicServer, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ping = server.latencyMs?.takeIf { it > 0 }
    val name = server.displayName
    val subtitle = if (ping != null) "$name · $ping ms" else name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(Res.drawable.ic_bolt), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Fastest server", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Connect", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
        }
    }
}

@Composable
private fun ContinentHeader(
    name: String,
    emoji: String,
    countries: Int,
    servers: Int,
    expanded: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text(
                "$countries countries · $servers servers",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(Res.drawable.ic_keyboard_arrow_down),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(22.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun CountryRow(
    name: String,
    flag: String,
    serverCount: Int,
    bestPing: Int?,
    expanded: Boolean,
    containsSelected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(flag, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (containsSelected) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (serverCount == 1) "1 server" else "$serverCount servers",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (bestPing != null) {
            val c = pingQualityColor(bestPing, scheme)
            Box(modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("$bestPing ms", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = c)
            }
        }
        Icon(
            painterResource(Res.drawable.ic_keyboard_arrow_down),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun ServerRow(
    server: PublicServer,
    selected: Boolean,
    locked: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val name = server.displayName
    val ping = server.latencyMs?.takeIf { it > 0 }
    val pColor = pingQualityColor(ping, scheme)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape(top = false, bottom = last))
            .background(scheme.surfaceContainer)
            .padding(start = 52.dp, end = 8.dp, top = 1.dp, bottom = if (last) 8.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeInner)
                .then(if (selected) Modifier.background(scheme.primaryContainer) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(pColor, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = (if (selected) scheme.onPrimaryContainer else scheme.onSurface).copy(alpha = if (locked) 0.55f else 1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (server.protocolLabel.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = server.protocolLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (ping != null) {
                Text(
                    "$ping ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = pColor,
                )
            }
            if (selected) {
                Icon(painterResource(Res.drawable.ic_check_circle), "Selected", tint = scheme.primary, modifier = Modifier.size(18.dp))
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
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("Region", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                REGION_FILTERS.forEach { cont ->
                    FilterChip(
                        selected = selectedContinent == cont,
                        onClick = { onContinentSelect(cont) },
                        label = { Text(cont) },
                    )
                }
            }

            Text("Protocol", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtocolFilter.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { proto ->
                            val isSelected = selectedProtocol == proto
                            val scheme = MaterialTheme.colorScheme
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) scheme.primaryContainer else scheme.surfaceContainerHigh)
                                    .clickable { onProtocolSelect(proto) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    proto.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                                )
                                if (proto != ProtocolFilter.All) {
                                    Text(
                                        "${protoCounts[proto] ?: 0}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.primary,
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text("Sort", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortOption.entries.forEach { sort ->
                    FilterChip(
                        selected = currentSort == sort,
                        onClick = { onSortSelect(sort) },
                        label = { Text(sort.label) },
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
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
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showReset) {
            FilledTonalButton(onClick = onReset, shape = RoundedCornerShape(16.dp)) {
                Text("Reset filters", fontWeight = FontWeight.Bold)
            }
        }
    }
}
