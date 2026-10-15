package com.zvpn.ui

import com.zvpn.ui.platform.BackHandler
import com.zvpn.ui.home.HomeFabCluster
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zvpn.data.config.ConfigRepository
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.ic_bolt
import androidx.compose.foundation.clickable
import com.zvpn.shared.resources.auto_title_for
import com.zvpn.shared.resources.tour_done
import com.zvpn.shared.resources.tour_next
import com.zvpn.shared.resources.tour_skip
import com.zvpn.domain.model.AppLanguage
import com.zvpn.domain.model.AppSettings
import com.zvpn.ui.servers.ServersScreen
import com.zvpn.ui.theme.toPersonalization
import com.zvpn.ui.connection.ConnectionViewModel
import com.zvpn.ui.easteregg.SpaceScreen
import com.zvpn.ui.home.HomeScreen
import com.zvpn.ui.motion.transitionDecoration
import com.zvpn.ui.navigation.TopDestination
import com.zvpn.ui.settings.SettingsScreen
import com.zvpn.ui.update.NudgeHost
import com.zvpn.ui.vault.VaultImportHost
import com.zvpn.ui.vault.VaultScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    settings: AppSettings,
    configRepository: ConfigRepository,
    onToggleConnection: () -> Unit,

    onActiveServerChanged: () -> Unit = {},
    onImportZvx: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,

    aiBridge: com.zvpn.domain.ai.AiAppBridge? = null,
    appVersion: String = "",

    /** Current billing tier — flows into ServersScreen to control lock icons. */
    userTier: com.zvpn.domain.billing.UserTier = com.zvpn.domain.billing.UserTier.Free,
    /** Triggered when user wants to upgrade (taps locked server / "Go Premium"). */
    onUpgrade: (() -> Unit)? = null,
    /** Optional Unity Banner ad — shown for free users at bottom of server list. */
    adBanner: @Composable (() -> Unit)? = null,
    /** Triggered when user taps Sign In With Google in SettingsScreen */
    onSignInWithGoogle: (() -> Unit)? = null,
) {
    val platform = com.zvpn.ui.platform.LocalPlatform.current
    var current by rememberSaveable { mutableStateOf(TopDestination.Home) }

    var setupDone by rememberSaveable {
        mutableStateOf(settings.onboardingVersion >= com.zvpn.ui.onboarding.ONBOARDING_VERSION)
    }
    var tourStep by rememberSaveable { mutableStateOf(-1) }
    var spaceUnlocked by rememberSaveable { mutableStateOf(false) }

    var deepLinkOrigin by rememberSaveable { mutableStateOf<TopDestination?>(null) }

    var pendingSettingsPage by rememberSaveable {
        mutableStateOf(com.zvpn.ui.settings.SettingsPage.Root)
    }
    val connectionVm: ConnectionViewModel = viewModel { ConnectionViewModel() }
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val profiles by configRepository.profiles.collectAsStateWithLifecycle()
    val subscriptions by configRepository.subscriptions.collectAsStateWithLifecycle()
    val activeId by configRepository.activeId.collectAsStateWithLifecycle()

    val activeProfile = androidx.compose.runtime.remember(profiles, subscriptions, activeId) {
        activeId?.let { configRepository.profile(it) }
    }
    val autoSession by com.zvpn.core.AutoSelect.session.collectAsStateWithLifecycle()

    val lockedNote = activeProfile?.takeIf { it.isLocked }?.note

    val mtuHint = androidx.compose.runtime.remember(activeProfile) {
        val link = (activeProfile?.source as? com.zvpn.domain.config.ProfileSource.Link)?.link
        val parsed = link?.let {
            runCatching { com.zvpn.domain.config.ConfigParser.parse(it) }.getOrNull()
        }
        val host = parsed?.address?.takeIf { it.isNotBlank() } ?: activeProfile?.address.orEmpty()
        com.zvpn.data.net.MtuServerHint(
            host = host,
            overhead = parsed
                ?.let { com.zvpn.data.net.MtuOverheads.ofServer(it) }
                ?: com.zvpn.data.net.MtuOverheads.worstCase(),
        )
    }

    VaultImportHost(
        repository = configRepository,
        onImported = { current = TopDestination.Browser },
    )

    com.zvpn.ui.servers.DeepLinkImportHost(
        repository = configRepository,
        onImported = { current = TopDestination.Servers },
    )

    NudgeHost(settings = settings, onUpdateSettings = onUpdateSettings)

    BackHandler(enabled = current != TopDestination.Home) { current = TopDestination.Home }

    BackHandler(enabled = tourStep >= 0) {
        tourStep = -1
        current = TopDestination.Home
    }

    if (spaceUnlocked) {
        SpaceScreen(onExit = { spaceUnlocked = false })
        return
    }

    if (!setupDone) {
        com.zvpn.ui.onboarding.OnboardingFlow(
            settings = settings,
            onUpdate = onUpdateSettings,
            onLanguage = onLanguage,
            onStartTour = {
                onUpdateSettings {
                    it.copy(onboardingVersion = com.zvpn.ui.onboarding.ONBOARDING_VERSION)
                }
                setupDone = true
                current = TopDestination.Home
                tourStep = 0
            },
            onFinish = {
                onUpdateSettings {
                    it.copy(onboardingVersion = com.zvpn.ui.onboarding.ONBOARDING_VERSION)
                }
                setupDone = true
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        var showMyIpDialog by rememberSaveable { mutableStateOf(false) }
        var showPaywallSheet by rememberSaveable { mutableStateOf(false) }

        if (showMyIpDialog) {
            com.zvpn.ui.components.MyIpDialog(onDismiss = { showMyIpDialog = false })
        }

        if (showPaywallSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showPaywallSheet = false },
                sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                com.zvpn.ui.premium.PaywallScreen(
                    onPlanSelected = { _ ->
                        showPaywallSheet = false
                        onUpgrade?.invoke()
                    },
                    onDismiss = { showPaywallSheet = false },
                    onWatchAd = if (userTier == com.zvpn.domain.billing.UserTier.Free) ({
                        showPaywallSheet = false
                        onUpgrade?.invoke()
                    }) else null,
                )
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            com.zvpn.ui.navigation.ZedNavBar(
                style = settings.navBarStyle,
                current = current,
                onSelect = { current = it },
                onOpenPremium = { showPaywallSheet = true },
            )
        },
    ) { innerPadding ->
      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        val sign = com.zvpn.ui.motion.layoutSign()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                com.zvpn.ui.motion.screenTransition(
                    style = settings.screenTransition,
                    forward = targetState.ordinal >= initialState.ordinal,
                    direction = sign,
                    reduceMotion = settings.reduceMotion,
                ).using(SizeTransform(clip = false))
            },
            label = "top-destination",
            modifier = Modifier.fillMaxSize(),
        ) { dest ->

            val decoration = transitionDecoration(
                style = settings.screenTransition,
                forward = dest.ordinal >= current.ordinal,
                direction = sign,
                reduceMotion = settings.reduceMotion,
            )
            Box(decoration) {
            when (dest) {
                TopDestination.Home -> HomeScreen(
                    connectionVm = connectionVm,
                    activeConfigName = activeProfile?.let { p ->
                        if (p.isAutoSelect) {
                            stringResource(
                                Res.string.auto_title_for,
                                com.zvpn.ui.servers.autoSelectGroupLabel(p),
                            )
                        } else p.name
                    },
                    exitGeneration = autoSession?.takeIf { it.profileId == activeProfile?.id }?.exitGeneration ?: 0,
                    activeConfigDetail = activeProfile?.takeIf { it.isAutoSelect }?.let { p ->
                        autoSession?.takeIf { it.profileId == p.id }?.let { session ->
                            com.zvpn.ui.servers.autoSelectLiveLine(session) { id ->
                                profiles.firstOrNull { it.id == id }?.name
                            }
                        }
                    },
                    lockedNote = lockedNote,
                    localProxyAddress = if (settings.runMode == com.zvpn.domain.model.RunMode.ProxyOnly) {
                        "127.0.0.1:${com.zvpn.core.VpnManager.activeSocksPort ?: com.zvpn.domain.config.LocalProxy.SOCKS_PORT}"
                    } else null,
                    activeLocked = activeProfile?.isLocked == true,
                    reduceMotion = settings.reduceMotion,

                    showConnectionInfo = activeProfile?.isDnsTunnel != true,
                    ipApiUrl = settings.ipApiUrl,
                    delayTestUrl = settings.delayTestUrl,

                    personalization = settings.toPersonalization(),
                    connectStyle = settings.connectButtonStyle,
                    contentPadding = innerPadding,
                    onToggleConnection = onToggleConnection,

                    onBrowseConfigs = {
                        current = TopDestination.Servers
                    },
                    onOpenMap = {
                        deepLinkOrigin = current
                        pendingSettingsPage = com.zvpn.ui.settings.SettingsPage.Map
                        current = TopDestination.Settings
                    },
                    onOpenMyIp = {
                        deepLinkOrigin = current
                        pendingSettingsPage = com.zvpn.ui.settings.SettingsPage.MyIp
                        current = TopDestination.Settings
                    },
                    onSecretUnlocked = { spaceUnlocked = true },
                )
                TopDestination.Servers -> ServersScreen(
                    repository = configRepository,
                    contentPadding = innerPadding,
                    twoColumns = settings.doubleColumnDisplay,
                    showAllGroup = settings.groupAllDisplay,
                    confirmRemove = settings.confirmRemove,
                    realPingConcurrency = settings.realPingConcurrency,
                    delayTestUrl = settings.delayTestUrl,
                    autoTestAfterUpdate = settings.autoTestAfterUpdate,
                    autoRemoveInvalidAfterTest = settings.autoRemoveInvalidAfterTest,
                    autoSortAfterTest = settings.autoSortAfterTest,
                    personalization = settings.toPersonalization(),
                    userTier = userTier,
                    onUpgrade = onUpgrade ?: { showPaywallSheet = true },
                    adBanner = adBanner,
                    onServerActivated = {
                        if (settings.homeAfterSelect) current = TopDestination.Home
                        onActiveServerChanged()
                    },
                )

                TopDestination.Browser -> {
                    val connState = connectionVm.ui.collectAsStateWithLifecycle().value.state
                    val isConn = connState == com.zvpn.domain.model.ConnectionState.Connected

                    val realPrivacy = com.zvpn.ui.browser.BrowserPrivacy(
                        routedThroughVpn = isConn,
                        torCircuitActive = true,
                        dnsProtected = false,
                    )
                    com.zvpn.ui.browser.BrowserScreen(
                        contentPadding = innerPadding,
                        privacy = realPrivacy,
                    )
                }
                TopDestination.Settings -> SettingsScreen(
                    settings = settings,
                    contentPadding = innerPadding,
                    repository = configRepository,
                    onUpdate = onUpdateSettings,
                    onLanguage = onLanguage,
                    initialPage = pendingSettingsPage,
                    onDeepLinkBack = deepLinkOrigin?.let { origin ->
                        {
                            current = origin
                            deepLinkOrigin = null
                        }
                    },
                    onInitialPageConsumed = {
                        pendingSettingsPage = com.zvpn.ui.settings.SettingsPage.Root
                    },

                    serverTargets = profiles
                        .filter { it.source is com.zvpn.domain.config.ProfileSource.Link }
                        .map { it.id to it.name },
                    dnsTunnelActive = activeProfile?.isDnsBasedTunnel == true,

                    onReplayTour = {
                        current = TopDestination.Home
                        tourStep = 0
                    },

                    mtuHint = mtuHint,
                    ai = aiBridge?.let { bridge ->
                        com.zvpn.ui.settings.AiSection(
                            settings = settings.ai,
                            onUpdate = { next -> onUpdateSettings { it.copy(ai = next) } },
                            bridge = bridge,
                            onOpenUrl = { url -> platform.openUri(url) },

                            languageName = languageEnglishName(settings.language),
                            languageNative = languageNativeName(settings.language),
                            platform = "Android",
                            appVersion = appVersion,
                        )
                    },
                    onSignInWithGoogle = onSignInWithGoogle,
                )
            }
            }
        }

        val vpnStatus by com.zvpn.core.VpnManager.status.collectAsStateWithLifecycle()
        HomeFabCluster(
            visible = current == TopDestination.Home,
            showSpeedTest = vpnStatus.state == com.zvpn.domain.model.ConnectionState.Connected &&
                activeProfile?.isDnsBasedTunnel != true,
            reduceMotion = settings.reduceMotion,
            atEnd = settings.speedFabAtEnd,
            yFraction = settings.speedFabY,
            onMove = { end, y -> onUpdateSettings { it.copy(speedFabAtEnd = end, speedFabY = y) } },
            onOpenMap = {
                deepLinkOrigin = current
                pendingSettingsPage = com.zvpn.ui.settings.SettingsPage.Map
                current = TopDestination.Settings
            },
            onOpenMyIp = {
                deepLinkOrigin = current
                pendingSettingsPage = com.zvpn.ui.settings.SettingsPage.MyIp
                current = TopDestination.Settings
            },
            onOpenSpeedTest = {
                deepLinkOrigin = current
                pendingSettingsPage = com.zvpn.ui.settings.SettingsPage.SpeedTest
                current = TopDestination.Settings
            },
            modifier = Modifier.padding(innerPadding),
        )
      }
    }

    val tourSteps = com.zvpn.ui.onboarding.rememberTourSteps()
    if (tourStep in tourSteps.indices) {
        val step = tourSteps[tourStep]

        val finish = {
            tourStep = -1
            current = TopDestination.Home
        }
        val advance = { if (tourStep >= tourSteps.lastIndex) finish() else tourStep += 1 }
        androidx.compose.runtime.LaunchedEffect(tourStep) {
            current = when (step.destination) {
                com.zvpn.ui.onboarding.TourDestination.Home -> TopDestination.Home
                com.zvpn.ui.onboarding.TourDestination.Servers -> TopDestination.Servers
                com.zvpn.ui.onboarding.TourDestination.Settings -> TopDestination.Settings
            }
        }
        com.zvpn.ui.onboarding.TourOverlay(
            step = step,
            index = tourStep,
            total = tourSteps.size,
            nextLabel = if (tourStep == tourSteps.lastIndex) {
                stringResource(Res.string.tour_done)
            } else {
                stringResource(Res.string.tour_next)
            },
            skipLabel = stringResource(Res.string.tour_skip),
            onNext = advance,
            onSkip = finish,

            onTargetMissing = advance,
        )
    }
    }
}

private fun languageEnglishName(language: AppLanguage): String = when (language) {
    AppLanguage.Arabic -> "Arabic"
    AppLanguage.Persian -> "Persian"
    AppLanguage.Spanish -> "Spanish"
    AppLanguage.Chinese -> "Chinese"
    AppLanguage.French -> "French"
    AppLanguage.Russian -> "Russian"
    AppLanguage.German -> "German"
    AppLanguage.English -> "English"

    AppLanguage.System -> "the language the user writes in"
}

private fun languageNativeName(language: AppLanguage): String = when (language) {
    AppLanguage.Arabic -> "العربية"
    AppLanguage.Persian -> "فارسی"
    AppLanguage.Spanish -> "Español"
    AppLanguage.Chinese -> "中文"
    AppLanguage.French -> "Français"
    AppLanguage.Russian -> "Русский"
    AppLanguage.German -> "Deutsch"
    AppLanguage.English -> "English"
    AppLanguage.System -> ""
}
