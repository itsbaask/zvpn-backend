package com.zvpn.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zvpn.data.config.ConfigRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zvpn.core.VpnManager
import com.zvpn.data.map.WorldMap
import com.zvpn.domain.config.VpnProfile
import com.zvpn.domain.model.ConnectionState
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.ic_check_circle
import com.zvpn.shared.resources.ic_close
import com.zvpn.shared.resources.ic_info
import com.zvpn.shared.resources.ic_search
import com.zvpn.shared.resources.map_exit
import com.zvpn.shared.resources.map_locating
import com.zvpn.shared.resources.map_no_origin
import com.zvpn.shared.resources.map_offline_here
import com.zvpn.shared.resources.map_other_vpn
import com.zvpn.shared.resources.map_ping
import com.zvpn.shared.resources.map_sea_caspian
import com.zvpn.shared.resources.map_sea_persian_gulf
import com.zvpn.shared.resources.map_server
import com.zvpn.shared.resources.map_subtitle
import com.zvpn.shared.resources.map_title
import com.zvpn.shared.resources.map_unknown
import com.zvpn.shared.resources.map_vpn_warning
import com.zvpn.shared.resources.map_you
import com.zvpn.shared.resources.map_you_saved
import com.zvpn.ui.components.FlagBadge
import com.zvpn.ui.format.ltrIsolate
import com.zvpn.ui.theme.Personalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MapScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    repository: ConfigRepository? = null,
    reduceMotion: Boolean = false,
    ipApiUrl: String = "",
    personalization: Personalization = Personalization.Default,

    savedOriginCode: String = "",
    savedOriginLabel: String = "",
    onRealLocation: (code: String, label: String) -> Unit = { _, _ -> },
    vm: MapViewModel = viewModel { MapViewModel() },
) {
    LaunchedEffect(repository) {
        repository?.let { vm.setRepository(it) }
    }
    val status by VpnManager.status.collectAsStateWithLifecycle()
    val connected = status.state == ConnectionState.Connected
    val ownTunnel = status.state.isActive || status.state.isTransitioning

    var deviceVpn by remember { mutableStateOf(VpnManager.deviceVpnActive()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(VPN_POLL_MS)
            deviceVpn = withContext(Dispatchers.Default) { VpnManager.deviceVpnActive() }
        }
    }
    val vpnOn = ownTunnel || deviceVpn
    val countries by vm.countries.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()

    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedProtocol by vm.selectedProtocol.collectAsStateWithLifecycle()
    val maxPingFilter by vm.maxPingFilter.collectAsStateWithLifecycle()
    val selectedRegion by vm.selectedRegion.collectAsStateWithLifecycle()
    val filteredProfiles by vm.filteredProfiles.collectAsStateWithLifecycle()
    val selectedServer by vm.selectedServer.collectAsStateWithLifecycle()

    LaunchedEffect(ipApiUrl) { vm.preferredIpApiUrl = ipApiUrl.ifBlank { null } }

    val autoSession by com.zvpn.core.AutoSelect.session.collectAsStateWithLifecycle()
    val exitGeneration = autoSession?.exitGeneration ?: 0

    LaunchedEffect(connected, status.sessionId, vpnOn, exitGeneration) { vm.refresh() }

    LaunchedEffect(vpnOn, info) {
        val code = info.countryCode
        if (!vpnOn && !info.underVpn && !info.loading && !code.isNullOrBlank()) {
            onRealLocation(code.uppercase(), locationLabel(info.city, info.country))
        }
    }

    val accent = personalization.connectedColor ?: MaterialTheme.colorScheme.primary
    val originTint = MaterialTheme.colorScheme.tertiary
    val land = MaterialTheme.colorScheme.onSurface

    val lookupIsExit = info.underVpn
    val originCode = when {
        vpnOn -> savedOriginCode
        lookupIsExit -> ""
        else -> info.countryCode.orEmpty()
    }
    val originLabel = when {
        vpnOn -> savedOriginLabel
        lookupIsExit -> ""
        else -> locationLabel(info.city, info.country)
    }
    val exitCode = if (vpnOn && lookupIsExit) info.countryCode.orEmpty() else ""
    val exitLabel = if (vpnOn && lookupIsExit) locationLabel(info.city, info.country) else ""

    val origin = WorldMap.anchorOf(countries, originCode)
        ?.let { MapPoint(it.first, it.second, originLabel, originCode.uppercase()) }
    val exit = WorldMap.anchorOf(countries, exitCode)
        ?.let { MapPoint(it.first, it.second, exitLabel, exitCode.uppercase()) }

    // Convert filtered server profiles into Map Server Pins
    val serverPins = remember(filteredProfiles, countries) {
        filteredProfiles.mapNotNull { p ->
            val cc = p.countryCode ?: "UN"
            val anchor = WorldMap.anchorOf(countries, cc) ?: return@mapNotNull null
            ServerPin(
                id = p.id,
                countryCode = cc.uppercase(),
                countryName = p.name,
                point = MapPoint(anchor.first, anchor.second, p.name, cc.uppercase()),
                count = 1,
                bestPingMs = p.lastPingMs,
                profile = p
            )
        }
    }

    val selectedPin = remember(selectedServer, countries) {
        selectedServer?.let { p ->
            val cc = p.countryCode ?: "UN"
            val anchor = WorldMap.anchorOf(countries, cc)
            if (anchor != null) {
                ServerPin(
                    id = p.id,
                    countryCode = cc.uppercase(),
                    countryName = p.name,
                    point = MapPoint(anchor.first, anchor.second, p.name, cc.uppercase()),
                    count = 1,
                    bestPingMs = p.lastPingMs,
                    profile = p
                )
            } else null
        }
    }

    val persianGulf = stringResource(Res.string.map_sea_persian_gulf)
    val caspian = stringResource(Res.string.map_sea_caspian)
    val labels = remember(persianGulf, caspian) {
        listOf(
            seaCaption(persianGulf, lon = 51.8f, lat = 26.8f, widthDegrees = 6.5f),
            seaCaption(caspian, lon = 50.9f, lat = 41.6f, widthDegrees = 4.0f),
        )
    }

    Box(modifier.fillMaxSize()) {
        WorldMapCanvas(
            countries = countries,
            origin = origin,
            exit = exit,
            serverPins = serverPins,
            selectedPin = selectedPin,
            onSelectPin = { pin -> vm.selectServer(pin.profile) },
            accent = accent,
            originColor = originTint,
            landColor = land,
            reduceMotion = reduceMotion,
            labels = labels,
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Page Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.zvpn.ui.components.PageBackButton()
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(Res.string.map_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${filteredProfiles.size} Servers Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Floating Search & Filter Header
            MapFilterBar(
                searchQuery = searchQuery,
                onSearchChange = { vm.searchQuery.value = it },
                selectedProtocol = selectedProtocol,
                onProtocolSelect = { vm.selectedProtocol.value = it },
                maxPingFilter = maxPingFilter,
                onPingFilterSelect = { vm.maxPingFilter.value = it },
                selectedRegion = selectedRegion,
                onRegionSelect = { vm.selectedRegion.value = it },
                onReset = { vm.resetFilters() },
                accent = accent
            )

            Spacer(Modifier.weight(1f))

            // Interactive Server Selection Bottom Sheet
            AnimatedVisibility(
                visible = selectedServer != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedServer?.let { server ->
                    ServerCardSheet(
                        server = server,
                        accent = accent,
                        onClose = { vm.selectServer(null) },
                        onConnect = {
                            vm.setActiveServer(server)
                            vm.selectServer(null)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MapFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedProtocol: String,
    onProtocolSelect: (String) -> Unit,
    maxPingFilter: Int?,
    onPingFilterSelect: (Int?) -> Unit,
    selectedRegion: String,
    onRegionSelect: (String) -> Unit,
    onReset: () -> Unit,
    accent: Color
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search Country or City...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(painterResource(Res.drawable.ic_close), null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            // Scrollable Filter Chips Row
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button if active
                if (searchQuery.isNotEmpty() || selectedProtocol != "All" || maxPingFilter != null || selectedRegion != "All") {
                    FilterChip(
                        selected = true,
                        onClick = onReset,
                        label = { Text("Reset Filters ✕", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.error,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Protocol Chips
                val protocols = listOf("All", "OpenVPN", "VLESS", "WireGuard", "VMess", "Shadowsocks", "Trojan")
                protocols.forEach { proto ->
                    FilterChip(
                        selected = selectedProtocol == proto,
                        onClick = { onProtocolSelect(proto) },
                        label = { Text(proto, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Ping Filter Chips
                val pingOpts = listOf(null to "Ping: All", 100 to "< 100ms", 200 to "< 200ms", 300 to "< 300ms")
                pingOpts.forEach { (limit, label) ->
                    FilterChip(
                        selected = maxPingFilter == limit,
                        onClick = { onPingFilterSelect(limit) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Region Filter Chips
                val regions = listOf("All", "Europe", "Americas", "Asia-Pacific", "ME/Africa")
                regions.forEach { reg ->
                    FilterChip(
                        selected = selectedRegion == reg,
                        onClick = { onRegionSelect(reg) },
                        label = { Text(reg, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCardSheet(
    server: VpnProfile,
    accent: Color,
    onClose: () -> Unit,
    onConnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagBadge(server.countryCode ?: "UN", size = 32.dp, modifier = Modifier.clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            server.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${server.protocol.uppercase()} • ${server.address}:${server.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(painterResource(Res.drawable.ic_close), null, modifier = Modifier.size(20.dp))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ping Badge
                val ping = server.lastPingMs
                val pingColor = when {
                    ping == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    ping < 100 -> Color(0xFF4CAF50)
                    ping < 200 -> Color(0xFFFFC107)
                    else -> Color(0xFFFF5722)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pingColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = if (ping != null) "$ping ms" else "Fast",
                        color = pingColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = server.protocol.uppercase(),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Connect Action Button
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(painterResource(Res.drawable.ic_check_circle), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect to ${server.name}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun seaCaption(text: String, lon: Float, lat: Float, widthDegrees: Float): MapLabel {
    val (x, y) = WorldMap.gridOf(lon, lat)
    return MapLabel(x, y, text, span = widthDegrees / 360f * WorldMap.WIDTH)
}

private fun locationLabel(city: String?, country: String?): String = listOfNotNull(
    city?.takeIf { it.isNotBlank() },
    country?.takeIf { it.isNotBlank() },
).joinToString(", ")

private fun shortLabel(label: String): String =
    label.substringBefore(',').trim()

private const val VPN_POLL_MS = 2_000L
