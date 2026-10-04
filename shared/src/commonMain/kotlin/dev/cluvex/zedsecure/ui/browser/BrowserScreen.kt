@file:OptIn(ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.browser

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.platform.BackHandler
import dev.cluvex.zedsecure.ui.theme.ZedGradients
import org.jetbrains.compose.resources.painterResource

// ───────── Ultra-Modern Private Browser Screen ─────────

data class BrowserTab(
    val id: Int,
    val title: String,
    val url: String,
    val description: String,
    val category: String,
)

val professionalTorTabs = listOf(
    BrowserTab(1, "Tor Check", "https://check.torproject.org", "Verifies active Tor circuit anonymity and IP address masking.", "Security"),
    BrowserTab(2, "IP Inspector", "https://api.ip.sb/ip", "Inspects current public IP address and ISP routing.", "Privacy"),
    BrowserTab(3, "DuckDuckGo", "https://duckduckgo.com", "Zero-tracking private search engine.", "Search"),
    BrowserTab(4, "Ahmia Search", "https://ahmia.fi", "Encrypted onion directory and secure search engine.", "Onion"),
    BrowserTab(5, "DNS Leak Test", "https://www.dnsleaktest.com", "Verifies DNS request protection and leak safety.", "Security"),
    BrowserTab(6, "Tor Metrics", "https://metrics.torproject.org", "Real-time Tor network telemetry and circuit stats.", "Analytics"),
)

data class BrowserPrivacy(
    val routedThroughVpn: Boolean = false,
    val torCircuitActive: Boolean = false,
    val dnsProtected: Boolean = false,
    val trackersBlocked: Int? = null,
)

@Composable
fun BrowserScreen(
    contentPadding: PaddingValues,
    privacy: BrowserPrivacy = BrowserPrivacy(),
    controller: BrowserController = viewModel { BrowserController() },
) {
    var showTabs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf<Int?>(null) } // 0 = bookmarks, 1 = history
    val tab = controller.active
    val isConnected = controller.torState == BrowserTorState.Connected

    val live = privacy.copy(
        trackersBlocked = controller.totalBlocked,
        torCircuitActive = isConnected,
    )

    // Hardware Back button handling
    BackHandler(enabled = tab.url.isNotBlank() || controller.tabs.size > 1) {
        if (tab.url.isNotBlank()) {
            controller.back()
        } else if (controller.tabs.size > 1) {
            controller.close(tab.id)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            BrowserBrandHeader(
                torState = controller.torState,
                torProgress = controller.torProgress,
                onToggleTor = controller::toggleTor,
            )

            if (tab.url.isNotBlank()) {
                AddressBar(
                    url = tab.url,
                    privacy = live,
                    progress = if (tab.loading) tab.progress else null,
                    onGo = controller::open,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                key(tab.id) {
                    PlatformWebView(tab, controller, Modifier.fillMaxSize())
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = tab.url.isBlank(),
                    enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.98f, animationSpec = tween(350)),
                    exit = fadeOut(tween(150)),
                ) {
                    NewTabPage(
                        controller = controller,
                        privacy = live,
                        onOpen = controller::open,
                    )
                }
            }

            BottomToolbar(
                canGoBack = tab.canGoBack || tab.url.isNotBlank(),
                canGoForward = tab.canGoForward,
                tabCount = controller.tabs.size,
                isConnected = isConnected,
                onBack = controller::back,
                onForward = controller::forward,
                onHome = controller::home,
                onTabs = { showTabs = true },
                onMenu = { showMenu = true },
            )
        }
    }

    if (showTabs) {
        TabSwitcher(
            tabs = controller.tabs,
            activeId = controller.activeId,
            isConnected = isConnected,
            onSelect = { controller.select(it); showTabs = false },
            onClose = controller::close,
            onNew = { controller.newTab(); showTabs = false },
            onDismiss = { showTabs = false },
        )
    }
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val open = tab.url.isNotBlank()
                if (open) MenuRow(if (tab.loading) "Stop loading" else "Reload page") { controller.reloadOrStop(); showMenu = false }
                if (open) MenuRow(if (controller.isBookmarked) "Remove bookmark" else "Bookmark page") { controller.toggleBookmark(); showMenu = false }
                MenuRow("Bookmarks (${controller.bookmarks.size})") { library = 0; showMenu = false }
                MenuRow("History (${controller.history.size})") { library = 1; showMenu = false }
                MenuRow("Clear browsing data", danger = true) { controller.clearAll(); showMenu = false }
            }
        }
    }
    library?.let { kind ->
        val bookmarksMode = kind == 0
        val rows = if (bookmarksMode) controller.bookmarks.map { it.title to it.url } else controller.history.map { it.title to it.url }
        ModalBottomSheet(
            onDismissRequest = { library = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = if (bookmarksMode) "Bookmarks" else "History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                if (rows.isEmpty()) {
                    Text(
                        text = "Nothing saved here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows) { (title, url) ->
                            Surface(
                                onClick = { controller.open(url); library = null },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserBrandHeader(
    torState: BrowserTorState,
    torProgress: Int,
    onToggleTor: () -> Unit,
) {
    val isConnected = torState == BrowserTorState.Connected
    val isConnecting = torState == BrowserTorState.Connecting

    val label = when {
        isConnected -> "Tor Connected"
        isConnecting -> "Connecting… $torProgress%"
        else -> "Not connected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                isConnected -> MaterialTheme.colorScheme.primaryContainer
                isConnecting -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.clickable { onToggleTor() },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnected -> Color(0xFF10B981)
                                isConnecting -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.outline
                            },
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                        isConnecting -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = "Z BROWSER",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NewTabPage(
    controller: BrowserController,
    privacy: BrowserPrivacy,
    onOpen: (String) -> Unit,
) {
    val isConnected = controller.torState == BrowserTorState.Connected
    val isConnecting = controller.torState == BrowserTorState.Connecting

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            // Authentic Morphing Blob Hero "Z" Connection Button
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = { controller.toggleTor() },
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(124.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MorphingBlob(
                            progress = if (isConnected) 1f else 0f,
                            brush = if (isConnected) ZedGradients.connected else ZedGradients.idle,
                            modifier = Modifier.size(118.dp),
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(Res.drawable.logo_white),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = when {
                        isConnected -> "Tor Protected"
                        isConnecting -> "Connecting Tor… ${controller.torProgress}%"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = when {
                        isConnected -> "Encrypted Onion Routing · Neon Mode"
                        isConnecting -> "Bootstrapping Tor network relays..."
                        else -> "Tap the Z hero button above to connect Tor"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            HeroSearch(isConnected = isConnected, onGo = onOpen)

            // Quick Access Tabs Grid
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                professionalTorTabs.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { site -> SiteTile(site = site, isConnected = isConnected, onClick = { onOpen(site.url) }, modifier = Modifier.weight(1f)) }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // Privacy Security Card
            PrivacyCard(p = privacy, isConnected = isConnected)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeroSearch(isConnected: Boolean, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.5.dp,
            if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = "Search DuckDuckGo or enter URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Surface(
                onClick = { onGo(text) },
                shape = CircleShape,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = "Go",
                        tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteTile(site: BrowserTab, isConnected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.aspectRatio(0.92f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = site.title.first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = site.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = site.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyCard(p: BrowserPrivacy, isConnected: Boolean) {
    val checks = listOf(p.routedThroughVpn, p.torCircuitActive, p.dnsProtected)
    val verified = checks.count { it }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Protection Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (verified == 0) "Not verified" else "$verified of 3 security checks active",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (verified > 0 && isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (verified > 0 && isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = "$verified/3",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (verified > 0 && isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                checks.forEach { ok ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (ok && isConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            StatusRow("VPN Tunneling", p.routedThroughVpn, isConnected, "Active", "Not verified")
            StatusRow("Tor Onion Circuit", p.torCircuitActive, isConnected, "Active", "Not verified")
            StatusRow("DNS Protection", p.dnsProtected, isConnected, "Active", "Not verified")
            StatusRow("Trackers Blocked", p.trackersBlocked != null, isConnected, "${p.trackersBlocked} blocked", "0 blocked")
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, isConnected: Boolean, okText: String, offText: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (ok && isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (ok) okText else offText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AddressBar(
    url: String,
    privacy: BrowserPrivacy,
    progress: Float?,
    onGo: (String) -> Unit,
) {
    var text by remember(url) { mutableStateOf(url) }
    val verified = privacy.torCircuitActive || privacy.routedThroughVpn
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_encrypted),
                    contentDescription = null,
                    tint = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Search or enter URL",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onGo(text) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun MenuRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun BottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    isConnected: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(painterResource(Res.drawable.ic_arrow_back), "Back")
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(painterResource(Res.drawable.ic_chevron_right), "Forward")
            }
            IconButton(onClick = onHome) {
                Icon(
                    painter = painterResource(Res.drawable.logo_white),
                    contentDescription = "Home",
                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onTabs) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$tabCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onMenu) {
                Text("⋯", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TabSwitcher(
    tabs: List<BrowserTabState>,
    activeId: Int,
    isConnected: Boolean,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tabs · ${tabs.size}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onNew) { Text("New tab") }
            }
            tabs.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { t ->
                        val active = t.id == activeId
                        Surface(
                            onClick = { onSelect(t.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) (if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                        ) {
                            Box(Modifier.fillMaxSize().padding(14.dp)) {
                                Column(Modifier.align(Alignment.BottomStart).padding(end = 28.dp)) {
                                    Text(
                                        text = if (t.url.isBlank()) "New tab" else t.title.ifBlank { hostOf(t.url) },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (t.url.isBlank()) "Private" else "Open",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = { onClose(t.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp),
                                ) {
                                    Icon(painterResource(Res.drawable.ic_close), "Close tab", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("http://").substringBefore('/')

private fun normalizeUrl(raw: String): String {
    val s = raw.trim()
    return when {
        s.isEmpty() -> ""
        s.startsWith("http://") || s.startsWith("https://") -> s
        '.' in s && ' ' !in s -> "https://$s"
        else -> "https://duckduckgo.com/?q=" + s.replace(' ', '+')
    }
}
