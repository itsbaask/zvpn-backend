@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.zvpn.ui.servers

import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.*
import com.zvpn.data.config.ConfigRepository
import com.zvpn.data.net.PingCoordinator
import com.zvpn.data.net.PingService
import com.zvpn.core.AutoSelect
import com.zvpn.domain.config.AutoSelectIds
import com.zvpn.domain.config.ConfigParseException
import com.zvpn.domain.config.VpnProfile
import com.zvpn.ui.onboarding.TourTargets
import com.zvpn.ui.onboarding.tourTarget
import com.zvpn.ui.platform.LocalPlatform
import com.zvpn.ui.components.PageHeader
import com.zvpn.ui.theme.Personalization
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zvpn.ui.platform.mouseDragScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.zvpn.ui.components.MorphingBlob
import com.zvpn.ui.platform.onSecondaryClick
import com.zvpn.ui.theme.ZGradients
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun ServersScreen(
    repository: ConfigRepository,
    contentPadding: PaddingValues,
    onServerActivated: () -> Unit,
    modifier: Modifier = Modifier,
    twoColumns: Boolean = false,
    showAllGroup: Boolean = true,
    confirmRemove: Boolean = true,

    realPingConcurrency: Int = 16,
    delayTestUrl: String = "",
    autoTestAfterUpdate: Boolean = false,
    autoRemoveInvalidAfterTest: Boolean = false,
    autoSortAfterTest: Boolean = false,

    personalization: Personalization = Personalization.Default,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val lockedCannotShare = stringResource(Res.string.locked_cannot_share)

    fun toastImportFailure(e: Throwable) {
        if (e is ConfigRepository.SubscriptionNotFetchedException) {
            scope.launch {
                platform.toast(
                    if (e.savedForLater) getString(Res.string.subs_saved_unreachable, e.name)
                    else getString(Res.string.subs_failed),
                )
            }
            return
        }
        val ovpn = (e as? com.zvpn.domain.config.OvpnConfig.UnsupportedException)?.reason
        val res = when {
            ovpn != null -> when (ovpn) {
                com.zvpn.domain.config.OvpnConfig.Reason.NoRemote -> Res.string.import_err_ovpn_no_remote
                com.zvpn.domain.config.OvpnConfig.Reason.Tap -> Res.string.import_err_ovpn_tap
                com.zvpn.domain.config.OvpnConfig.Reason.ExternalFiles -> Res.string.import_err_ovpn_files
                com.zvpn.domain.config.OvpnConfig.Reason.UnsupportedCredentials ->
                    Res.string.import_err_ovpn_creds
            }

            e is com.zvpn.domain.config.AmneziaLink.UnsupportedException -> when (e.reason) {
                com.zvpn.domain.config.AmneziaLink.Reason.Backup ->
                    Res.string.import_err_amnezia_backup
                com.zvpn.domain.config.AmneziaLink.Reason.Subscription ->
                    Res.string.import_err_amnezia_subscription
                com.zvpn.domain.config.AmneziaLink.Reason.NoRunnableContainer ->
                    Res.string.import_err_amnezia_unsupported
            }
            else -> when ((e as? ConfigParseException)?.reason) {
                ConfigParseException.Reason.UnsupportedSsCipher -> Res.string.import_err_ss_cipher
                ConfigParseException.Reason.MissingSsCipher -> Res.string.import_err_ss_no_cipher
                ConfigParseException.Reason.UnsupportedSsPlugin -> Res.string.import_err_ss_plugin
                else -> Res.string.config_invalid
            }
        }
        scope.launch { platform.toast(getString(res)) }
    }

    fun toastRes(res: StringResource, vararg args: Any) {
        scope.launch { platform.toast(getString(res, *args)) }
    }

    val customJsonInvalid = stringResource(Res.string.custom_json_invalid)
    val singBoxJsonInvalid = stringResource(Res.string.singbox_json_invalid)

    var obfuscateCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    var ovpnPrompt by remember { mutableStateOf<OvpnPrompt?>(null) }

    suspend fun offerObfuscationAfter(block: suspend () -> Unit) {
        val before = repository.profiles.value.map { it.id }.toSet()
        block()
        val fresh = repository.profiles.value.map { it.id }.filterNot { it in before }
        if (fresh.isNotEmpty()) obfuscateCandidates = repository.plainWireguardAmong(fresh)
    }

    fun importScanned(text: String) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var outcome: Result<com.zvpn.data.config.ConfigRepository.PastedImport>? = null
            offerObfuscationAfter { outcome = repository.importPasted(text) }
            outcome!!
                .onSuccess { r ->
                    platform.toast(
                        when {
                            r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                            r.count > 0 -> getString(Res.string.servers_imported, r.count)

                            r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                            else -> getString(Res.string.config_invalid)
                        },
                    )
                }
                .onFailure { toastImportFailure(it) }
        }
    }

    fun importFromFile() {
        scope.launch {
            val picked = platform.pickFileBytes() ?: return@launch
            val text = picked.bytes.decodeToString()
            if (text.isBlank()) {
                toastRes(Res.string.config_invalid)
                return@launch
            }
            val fileName = picked.name.substringBeforeLast('.').takeIf { it.isNotBlank() }

            val namedOvpn = picked.name.endsWith(".ovpn", ignoreCase = true)
            val info = runCatching {
                com.zvpn.domain.config.OvpnConfig
                    .takeIf { namedOvpn || it.looksLikeOvpn(text) }?.inspect(text)
            }
            info.onFailure {
                toastImportFailure(it)

                if (namedOvpn) return@launch
            }
            val ovpn = info.getOrNull()
            if (ovpn != null && ovpn.needsCredentials && !ovpn.hasInlineCredentials) {
                ovpnPrompt = OvpnPrompt(text = text, name = fileName ?: ovpn.name)
                return@launch
            }
            if (ovpn != null) {
                repository.addOvpn(text, name = fileName ?: ovpn.name)
                    .onSuccess { platform.toast(getString(Res.string.servers_imported, 1)) }
                    .onFailure { toastImportFailure(it) }
                return@launch
            }
            withContext(kotlinx.coroutines.Dispatchers.Default) { repository.importPasted(text) }
                .onSuccess { r ->
                    platform.toast(
                        when {
                            r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                            r.count > 0 -> getString(Res.string.servers_imported, r.count)
                            r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                            else -> getString(Res.string.config_invalid)
                        },
                    )
                }
                .onFailure { toastImportFailure(it) }
        }
    }

    fun importFromClipboard() {
        val text = platform.readClipboard()
        if (text.isNullOrBlank()) {
            toastRes(Res.string.paste_empty)
            return
        }

        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var outcome: Result<com.zvpn.data.config.ConfigRepository.PastedImport>? = null
            offerObfuscationAfter { outcome = repository.importPasted(text) }
            outcome!!
                .onSuccess { r ->
                    platform.toast(
                        when {
                            r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                            r.count > 0 -> getString(Res.string.servers_imported, r.count)

                            r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                            else -> getString(Res.string.config_invalid)
                        },
                    )
                }
                .onFailure { toastImportFailure(it) }
        }
    }
    val profiles by repository.profiles.collectAsStateWithLifecycle()
    val subscriptions by repository.subscriptions.collectAsStateWithLifecycle()
    val activeId by repository.activeId.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }

    var fanOpen by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<VpnProfile?>(null) }
    var updating by remember { mutableStateOf(false) }

    val testing = rememberIsTesting()

    var showRealDelayNote by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    var confirmDeleteInvalid by remember { mutableStateOf<Int?>(null) }
    var editTarget by remember { mutableStateOf<VpnProfile?>(null) }

    var moveTarget by remember { mutableStateOf<VpnProfile?>(null) }

    var qrTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCustomJson by remember { mutableStateOf(false) }
    var showPsiphon by remember { mutableStateOf(false) }
    var showDnsTunnel by remember { mutableStateOf(false) }
    var showMasterDns by remember { mutableStateOf(false) }
    var showOpenConnect by remember { mutableStateOf(false) }
    var showIkev2 by remember { mutableStateOf(false) }
    var showSsh by remember { mutableStateOf(false) }
    var showSniSpoof by remember { mutableStateOf(false) }
    var showProxyChain by remember { mutableStateOf(false) }
    var showCrossChain by remember { mutableStateOf(false) }

    val servers = remember(profiles) { profiles.filterNot { it.isLocked } }

    val chainCandidates = remember(servers) {
        servers.filter {
            it.rawPayload() != null && (!it.isCustom || it.isServerless) && !it.isManagedTunnel && !it.isProxyChain &&
                    !it.isSingBoxConfig
        }
    }

    val spoofCandidates = remember(servers) {
        servers.filter {
            it.rawPayload() != null && !it.isManagedTunnel && !it.isProxyChain &&
                    !it.isCrossChain && !it.isSniSpoof
        }
    }
    val crossCarriers = remember(servers) { servers.filter { it.canCarryChain && !it.isCrossChain } }
    val crossExits = remember(servers) { servers.filter { it.canDialThroughProxy && !it.isCrossChain } }

    val udpMismatchTemplate = stringResource(Res.string.crosschain_udp_unsupported)
    val crossPairError: (VpnProfile, VpnProfile) -> String? = { inner, outer ->
        repository.udpMismatch(inner, outer)?.let {
            udpMismatchTemplate.replace("%1\$s", it.protocol).replace("%2\$s", it.carrier)
        }
    }

    val activeGroupIndex: () -> Int = {
        val active = when {
            activeId != null && AutoSelectIds.isAuto(activeId) ->
                runCatching { AutoSelectIds.subscriptionOf(activeId!!) }.getOrNull().orEmpty()
            else -> profiles.firstOrNull { it.id == activeId }?.subscriptionId.orEmpty()
        }
        when {
            active.isEmpty() -> if (showAllGroup) 0 else 1
            else -> subscriptions.indexOfFirst { it.id == active }.takeIf { it >= 0 }?.plus(2)
                ?: if (showAllGroup) 0 else 1
        }
    }

    var groupIndex by rememberSaveable { mutableIntStateOf(activeGroupIndex()) }
    var updatingSubId by remember { mutableStateOf<String?>(null) }
    val openSubscription = subscriptions.getOrNull(groupIndex - 2)
    var openSubscriptionId by remember { mutableStateOf(openSubscription?.id) }
    LaunchedEffect(groupIndex) { openSubscriptionId = subscriptions.getOrNull(groupIndex - 2)?.id }
    LaunchedEffect(subscriptions) {
        val id = openSubscriptionId ?: return@LaunchedEffect
        val index = subscriptions.indexOfFirst { it.id == id }
        if (index >= 0 && index + 2 != groupIndex) groupIndex = index + 2
    }

    fun updateSubscription(sub: com.zvpn.domain.config.Subscription) {
        if (updatingSubId != null) return
        updatingSubId = sub.id
        scope.launch {
            repository.updateSubscription(sub.id)
                .onSuccess { platform.toast(getString(Res.string.servers_imported, it)) }
                .onFailure { platform.toast(getString(Res.string.subs_failed)) }
            updatingSubId = null
        }
    }

    LaunchedEffect(activeId, subscriptions.size, showAllGroup) {
        groupIndex = activeGroupIndex()
    }

    val shown = remember(servers, subscriptions, groupIndex) {
        when {
            subscriptions.isEmpty() || groupIndex == 0 -> servers
            groupIndex == 1 -> servers.filter { it.subscriptionId.isEmpty() }
            else -> subscriptions.getOrNull(groupIndex - 2)?.let { sub ->
                servers.filter { it.subscriptionId == sub.id }
            } ?: servers
        }
    }

    val headerCount = shown.size

    val autoScope: String? = when {
        subscriptions.isEmpty() || groupIndex == 0 -> null
        groupIndex == 1 -> ""
        else -> subscriptions.getOrNull(groupIndex - 2)?.id
    }
    val autoId = AutoSelectIds.of(autoScope)
    val autoMembers = remember(profiles, autoScope) { repository.autoSelectMembers(autoScope) }
    val autoSession by AutoSelect.session.collectAsStateWithLifecycle()
    val autoLive = autoSession?.takeIf { it.profileId == autoId }
    val autoLabel = when (autoScope) {
        null -> stringResource(Res.string.auto_group_all)
        "" -> stringResource(Res.string.group_manual)
        else -> subscriptions.firstOrNull { it.id == autoScope }?.name.orEmpty()
    }

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    var moveSelectionTo by remember { mutableStateOf(false) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    val shownIds = remember(shown) { shown.map { it.id } }

    LaunchedEffect(shownIds) {
        if (selecting) selected = selected.intersect(shownIds.toSet())
    }
    var showAutoSheet by remember { mutableStateOf(false) }
    if (showAutoSheet) {
        AutoSelectSheet(
            groupLabel = autoLabel,
            members = autoMembers,
            live = autoLive,
            onDismiss = { showAutoSheet = false },
        )
    }

    // ── Shared actions (previously duplicated inline) ──────────────────────

    fun startPing(real: Boolean) {
        PingCoordinator.start(
            profiles = shown,
            useRealDelay = real,
            concurrency = realPingConcurrency,
            delayUrl = delayTestUrl,
            chainConfig = repository::chainProbeConfig,

            clearPings = { repository.clearPings(it, persist = false) },
            onResult = { id, ms, cc -> repository.setPing(id, ms, cc, persist = false) },
            flush = { repository.flushProfiles() },

            onFinished = { if (autoSortAfterTest) repository.sortByTestResults() },
        )
    }

    fun updateAll() {
        if (updating) return
        updating = true
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val sysN = repository.syncSystemBackendServers(force = true).getOrDefault(0)
            val subN = repository.updateAllSubscriptions()
            val totalN = if (sysN > 0) sysN else subN
            if (autoTestAfterUpdate) {
                val all = repository.profiles.value
                repository.clearPings(all.map { it.id }, persist = false)
                PingService.measureAll(
                    all,
                    useRealDelay = true,
                    concurrency = realPingConcurrency,
                    delayUrl = delayTestUrl,
                    chainConfig = repository::chainProbeConfig,
                ) { id, ms, cc ->
                    repository.setPing(id, ms, cc, persist = false)
                }
                repository.flushProfiles()
                if (autoRemoveInvalidAfterTest) repository.removeInvalid()
                if (autoSortAfterTest) repository.sortByTestResults()
            }
            updating = false
            toastRes(Res.string.subs_updated, totalN)
        }
    }

    if (showRealDelayNote) {
        AlertDialog(
            onDismissRequest = { showRealDelayNote = false },
            confirmButton = {
                TextButton(onClick = { showRealDelayNote = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            icon = { Icon(painterResource(Res.drawable.ic_speed), contentDescription = null) },
            title = { Text(stringResource(Res.string.real_delay_note_title)) },
            text = {
                Text(
                    stringResource(Res.string.real_delay_note_body),

                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.55f,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            Spacer(Modifier.height(8.dp))

            // ── Header: title + one compact action pill ───────────────────
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    PageHeader(
                        title = stringResource(Res.string.nav_servers),
                        subtitle = if (servers.isEmpty()) null
                        else stringResource(Res.string.servers_count, headerCount),

                        singleLine = true,
                    )
                }
                ServersActionBar(
                    testing = testing,
                    updating = updating,
                    onPingTcp = { startPing(real = false) },
                    onPingReal = { startPing(real = true) },
                    onStopPing = { PingCoordinator.cancel() },
                    onSubscriptions = { showSubs = true },
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.real_delay_note_title)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_info), null) },
                        onClick = {
                            dismiss()
                            showRealDelayNote = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.subs_update_all)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_sync), null) },
                        enabled = !updating,
                        onClick = {
                            dismiss()
                            updateAll()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.select_multiple)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_check_circle), null) },
                        enabled = shown.isNotEmpty(),
                        onClick = {
                            dismiss()
                            selecting = true
                            selected = emptySet()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.sort_by_results)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_tune), null) },
                        onClick = {
                            dismiss()
                            repository.sortByTestResults()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.del_duplicates)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                        onClick = {
                            dismiss()
                            val n = repository.removeDuplicates()
                            toastRes(Res.string.removed_count, n)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.del_invalid)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                        onClick = {
                            dismiss()
                            confirmDeleteInvalid = repository.invalidCount()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.export_all)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_ios_share), null) },
                        onClick = {
                            dismiss()
                            val payload = repository.exportAllPayloads()
                            if (payload.isBlank()) {
                                toastRes(Res.string.nothing_to_export)
                            } else {
                                platform.shareText(payload)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.del_all)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                        onClick = {
                            dismiss()
                            confirmDeleteAll = true
                        },
                    )
                }
            }

            PingProgressHost()

            // ── Group chips ───────────────────────────────────────────────
            if (subscriptions.isNotEmpty()) {
                val counts = remember(servers) { servers.groupingBy { it.subscriptionId }.eachCount() }

                LaunchedEffect(showAllGroup) { if (!showAllGroup && groupIndex == 0) groupIndex = 1 }

                val groupRow = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.foundation.lazy.LazyRow(
                    state = groupRow,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().tourTarget(TourTargets.SERVERS_GROUPS)
                        .mouseDragScroll(groupRow),
                ) {
                    if (showAllGroup) {
                        item(key = "group-all") {
                            GroupChip(
                                label = stringResource(Res.string.group_all),
                                count = servers.size,
                                selected = groupIndex == 0,
                                onClick = { groupIndex = 0 },
                            )
                        }
                    }
                    item(key = "group-manual") {
                        GroupChip(
                            label = stringResource(Res.string.group_manual),
                            count = counts[""] ?: 0,
                            selected = groupIndex == 1,
                            onClick = { groupIndex = 1 },
                        )
                    }
                    itemsIndexed(subscriptions, key = { _, sub -> sub.id }) { index, sub ->
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            GroupChip(
                                label = sub.name,
                                count = counts[sub.id] ?: 0,
                                selected = groupIndex == index + 2,
                                onClick = { groupIndex = index + 2 },
                                onMenu = { menuOpen = true },
                            )
                            androidx.compose.material3.DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                shape = MaterialTheme.shapes.largeIncreased,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.subs_update)) },
                                    leadingIcon = { Icon(painterResource(Res.drawable.ic_sync), null) },
                                    enabled = updatingSubId == null,
                                    onClick = {
                                        menuOpen = false
                                        updateSubscription(sub)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.subs_title)) },
                                    leadingIcon = { Icon(painterResource(Res.drawable.ic_edit), null) },
                                    onClick = {
                                        menuOpen = false
                                        showSubs = true
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Multi-select bar ──────────────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(visible = selecting) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.select_count, selected.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selected = if (selected.size == shownIds.size) emptySet() else shownIds.toSet()
                        }) {
                            Text(
                                stringResource(
                                    if (selected.size == shownIds.size && shownIds.isNotEmpty()) {
                                        Res.string.select_none
                                    } else {
                                        Res.string.select_all
                                    },
                                ),
                            )
                        }
                        IconButton(
                            onClick = { moveSelectionTo = true },
                            enabled = selected.isNotEmpty() && subscriptions.isNotEmpty(),
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_add_link),
                                contentDescription = stringResource(Res.string.groups_move),
                            )
                        }
                        IconButton(
                            onClick = { confirmDeleteSelection = true },
                            enabled = selected.isNotEmpty(),
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.action_delete),
                            )
                        }
                        IconButton(onClick = { selecting = false; selected = emptySet() }) {
                            Icon(
                                painterResource(Res.drawable.ic_close),
                                contentDescription = stringResource(Res.string.action_cancel),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val swipeLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
            val groupSwipe = if (subscriptions.isEmpty()) {
                Modifier
            } else {
                Modifier.pointerInput(subscriptions.size, showAllGroup, swipeLtr) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { _: androidx.compose.ui.geometry.Offset -> travelled = 0f },
                        onDragEnd = {
                            val lowest = if (showAllGroup) 0 else 1
                            val highest = subscriptions.size + 1
                            val forward = if (swipeLtr) travelled <= -swipeThreshold else travelled >= swipeThreshold
                            val back = if (swipeLtr) travelled >= swipeThreshold else travelled <= -swipeThreshold
                            when {
                                forward -> groupIndex = (groupIndex + 1).coerceAtMost(highest)
                                back -> groupIndex = (groupIndex - 1).coerceAtLeast(lowest)
                            }
                        },
                    ) { _: androidx.compose.ui.input.pointer.PointerInputChange, delta: Float ->
                        travelled += delta
                    }
                }
            }

            if (shown.isEmpty()) {
                ServersEmptyState(
                    onAdd = { showAddSheet = true },
                    onPaste = { importFromClipboard() },
                    modifier = Modifier.fillMaxSize().then(groupSwipe)
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                )
            } else {
                val publicServers = rememberPublicServers(shown)

                PublicServersTab(
                    servers = publicServers,
                    selectedId = activeId,
                    userTier = com.zvpn.domain.billing.UserTier.Free,
                    onSelect = { server ->
                        repository.setActive(server.id)
                        onServerActivated()
                    },
                    onLockedClick = { server ->
                        platform.toast(lockedCannotShare)
                    },
                    modifier = Modifier.fillMaxSize().then(groupSwipe)
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                )
            }
        }

        // ── Scrim behind the speed dial ───────────────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = fanOpen,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { fanOpen = false },
            )
        }

        // ── FAB + speed dial (thumb zone, bottom-end) ─────────────────────
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            SpeedDialItem(
                visible = fanOpen,
                delayMs = 90,
                icon = Res.drawable.ic_edit,
                label = stringResource(Res.string.servers_add_manual),
            ) {
                fanOpen = false
                showManual = true
            }
            SpeedDialItem(
                visible = fanOpen,
                delayMs = 45,
                icon = Res.drawable.ic_content_paste,
                label = stringResource(Res.string.servers_add_clipboard),
            ) {
                fanOpen = false
                importFromClipboard()
            }
            SpeedDialItem(
                visible = fanOpen,
                delayMs = 0,
                icon = Res.drawable.ic_more_vert,
                label = stringResource(Res.string.servers_add_more),
            ) {
                fanOpen = false
                showAddSheet = true
            }

            AddServerFab(
                fanOpen = fanOpen,
                onClick = { showAddSheet = true },
                onLongClick = { fanOpen = true },
                modifier = Modifier.tourTarget(TourTargets.SERVERS_ADD),
            )
        }
    }

    ovpnPrompt?.let { prompt ->
        var username by remember(prompt) { mutableStateOf("") }
        var password by remember(prompt) { mutableStateOf("") }
        val save = {
            val text = prompt.text
            val name = prompt.name
            val user = username
            val pass = password
            ovpnPrompt = null
            scope.launch {
                repository.addOvpn(text, name = name, username = user, password = pass)
                    .onSuccess { platform.toast(getString(Res.string.servers_imported, 1)) }
                    .onFailure { toastImportFailure(it) }
            }
            Unit
        }
        AlertDialog(
            onDismissRequest = { ovpnPrompt = null },
            title = { Text(stringResource(Res.string.ovpn_credentials_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.ovpn_credentials_body, prompt.name))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.ovpn_username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.ovpn_password)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = save) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { ovpnPrompt = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    if (showAddSheet) {
        AddServerSheetPro(
            onDismiss = { showAddSheet = false },
            onPasteLink = {
                showAddSheet = false
                importFromClipboard()
            },
            onImportFile = {
                showAddSheet = false
                importFromFile()
            },
            onScanQr = {
                showAddSheet = false
                platform.scanQrCode { text -> if (text != null) importScanned(text) }
            },
            onScanQrImage = {
                showAddSheet = false
                platform.scanQrFromImage { text -> if (text != null) importScanned(text) }
            },
            onManual = {
                showAddSheet = false
                showManual = true
            },
            onCustom = {
                showAddSheet = false
                showCustomJson = true
            },
            onPsiphon = {
                showAddSheet = false
                showPsiphon = true
            },
            onDnsTunnel = {
                showAddSheet = false
                showDnsTunnel = true
            },
            onMasterDns = {
                showAddSheet = false
                showMasterDns = true
            },
            onOpenConnect = {
                showAddSheet = false
                showOpenConnect = true
            },
            onIkev2 = {
                showAddSheet = false
                showIkev2 = true
            },
            onTor = {
                showAddSheet = false
                repository.addTor("Tor")

                toastRes(Res.string.tor_added)
            },
            onSsh = {
                showAddSheet = false
                showSsh = true
            },
            onSniSpoof = {
                showAddSheet = false
                showSniSpoof = true
            },
            onProxyChain = {
                showAddSheet = false
                showProxyChain = true
            },
            onCrossChain = {
                showAddSheet = false
                showCrossChain = true
            },
            onSubscription = {
                showAddSheet = false
                showSubs = true
            },
        )
    }

    if (showManual) {
        ManualConfigSheet(
            onDismiss = { showManual = false },
            onSave = { link ->
                showManual = false
                repository.importText(link)
                    .onSuccess { toastRes(Res.string.servers_imported, it) }
                    .onFailure { toastImportFailure(it) }
            },
        )
    }

    if (showDnsTunnel) {
        DnsTunnelSheet(
            onDismiss = { showDnsTunnel = false },
            onSave = { name, settings ->
                showDnsTunnel = false
                repository.addDnsTunnel(settings, name)
                onServerActivated()
            },
        )
    }

    if (showMasterDns) {
        MasterDnsSheet(
            onDismiss = { showMasterDns = false },
            onSave = { name, settings ->
                showMasterDns = false
                repository.addMasterDns(settings, name)
                onServerActivated()
            },
        )
    }

    if (showOpenConnect) {
        OpenConnectSheet(
            onDismiss = { showOpenConnect = false },
            onSave = { name, settings ->
                showOpenConnect = false
                repository.addOpenConnect(settings, name)
                onServerActivated()
            },
        )
    }

    if (showIkev2) {
        Ikev2Sheet(
            onDismiss = { showIkev2 = false },
            onSave = { name, settings ->
                showIkev2 = false
                repository.addIkev2(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSsh) {
        SshSheet(
            onDismiss = { showSsh = false },
            onSave = { name, settings ->
                showSsh = false
                repository.addSsh(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSniSpoof) {
        SniSpoofSheet(
            candidates = spoofCandidates,
            onDismiss = { showSniSpoof = false },
            onSave = { name, settings ->
                showSniSpoof = false
                repository.addSniSpoof(settings, name)
                onServerActivated()
            },
        )
    }

    if (showProxyChain) {
        ProxyChainSheet(
            candidates = chainCandidates,
            onDismiss = { showProxyChain = false },
            onSave = { name, memberIds ->
                showProxyChain = false
                repository.addProxyChain(memberIds, name)
                onServerActivated()
            },
        )
    }

    if (showCrossChain) {
        CrossChainSheet(
            exits = crossExits,
            carriers = crossCarriers,
            pairError = crossPairError,
            onDismiss = { showCrossChain = false },
            onSave = { name, innerId, outerId ->
                showCrossChain = false
                repository.addCrossChain(innerId = innerId, outerId = outerId, name = name)
                onServerActivated()
            },
        )
    }

    if (showPsiphon) {
        PsiphonSheet(
            onDismiss = { showPsiphon = false },
            onSave = { name, settings ->
                showPsiphon = false
                repository.addPsiphon(settings, name)
                onServerActivated()
            },
        )
    }

    if (showSubs) {
        SubscriptionsSheet(
            repository = repository,
            onDismiss = { showSubs = false },
        )
    }

    if (showCustomJson) {
        RawJsonSheet(
            title = stringResource(Res.string.servers_add_custom),
            initial = "",
            onDismiss = { showCustomJson = false },
            onSave = { text ->
                repository.importText(text).fold(
                    onSuccess = { count ->
                        if (count > 0) {
                            toastRes(Res.string.servers_imported, count)
                            showCustomJson = false
                            null
                        } else {
                            customJsonInvalid
                        }
                    },
                    onFailure = { it.message?.take(160)?.ifBlank { null } ?: customJsonInvalid },
                )
            },
            allowList = true,
        )
    }

    if (obfuscateCandidates.isNotEmpty()) {
        val pending = obfuscateCandidates
        AlertDialog(
            onDismissRequest = { obfuscateCandidates = emptyList() },
            title = { Text(stringResource(Res.string.wg_obfuscate_title)) },
            text = { Text(stringResource(Res.string.wg_obfuscate_body, pending.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val n = repository.enableWireguardObfuscation(pending)
                    obfuscateCandidates = emptyList()
                    toastRes(Res.string.wg_obfuscate_done, n)
                }) { Text(stringResource(Res.string.wg_obfuscate_enable)) }
            },
            dismissButton = {
                TextButton(onClick = { obfuscateCandidates = emptyList() }) {
                    Text(stringResource(Res.string.wg_obfuscate_keep))
                }
            },
        )
    }

    qrTarget?.let { (qrTitle, qrText) ->
        com.zvpn.ui.components.QrDialog(
            title = qrTitle,
            text = qrText,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = { platform.copyToClipboard(qrText); toastRes(Res.string.copied) },
            onShare = { platform.shareText(qrText) },
            onDismiss = { qrTarget = null },
        )
    }

    editTarget?.let { target ->
        when {
            target.isSingBox || target.isSingBoxConfig -> {
                val source = target.source
                RawJsonSheet(
                    title = stringResource(Res.string.singbox_json_title),
                    initial = when (source) {
                        is com.zvpn.domain.config.ProfileSource.SingBox -> source.json
                        is com.zvpn.domain.config.ProfileSource.SingBoxConfig -> source.json
                        else -> ""
                    },
                    onDismiss = { editTarget = null },
                    onSave = { text ->
                        repository.updateSingBox(target.id, text).fold(
                            onSuccess = {
                                toastRes(Res.string.saved)
                                editTarget = null
                                null
                            },
                            onFailure = { it.message?.take(160)?.ifBlank { null } ?: singBoxJsonInvalid },
                        )
                    },
                    flavour = JsonFlavour.SingBox,
                )
            }
            target.isCustom -> {
                RawJsonSheet(
                    title = stringResource(Res.string.custom_json_title),
                    initial = target.rawPayload().orEmpty(),
                    onDismiss = { editTarget = null },
                    onSave = { text ->
                        repository.updateRawJson(target.id, text).fold(
                            onSuccess = {
                                toastRes(Res.string.saved)
                                editTarget = null
                                null
                            },
                            onFailure = { it.message?.take(160)?.ifBlank { null } ?: customJsonInvalid },
                        )
                    },
                )
            }

            target.isPsiphon -> {
                PsiphonSheet(
                    initial = target.psiphonSettings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->
                        repository.addPsiphon(settings, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isDnsTunnel -> {
                DnsTunnelSheet(
                    initial = target.dnsTunnelSettings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->

                        repository.addDnsTunnel(settings, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isMasterDns -> {
                MasterDnsSheet(
                    initial = target.masterDnsSettings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->
                        repository.addMasterDns(settings, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isOpenConnect -> {
                OpenConnectSheet(
                    initial = target.openConnectSettings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->
                        repository.addOpenConnect(settings, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isIkev2 -> {
                Ikev2Sheet(
                    initial = target.ikev2Settings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->
                        repository.addIkev2(settings, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isSniSpoof -> {
                SniSpoofSheet(
                    candidates = spoofCandidates,
                    initial = target.sniSpoofSettings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->
                        repository.addSniSpoof(settings, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isProxyChain -> {
                ProxyChainSheet(
                    candidates = chainCandidates,
                    initialName = target.name,
                    initialMemberIds = target.proxyChainSettings().orEmpty(),
                    onDismiss = { editTarget = null },
                    onSave = { name, memberIds ->
                        repository.addProxyChain(memberIds, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isCrossChain -> {
                val (inId, outId) = target.crossChainSettings() ?: ("" to "")
                CrossChainSheet(
                    exits = crossExits,
                    carriers = crossCarriers,
                    pairError = crossPairError,
                    initialName = target.name,
                    initialInnerId = inId,
                    initialOuterId = outId,
                    onDismiss = { editTarget = null },
                    onSave = { name, innerId, outerId ->
                        repository.addCrossChain(innerId, outerId, name, id = target.id)
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            target.isSsh -> {
                SshSheet(
                    initial = target.sshSettings(),
                    initialName = target.name,
                    onDismiss = { editTarget = null },
                    onSave = { name, settings ->
                        repository.update(
                            VpnProfile.fromSsh(settings = settings, id = target.id, addedAt = target.addedAt, name = name),
                        )
                        toastRes(Res.string.saved)
                        editTarget = null
                    },
                )
            }
            else -> {
                val parsed = remember(target.id) {
                    target.rawPayload()?.let { payload ->
                        runCatching { com.zvpn.domain.config.ConfigParser.parse(payload) }.getOrNull()
                    }
                }
                if (parsed == null) {
                    LaunchedEffect(target.id) {
                        toastRes(Res.string.edit_unsupported)
                        editTarget = null
                    }
                } else {
                    ManualConfigSheet(
                        initial = parsed,
                        onDismiss = { editTarget = null },
                        onSave = { link ->
                            val updated = runCatching {
                                VpnProfile.fromLink(
                                    link = link,
                                    id = target.id,
                                    addedAt = target.addedAt,
                                    subscriptionId = target.subscriptionId,
                                )
                            }.getOrNull()
                            if (updated == null) {
                                toastRes(Res.string.config_invalid)
                            } else {
                                repository.update(updated.copy(lastPingMs = target.lastPingMs))
                                toastRes(Res.string.saved)
                            }
                            editTarget = null
                        },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        val save = {
            repository.rename(target.id, name.trim().ifBlank { target.name })
            renameTarget = null
        }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(Res.string.servers_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = save) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    confirmDeleteInvalid?.let { count ->
        AlertDialog(
            onDismissRequest = { confirmDeleteInvalid = null },
            icon = { Icon(painterResource(Res.drawable.ic_delete), null) },
            title = { Text(stringResource(Res.string.del_invalid)) },
            text = { Text(stringResource(Res.string.del_invalid_confirm, count)) },
            confirmButton = {
                TextButton(
                    enabled = count > 0,
                    onClick = {
                        confirmDeleteInvalid = null
                        toastRes(Res.string.removed_count, repository.removeInvalid())
                    },
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteInvalid = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            icon = { Icon(painterResource(Res.drawable.ic_delete), null) },
            title = { Text(stringResource(Res.string.del_all)) },
            text = { Text(stringResource(Res.string.del_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false

                    val removed = servers.size
                    repository.removeAll { !it.isLocked }
                    toastRes(Res.string.removed_count, removed)
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (moveSelectionTo) {
        AlertDialog(
            onDismissRequest = { moveSelectionTo = false },
            title = { Text(stringResource(Res.string.groups_move)) },
            text = {
                Column {
                    listOf("" to stringResource(Res.string.groups_manual)).plus(
                        subscriptions.map { it.id to it.name },
                    ).forEach { (id, label) ->
                        TextButton(
                            onClick = {
                                val moved = selected.size
                                repository.moveToGroup(selected, id)
                                moveSelectionTo = false
                                selecting = false
                                selected = emptySet()
                                toastRes(Res.string.select_moved, moved)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveSelectionTo = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteSelection) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text(stringResource(Res.string.action_delete)) },
            text = { Text(stringResource(Res.string.select_delete_confirm, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val n = selected.size
                    repository.removeAll(selected)
                    confirmDeleteSelection = false
                    selecting = false
                    selected = emptySet()
                    toastRes(Res.string.removed_count, n)
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    moveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(stringResource(Res.string.groups_move)) },
            text = {
                Column {
                    listOf("" to stringResource(Res.string.groups_manual)).plus(
                        subscriptions.map { it.id to it.name },
                    ).forEach { (id, label) ->
                        TextButton(
                            onClick = {
                                repository.moveToGroup(target.id, id)
                                moveTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (target.subscriptionId == id) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(painterResource(Res.drawable.ic_delete), null) },
            title = { Text(stringResource(Res.string.servers_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.servers_delete_confirm_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.remove(target.id)
                    deleteTarget = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun RawJsonSheet(
    title: String,
    initial: String,
    onDismiss: () -> Unit,

    onSave: (String) -> String?,

    flavour: JsonFlavour = JsonFlavour.Xray,

    allowList: Boolean = false,
) {
    val platform = LocalPlatform.current
    var text by remember { mutableStateOf(initial) }

    val parsed = remember(text) {
        runCatching { JsonEditor.json.parseToJsonElement(com.zvpn.domain.config.Jsonc.strip(text)) }
    }
    val lines = remember(text) { text.count { it == '\n' } + 1 }

    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) { saveError = null }

    val usable = remember(text, flavour) {
        parsed.isSuccess && when (flavour) {
            JsonFlavour.Xray -> com.zvpn.domain.config.CustomConfig.looksLikeCustomJson(text) &&
                    (allowList || parsed.getOrNull() is kotlinx.serialization.json.JsonObject)
            JsonFlavour.SingBox ->
                com.zvpn.domain.config.SingBoxJson.shapeOf(text) !=
                        com.zvpn.domain.config.SingBoxJson.Shape.None
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,

        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = { parsed.getOrNull()?.let { text = JsonEditor.pretty.encodeToString(it) } },
                    enabled = parsed.isSuccess,
                    label = { Text(stringResource(Res.string.json_format)) },
                )
                AssistChip(
                    onClick = { parsed.getOrNull()?.let { text = JsonEditor.json.encodeToString(it) } },
                    enabled = parsed.isSuccess,
                    label = { Text(stringResource(Res.string.json_minify)) },
                )
                AssistChip(
                    onClick = { platform.copyToClipboard(text) },
                    label = { Text(stringResource(Res.string.action_copy)) },
                )
                AssistChip(
                    onClick = { platform.readClipboard()?.takeIf { it.isNotBlank() }?.let { text = it } },
                    label = { Text(stringResource(Res.string.json_paste)) },
                )
            }

            Text(
                text = saveError ?: parsed.fold(
                    onSuccess = {
                        if (usable) {
                            stringResource(Res.string.json_valid, lines)
                        } else when (flavour) {
                            JsonFlavour.Xray -> stringResource(Res.string.custom_json_invalid)
                            JsonFlavour.SingBox -> stringResource(Res.string.singbox_json_invalid)
                        }
                    },
                    onFailure = {
                        stringResource(Res.string.json_broken, it.message?.take(120).orEmpty())
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (usable && saveError == null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.custom_json_hint)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                isError = text.isNotBlank() && !usable,

                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Button(
                onClick = { saveError = onSave(text.trim()) },
                enabled = usable,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private enum class JsonFlavour { Xray, SingBox }

private object JsonEditor {
    val json = kotlinx.serialization.json.Json { prettyPrint = false }
    val pretty = kotlinx.serialization.json.Json { prettyPrint = true }
}

private data class OvpnPrompt(val text: String, val name: String)

/*
 * Design system used here (8-pt grid):
 *  - spacing: 4 / 8 / 12 / 16 / 24 / 32
 *  - radii:   chips = full, cards = 20, buttons / FAB = 16-20
 *  - tap targets >= 48dp
 *  - colour: neutral surfaces 90%, primary only for the one thing that matters
 *  - shadows tinted with the primary colour (never pure black)
 */

// ───────────────────────────── 1. Header actions ─────────────────────────────

/**
 * Replaces the 6 loose IconButtons in the header with a single pill that
 * holds only the 3 primary actions. Everything else lives in the overflow menu.
 */
@Composable
internal fun ServersActionBar(
    testing: Boolean,
    updating: Boolean,
    onPingTcp: () -> Unit,
    onPingReal: () -> Unit,
    onStopPing: () -> Unit,
    onSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
    overflowContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var pingMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (testing) {
                IconButton(onClick = onStopPing) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.ping_stop),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Box {
                    IconButton(
                        onClick = { pingMenu = true },
                        modifier = Modifier.tourTarget(TourTargets.SERVERS_PING),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_speed),
                            contentDescription = stringResource(Res.string.ping_test),
                        )
                    }
                    DropdownMenu(
                        expanded = pingMenu,
                        onDismissRequest = { pingMenu = false },
                        shape = MaterialTheme.shapes.largeIncreased,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.ping_tcp_all)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_speed), null) },
                            onClick = { pingMenu = false; onPingTcp() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.ping_real_all)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.ic_bolt), null) },
                            onClick = { pingMenu = false; onPingReal() },
                        )
                    }
                }
            }

            if (updating) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            IconButton(
                onClick = onSubscriptions,
                modifier = Modifier.tourTarget(TourTargets.SERVERS_SUBS),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_add_link),
                    contentDescription = stringResource(Res.string.subs_title),
                )
            }

            Box {
                IconButton(
                    onClick = { overflow = true },
                    modifier = Modifier.tourTarget(TourTargets.SERVERS_MORE),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_more_vert),
                        contentDescription = stringResource(Res.string.servers_actions),
                    )
                }
                DropdownMenu(
                    expanded = overflow,
                    onDismissRequest = { overflow = false },
                    shape = MaterialTheme.shapes.largeIncreased,
                ) {
                    overflowContent { overflow = false }
                }
            }
        }
    }
}

// ───────────────────────────── 2. Group chips ─────────────────────────────

/** Replaces GroupTab: the count becomes a small badge so the label stays the hero. */
@Composable
internal fun GroupChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh,
        label = "chipContainer",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        label = "chipContent",
    )
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .then(if (onMenu != null) Modifier.onSecondaryClick(onMenu) else Modifier),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 144.dp),
            )
            Box(
                Modifier
                    .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .background(content.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ───────────────────────────── 3. Ping progress ─────────────────────────────

/** Replaces the bare text + progress bar with a compact card and percentage. */
@Composable
internal fun PingProgressCard(
    done: Int,
    total: Int,
    real: Boolean,
    modifier: Modifier = Modifier,
) {
    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, label = "pingProgress")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (real) Res.string.ping_progress_real else Res.string.ping_progress_tcp,
                        done,
                        total,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
        }
    }
}

// ───────────────────────────── 4. Empty state ─────────────────────────────

/** Replaces EmptyState: an empty screen now has a clear next step (CTA). */
@Composable
internal fun ServersEmptyState(
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MorphingBlob(progress = 0.35f, brush = ZGradients.idle, modifier = Modifier.size(136.dp)) {
            Icon(
                painterResource(Res.drawable.ic_dns),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.servers_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.servers_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(56.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_add), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.servers_add), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onPaste,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(56.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_content_paste), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.servers_add_clipboard), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ───────────────────────────── 5. FAB + speed dial ─────────────────────────────

/** Extended FAB with a label (clearer than a bare "+") and a primary-tinted shadow. */
@Composable
internal fun AddServerFab(
    fanOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val rotation by animateFloatAsState(if (fanOpen) 45f else 0f, label = "fabRotation")
    Surface(
        shape = shape,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = modifier
            .height(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = scheme.primary.copy(alpha = 0.25f),
                spotColor = scheme.primary.copy(alpha = 0.35f),
            )
            .clip(shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(Res.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
            Text(
                stringResource(Res.string.servers_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Replaces SpeedDialAction: 48dp tap target, same animation. */
@Composable
internal fun SpeedDialItem(
    visible: Boolean,
    delayMs: Int,
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMs)) +
                androidx.compose.animation.scaleIn(
                    androidx.compose.animation.core.tween(200, delayMs),
                    initialScale = 0.6f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f),
                ),
        exit = androidx.compose.animation.fadeOut() +
                androidx.compose.animation.scaleOut(targetScale = 0.6f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 2.dp,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// ───────────────────────────── 6. Add-server sheet ─────────────────────────────

private data class SheetItem(
    val icon: DrawableResource,
    val label: StringResource,
    val onClick: () -> Unit,
    val free: Boolean = false,
)

/**
 * Replaces AddServerSheet (same parameters).
 * Top: the 3-4 most common actions as big tiles (thumb-friendly).
 * Below: advanced protocols grouped inside rounded cards instead of one long flat list.
 */
@Composable
internal fun AddServerSheetPro(
    onDismiss: () -> Unit,
    onPasteLink: () -> Unit,
    onImportFile: () -> Unit,
    onScanQr: () -> Unit,
    onScanQrImage: () -> Unit,
    onManual: () -> Unit,
    onCustom: () -> Unit,
    onPsiphon: () -> Unit,
    onDnsTunnel: () -> Unit,
    onMasterDns: () -> Unit,
    onOpenConnect: () -> Unit,
    onIkev2: () -> Unit,
    onTor: () -> Unit,
    onSsh: () -> Unit,
    onSniSpoof: () -> Unit,
    onProxyChain: () -> Unit,
    onCrossChain: () -> Unit,
    onSubscription: () -> Unit,
) {
    val supportsQr = LocalPlatform.current.supportsQrScan

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Text(
                stringResource(Res.string.servers_add),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )

            // Free hint banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FreePill()
                    Text(
                        stringResource(Res.string.add_free_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick actions
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickTile(Res.drawable.ic_content_paste, Res.string.servers_add_clipboard, onPasteLink, Modifier.weight(1f))
                if (supportsQr) {
                    QuickTile(Res.drawable.ic_qr_code_2, Res.string.servers_add_scan_qr, onScanQr, Modifier.weight(1f))
                }
                QuickTile(Res.drawable.ic_description, Res.string.servers_add_file, onImportFile, Modifier.weight(1f))
                QuickTile(Res.drawable.ic_edit, Res.string.servers_add_manual, onManual, Modifier.weight(1f))
            }

            SheetSection(
                Res.string.add_group_import,
                listOf(
                    SheetItem(Res.drawable.ic_add_link, Res.string.servers_add_scan_image, onScanQrImage),
                    SheetItem(Res.drawable.ic_description, Res.string.servers_add_custom, onCustom),
                ),
            )
            SheetSection(
                Res.string.add_group_tunnels,
                listOf(
                    SheetItem(Res.drawable.ic_bolt, Res.string.psiphon_add_title, onPsiphon, free = true),
                    SheetItem(Res.drawable.ic_lock, Res.string.tor_add_title, onTor, free = true),
                    SheetItem(Res.drawable.ic_speed, Res.string.ssh_add_title, onSsh),
                    SheetItem(Res.drawable.ic_bolt, Res.string.snispoof_add_title, onSniSpoof),
                ),
            )
            SheetSection(
                Res.string.add_group_dns,
                listOf(
                    SheetItem(Res.drawable.ic_public, Res.string.dns_tunnel_add_title, onDnsTunnel),
                    SheetItem(Res.drawable.ic_public, Res.string.master_dns_add_title, onMasterDns),
                ),
            )
            SheetSection(
                Res.string.add_group_vpn,
                listOf(
                    SheetItem(Res.drawable.ic_lock, Res.string.openconnect_add_title, onOpenConnect),
                    SheetItem(Res.drawable.ic_lock, Res.string.ikev2_add_title, onIkev2),
                ),
            )
            SheetSection(
                Res.string.add_group_chains,
                listOf(
                    SheetItem(Res.drawable.ic_add_link, Res.string.proxychain_add_title, onProxyChain),
                    SheetItem(Res.drawable.ic_add_link, Res.string.crosschain_add_title, onCrossChain),
                ),
            )
            SheetSection(
                Res.string.add_group_subscription,
                listOf(SheetItem(Res.drawable.ic_add_link, Res.string.subs_add, onSubscription)),
            )
        }
    }
}

@Composable
private fun QuickTile(
    icon: DrawableResource,
    label: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetSection(title: StringResource, items: List<SheetItem>) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                SheetRow(item)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetRow(item: SheetItem) {
    Surface(onClick = item.onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(item.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(item.label),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.free) {
                Spacer(Modifier.width(8.dp))
                FreePill()
            }
        }
    }
}

@Composable
private fun FreePill() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary) {
        Text(
            stringResource(Res.string.add_free_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}