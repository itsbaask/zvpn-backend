@file:OptIn(ExperimentalMaterial3Api::class)

package com.zvpn.ui.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zvpn.shared.resources.*
import com.zvpn.ui.components.MorphingBlob
import com.zvpn.ui.platform.BackHandler
import com.zvpn.ui.theme.ZGradients
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

// ───────── Private Browser Screen ─────────

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

// ───────── Shared design helpers ─────────

private val ColorAnim = tween<Color>(durationMillis = 350, easing = FastOutSlowInEasing)

/** Semi-transparent surface so the Tor backdrop glows through the cards. */
@Composable
private fun glass(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)

@Composable
private fun hairline(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

/** Staggered entrance (fade + slide) that doesn't shift the layout. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        },
    ) { content() }
}

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

    BackHandler(enabled = tab.url.isNotBlank() || controller.tabs.size > 1) {
        if (tab.url.isNotBlank()) {
            controller.back()
        } else if (controller.tabs.size > 1) {
            controller.close(tab.id)
        }
    }

    // The web view stays composed (state is preserved) but fades out on the new-tab page
    // so its default white background never shows through.
    val webAlpha by animateFloatAsState(
        targetValue = if (tab.url.isBlank()) 0f else 1f,
        animationSpec = tween(250),
        label = "web-alpha",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (tab.url.isBlank()) {
            TorBackdrop(torState = controller.torState)
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AnimatedVisibility(
                visible = tab.url.isNotBlank(),
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
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
                    PlatformWebView(tab, controller, Modifier.fillMaxSize().alpha(webAlpha))
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = tab.url.isBlank(),
                    enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.97f, animationSpec = tween(400, easing = FastOutSlowInEasing)),
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
                    .padding(bottom = 28.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val open = tab.url.isNotBlank()
                if (open) MenuRow(if (tab.loading) "Stop loading" else "Reload page") { controller.reloadOrStop(); showMenu = false }
                if (open) MenuRow(if (controller.isBookmarked) "Remove bookmark" else "Bookmark page") { controller.toggleBookmark(); showMenu = false }

                MenuSwitchRow("Desktop site", checked = controller.desktopMode, onCheckedChange = { controller.toggleDesktopMode() })
                MenuSwitchRow("Enable JavaScript", checked = controller.javaScriptEnabled, onCheckedChange = { controller.toggleJavaScript() })

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
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
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
                                        text = title.ifBlank { hostOf(url) },
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

// ───────── Backdrop ─────────

@Composable
private fun TorBackdrop(torState: BrowserTorState) {
    val intensity by animateFloatAsState(
        targetValue = when (torState) {
            BrowserTorState.Connecting -> 0.7f
            BrowserTorState.Connected -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "tor-backdrop-intensity",
    )

    val transition = rememberInfiniteTransition(label = "tor-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "tor-pulse-val",
    )

    val stops = if (torState == BrowserTorState.Connected) ZGradients.connectedStops(0) else ZGradients.connectingStops
    val bg = MaterialTheme.colorScheme.background

    // State is read only inside the draw block -> no recomposition per frame.
    Canvas(Modifier.fillMaxSize()) {
        val level = intensity
        if (level <= 0.002f) return@Canvas
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(colors = stops, startY = 0f, endY = h),
            alpha = 0.55f * level,
        )
        // Fade into the app background so text/cards stay readable.
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(0f to Color.Transparent, 1f to bg),
                startY = h * 0.15f,
                endY = h * 0.8f,
            ),
        )

        val center = Offset(w / 2f, h * 0.22f)
        val ringCount = 4
        val p0 = pulse
        for (i in 0 until ringCount) {
            val p = (p0 + i.toFloat() / ringCount) % 1f
            drawCircle(
                color = Color.White.copy(alpha = 0.16f * (1f - p) * level),
                radius = h * 0.05f + p * h * 0.36f,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

// ───────── New tab page ─────────

@Composable
private fun NewTabPage(
    controller: BrowserController,
    privacy: BrowserPrivacy,
    onOpen: (String) -> Unit,
) {
    val isConnected = controller.torState == BrowserTorState.Connected
    val isConnecting = controller.torState == BrowserTorState.Connecting

    // Transparent so the animated Tor backdrop is actually visible.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Reveal(0) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HeroButton(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    onClick = { controller.toggleTor() },
                )

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = controller.torState,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "tor-status-text",
                ) { state ->
                    val connected = state == BrowserTorState.Connected
                    val connecting = state == BrowserTorState.Connecting
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                connected -> "Tor Protected"
                                connecting -> "Connecting Tor… ${controller.torProgress}%"
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                connected -> "Encrypted onion routing"
                                connecting -> "Bootstrapping Tor network relays…"
                                else -> "Tap the button to connect Tor"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Reveal(1) { HeroSearch(isConnected = isConnected, onGo = onOpen) }

        Reveal(2) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                professionalTorTabs.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { site ->
                            SiteTile(site = site, isConnected = isConnected, onClick = { onOpen(site.url) }, modifier = Modifier.weight(1f))
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Reveal(3) { PrivacyCard(p = privacy, isConnected = isConnected) }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeroButton(isConnected: Boolean, isConnecting: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "hero-breath")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val glow by animateFloatAsState(
        targetValue = if (isConnected || isConnecting) 1f else 0f,
        animationSpec = tween(600),
        label = "hero-glow",
    )

    Box(
        Modifier
            .size(168.dp)
            .drawBehind {
                if (glow > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.38f * glow * (0.6f + 0.4f * breath)), Color.Transparent),
                        ),
                        radius = size.minDimension / 2f * (0.85f + 0.15f * breath),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(124.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MorphingBlob(
                    progress = if (isConnected) 1f else 0f,
                    brush = if (isConnected) ZGradients.connected else ZGradients.idle,
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
    }
}

@Composable
private fun HeroSearch(isConnected: Boolean, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val borderColor by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ColorAnim, label = "search-border",
    )
    val buttonColor by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        ColorAnim, label = "search-btn",
    )
    val iconColor by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ColorAnim, label = "search-icon",
    )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = glass(),
        border = BorderStroke(1.5.dp, borderColor),
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(
                        text = "Search DuckDuckGo or enter URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
            Surface(
                onClick = { onGo(text) },
                shape = CircleShape,
                color = buttonColor,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = "Go",
                        tint = iconColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteTile(site: BrowserTab, isConnected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val circleColor by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        ColorAnim, label = "tile-circle",
    )
    val letterColor by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ColorAnim, label = "tile-letter",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = glass(),
        border = hairline(),
        modifier = modifier.height(108.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(circleColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = site.title.first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = letterColor,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = site.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = site.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ───────── Privacy card ─────────

@Composable
private fun PrivacyCard(p: BrowserPrivacy, isConnected: Boolean) {
    val checks = listOf(p.routedThroughVpn, p.torCircuitActive, p.dnsProtected)
    val verified = checks.count { it }
    val good = verified > 0
    val trackers = p.trackersBlocked ?: 0

    val subtitleColor by animateColorAsState(
        if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ColorAnim, label = "privacy-subtitle",
    )
    val badgeBg by animateColorAsState(
        if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ColorAnim, label = "privacy-badge-bg",
    )
    val badgeFg by animateColorAsState(
        if (good) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ColorAnim, label = "privacy-badge-fg",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = glass(),
        border = hairline(),
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
                        color = subtitleColor,
                    )
                }
                Surface(shape = CircleShape, color = badgeBg) {
                    Text(
                        text = "$verified/3",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                checks.forEach { ok ->
                    val segment by animateColorAsState(
                        if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ColorAnim, label = "privacy-segment",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(segment),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            StatusRow("VPN Tunneling", p.routedThroughVpn, "Active", "Not verified")
            StatusRow("Tor Onion Circuit", p.torCircuitActive, "Active", "Not verified")
            StatusRow("DNS Protection", p.dnsProtected, "Active", "Not verified")
            StatusRow("Trackers Blocked", trackers > 0, "$trackers blocked", "0 blocked")
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, offText: String) {
    val dot by animateColorAsState(
        if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ColorAnim, label = "status-dot",
    )
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
                    .background(dot),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (ok) okText else offText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ───────── Address bar ─────────

@Composable
private fun AddressBar(
    url: String,
    privacy: BrowserPrivacy,
    progress: Float?,
    onGo: (String) -> Unit,
) {
    var text by remember(url) { mutableStateOf(url) }
    val verified = privacy.torCircuitActive || privacy.routedThroughVpn
    val borderColor by animateColorAsState(
        if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ColorAnim, label = "address-border",
    )
    val lockColor by animateColorAsState(
        if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ColorAnim, label = "address-lock",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = glass(),
            border = BorderStroke(1.5.dp, borderColor),
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
                    tint = lockColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
        AnimatedVisibility(
            visible = progress != null,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
        ) {
            val animated by animateFloatAsState(progress ?: 1f, tween(250), label = "load-progress")
            Column {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                )
            }
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
private fun MenuSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

// ───────── Bottom toolbar ─────────

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
    val homeTint by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        ColorAnim, label = "home-tint",
    )
    val badgeBg by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        ColorAnim, label = "badge-bg",
    )
    val badgeFg by animateColorAsState(
        if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ColorAnim, label = "badge-fg",
    )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = glass(),
        border = hairline(),
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
                    tint = homeTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onTabs) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$tabCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
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

// ───────── Tab switcher ─────────

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
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
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
            // Scrollable grid so many tabs never overflow the sheet.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tabs, key = { it.id }) { t ->
                    TabCard(
                        t = t,
                        active = t.id == activeId,
                        isConnected = isConnected,
                        onSelect = { onSelect(t.id) },
                        onClose = { onClose(t.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    t: BrowserTabState,
    active: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val activeColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val borderColor by animateColorAsState(
        if (active) activeColor else MaterialTheme.colorScheme.outlineVariant,
        ColorAnim, label = "tab-border",
    )
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (active) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.align(Alignment.BottomStart).padding(end = 28.dp)) {
                Text(
                    text = if (t.url.isBlank()) "New tab" else t.title.ifBlank { hostOf(t.url) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (t.url.isBlank()) "Private" else hostOf(t.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_close), "Close tab", modifier = Modifier.size(16.dp))
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