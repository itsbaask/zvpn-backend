@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zvpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zvpn.ads.UnityAdsManager
import com.zvpn.billing.BillingManager
import com.zvpn.core.AndroidVpn
import com.zvpn.core.StartPlanner
import com.zvpn.core.VaultImportBus
import com.zvpn.core.VpnManager
import com.zvpn.core.platform.LocaleManager
import com.zvpn.data.config.ConfigRepository
import com.zvpn.data.settings.SettingsRepository
import com.zvpn.domain.ads.PointsManager
import com.zvpn.domain.billing.UserTier
import com.zvpn.domain.model.ConnectionState
import com.zvpn.domain.model.ThemeMode
import com.zvpn.platform.AndroidKeyValueStore
import com.zvpn.platform.AndroidPlatform
import com.zvpn.ui.MainScaffold
import com.zvpn.ui.platform.FilePick
import com.zvpn.ui.platform.LocalPlatform
import com.zvpn.ui.theme.ZVpnTheme
import kotlinx.coroutines.CompletableDeferred


class MainActivity : ComponentActivity() {
    private val configRepository: ConfigRepository
        get() = (application as ZVpnApp).container.configRepository

    private val settingsRepository: SettingsRepository
        get() = (application as ZVpnApp).container.settingsRepository

    private var pendingStart: StartPlanner.Plan.Start? = null

    private val planner by lazy { StartPlanner(this) }

    private var planning = false

    /** BillingManager handles Google Play subscriptions + ad-reward tier escalation */
    private val billingManager by lazy { BillingManager(this) }

    /** Count how many VPN sessions have ended — used to pace interstitial ads */
    private var vpnDisconnectCount = 0

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val start = pendingStart
            pendingStart = null
            if (result.resultCode == RESULT_OK && start != null) {
                AndroidVpn.start(this, start.configJson, start.remark, start.socksPort, start.kind)
            } else if (result.resultCode != RESULT_OK) {
                toast(getString(R.string.vpn_permission_denied))
            }
        }

    private var pendingIkev2Remark: String? = null
    private val ikev2ConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val remark = pendingIkev2Remark
            pendingIkev2Remark = null
            if (result.resultCode == RESULT_OK && remark != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        com.zvpn.core.Ikev2Controller.startProvisioned(this@MainActivity, remark)
                    }
                }
            } else {
                com.zvpn.core.Ikev2Controller.forgetProvisionedProfile(this)
                VpnManager.onDisconnected()
                if (result.resultCode != RESULT_OK) toast(getString(R.string.vpn_permission_denied))
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val openZvxLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            offerLockedConfig(uri)
        }

    private fun importZvx() {
        openZvxLauncher.launch(arrayOf("*/*"))
    }

    private var filePickPending: CompletableDeferred<FilePick?>? = null
    private val filePickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val pending = filePickPending
            filePickPending = null
            if (uri == null) { pending?.complete(null); return@registerForActivityResult }
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            pending?.complete(if (bytes != null) FilePick(name, bytes) else null)
        }

    private var qrScanPending: ((String?) -> Unit)? = null
    private val qrScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = qrScanPending
            qrScanPending = null
            pending?.invoke(
                result.data?.getStringExtra(com.zvpn.qr.QrScanActivity.EXTRA_RESULT)
                    ?.takeIf { result.resultCode == RESULT_OK },
            )
        }

    private var imagePickPending: CompletableDeferred<ByteArray?>? = null
    private val imagePickLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val pending = imagePickPending
            imagePickPending = null
            pending?.complete(
                uri?.let {
                    runCatching { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }
                        .getOrNull()
                },
            )
        }

    private suspend fun pickImage(): ByteArray? {
        imagePickPending?.complete(null)
        val deferred = CompletableDeferred<ByteArray?>()
        imagePickPending = deferred
        imagePickLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
        return deferred.await()
    }

    private fun scanQr(onResult: (String?) -> Unit) {
        qrScanPending?.invoke(null)
        qrScanPending = onResult
        qrScanLauncher.launch(Intent(this, com.zvpn.qr.QrScanActivity::class.java))
    }

    private suspend fun pickFile(): FilePick? {
        filePickPending?.complete(null)
        val deferred = CompletableDeferred<FilePick?>()
        filePickPending = deferred
        filePickLauncher.launch(arrayOf("*/*"))
        return deferred.await()
    }

    private var certInstallPending: CompletableDeferred<com.zvpn.ui.servers.CertInstallResult>? = null
    private val certInstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = certInstallPending
            certInstallPending = null
            pending?.complete(
                if (result.resultCode == RESULT_OK) {
                    com.zvpn.ui.servers.CertInstallResult.Installed
                } else {
                    com.zvpn.ui.servers.CertInstallResult.Cancelled
                }
            )
        }

    private suspend fun installCredential(
        bytes: ByteArray,
        suggestedName: String,
    ): com.zvpn.ui.servers.CertInstallResult {
        certInstallPending?.complete(com.zvpn.ui.servers.CertInstallResult.Cancelled)
        val deferred = CompletableDeferred<com.zvpn.ui.servers.CertInstallResult>()
        certInstallPending = deferred
        val intent = android.security.KeyChain.createInstallIntent().apply {
            putExtra(android.security.KeyChain.EXTRA_PKCS12, bytes)
            if (suggestedName.isNotBlank()) {
                putExtra(android.security.KeyChain.EXTRA_NAME, suggestedName)
            }
        }
        return runCatching { certInstallLauncher.launch(intent); deferred.await() }
            .getOrElse {
                certInstallPending = null
                com.zvpn.ui.servers.CertInstallResult.Unsupported
            }
    }

    private val androidPlatform by lazy { AndroidPlatform(this, ::pickFile, ::scanQr, ::pickImage) }

    override fun attachBaseContext(newBase: Context) {
        val tag = SettingsRepository.readLanguageTag(newBase)
        super.attachBaseContext(LocaleManager.wrap(newBase, tag))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun onResume() {
        super.onResume()
        runCatching { com.zvpn.core.Ikev2Controller.resync(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDeepLink(intent)) handleIncomingZvx(intent)
        handleConnectRequest(intent)
    }

    private fun handleDeepLink(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        val scheme = data.scheme?.lowercase() ?: return false
        if (scheme !in com.zvpn.domain.config.DeepLinkParser.SCHEMES) return false
        val request = com.zvpn.domain.config.DeepLinkParser.parse(intent.dataString.orEmpty())
        if (request == null) {
            toast(getString(R.string.config_invalid))
        } else {
            com.zvpn.core.DeepLinkBus.request(request)
        }
        return true
    }

    private fun handleConnectRequest(intent: Intent?) {
        if (intent?.action != com.zvpn.core.VpnNotifications.ACTION_CONNECT) return

        intent.action = null
        if (VpnManager.status.value.state.isActive) return
        toggleConnection()
    }

    private fun handleIncomingZvx(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java,
            )
            else -> null
        } ?: return

        offerLockedConfig(uri)
    }

    private fun offerLockedConfig(uri: Uri) {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            toast(getString(R.string.zvx_invalid))
            return
        }
        val peeked = runCatching { configRepository.peekLocked(bytes) }
        val meta = peeked.getOrNull()
        if (meta == null) {
            val legacy = peeked.exceptionOrNull() is com.zvpn.crypto.ZvxLegacyException
            toast(getString(if (legacy) R.string.zvx_legacy else R.string.zvx_invalid))
            return
        }
        if (meta.isExpired) {
            toast(getString(R.string.zvx_expired))
            return
        }
        VaultImportBus.request(bytes, meta)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.zvpn.core.Ikev2CertBridgeAndroid.install(this)
        com.zvpn.core.CertImportAndroid.installer = ::installCredential
        com.zvpn.core.CertImportAndroid.install()
        maybeRequestNotificationPermission()

        // ── Monetization init ────────────────────────────────────────────────
        // 1. Points system (persisted via KeyValueStore)
        PointsManager.init(AndroidKeyValueStore(this, "zvpn_points"))

        // 2. Google Play Billing (subscriptions)
        billingManager.init()

        // 3. Unity Ads — init once, then update on tier changes
        val deviceId = com.zvpn.platform.DeviceIdentity.id.ifBlank { "anonymous" }
        val initialTier = billingManager.userTier.value
        val initiallyPro = initialTier == UserTier.Premium || initialTier == UserTier.PremiumPlus
        UnityAdsManager.init(
            context = applicationContext,
            userId = deviceId,
            isProUser = initiallyPro,
        )
        // Track tier changes → update ads + reward expiry
        lifecycleScope.launch {
            billingManager.userTier.collect { tier ->
                val isPro = tier == UserTier.Premium || tier == UserTier.PremiumPlus
                UnityAdsManager.updateUser(userId = deviceId, isProUser = isPro)
            }
        }

        // 4. Check reward expiry every 60 seconds
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                billingManager.checkRewardExpiry()
            }
        }

        // 5. Consume points every 60 s while VPN is connected
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                val status = VpnManager.status.value
                if (status.state == ConnectionState.Connected) {
                    val tier = billingManager.userTier.value
                    PointsManager.consumePerMinute(tier.label)
                }
            }
        }

        // 6. Show interstitial on every 2nd VPN disconnect (free users only)
        lifecycleScope.launch {
            VpnManager.status.collect { status ->
                if (status.state == ConnectionState.Idle || status.state == ConnectionState.Error) {
                    val tier = billingManager.userTier.value
                    val isPro = tier == UserTier.Premium || tier == UserTier.PremiumPlus
                    if (!isPro && !PointsManager.isAdRewardActive) {
                        vpnDisconnectCount++
                        if (vpnDisconnectCount % 2 == 0) {
                            UnityAdsManager.showInterstitial(
                                activity = this@MainActivity,
                                onComplete = { },
                                onFailed = { },
                            )
                        }
                    }
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────


        if (savedInstanceState == null) {
            if (!handleDeepLink(intent)) handleIncomingZvx(intent)
        }
        handleConnectRequest(intent)
        val repo = (application as ZVpnApp).container.settingsRepository

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val langTag = settings.language.tag ?: resources.configuration.locales[0].language
            ZVpnTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                accentColor = settings.accentColor,
                amoledBlack = settings.amoledBlack,
                languageTag = langTag,
                fontScale = settings.uiFontScale.scale,
            ) {
                CompositionLocalProvider(LocalPlatform provides androidPlatform) {
                    val aiBridge = androidx.compose.runtime.remember {
                        com.zvpn.ai.AndroidAiBridge.create(
                            repository = configRepository,
                            settings = repo,
                            toggleConnection = ::toggleConnection,
                            mtuHint = {
                                val active = configRepository.activeProfile()
                                val link = (active?.source as? com.zvpn.domain.config.ProfileSource.Link)?.link
                                val parsed = link?.let {
                                    runCatching { com.zvpn.domain.config.ConfigParser.parse(it) }.getOrNull()
                                }
                                val host = parsed?.address?.takeIf { it.isNotBlank() }
                                    ?: active?.address.orEmpty()
                                if (host.isBlank()) {
                                    null
                                } else {
                                    com.zvpn.data.net.MtuServerHint(
                                        host = host,
                                        overhead = parsed
                                            ?.let { com.zvpn.data.net.MtuOverheads.ofServer(it) }
                                            ?: com.zvpn.data.net.MtuOverheads.worstCase(),
                                    )
                                }
                            },
                        )
                    }
                    val currentTier by billingManager.userTier.collectAsStateWithLifecycle()
                    val isPremiumUser = currentTier == UserTier.Premium || currentTier == UserTier.PremiumPlus
                    MainScaffold(
                        settings = settings,
                        configRepository = configRepository,
                        onToggleConnection = ::toggleConnection,
                        onActiveServerChanged = ::switchActiveServer,
                        onImportZvx = ::importZvx,
                        onUpdateSettings = repo::update,
                        onLanguage = { lang ->
                            repo.update { it.copy(language = lang) }
                            recreate()
                        },
                        aiBridge = aiBridge,
                        appVersion = com.zvpn.BuildConfig.VERSION_NAME,
                        userTier = currentTier,
                        onUpgrade = { /* AdRewardHost handles billing flow */ },
                        adBanner = if (!isPremiumUser) ({
                            com.zvpn.ads.UnityBannerAdView(
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }) else null,
                    )

                    // ── Ad / Paywall global overlay ──────────────────────────
                    val adHostState = com.zvpn.ui.ads.rememberAdRewardHostState()
                    com.zvpn.ui.ads.AdRewardHost(
                        state = adHostState,
                        billingManager = billingManager,
                        onShareReferral = {
                            val code = com.zvpn.domain.ads.PointsManager.referralCode.value
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join ZVPN — Get 50 Free Points!")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "🚀 Join ZVPN and get 50 FREE points instantly!\n" +
                                    "Use my code: $code\n" +
                                    "Download: https://play.google.com/store/apps/details?id=com.zvpn"
                                )
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share ZVPN"))
                        },
                        onRedeemCode = { code ->
                            val success = com.zvpn.domain.ads.PointsManager.redeemReferralCode(code)
                            if (success) {
                                Toast.makeText(this@MainActivity, "✅ +50 points added!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "❌ Invalid or already used code", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    // ────────────────────────────────────────────────────────


                    com.zvpn.ui.OpenConnectAuthDialog()
                    com.zvpn.ui.OpenConnectCertTrustDialog(
                        onRemember = { hash ->
                            configRepository.activeProfile()?.let {
                                configRepository.rememberOpenConnectCert(it.id, hash)
                            }
                        },
                    )
                    com.zvpn.ui.OpenConnectSsoDialog()
                }
            }
        }
    }


    private val switchStopTimeoutMs = 8_000L

    private val switchSettleMs = 350L

    private fun switchActiveServer() {
        if (!VpnManager.status.value.state.isActive) return
        lifecycleScope.launch {
            toggleConnection()
            val stopped = withTimeoutOrNull(switchStopTimeoutMs) {
                VpnManager.status.first { !it.state.isActive && !it.state.isTransitioning }
                true
            } != null
            if (!stopped) {
                toast(getString(R.string.switch_server_timeout))
                return@launch
            }

            delay(switchSettleMs)
            toggleConnection()
        }
    }

    private fun toggleConnection() {
        val state = VpnManager.status.value.state
        if (state.isActive || state.isTransitioning) {
            if (com.zvpn.core.Ikev2Controller.isActive) {
                com.zvpn.core.Ikev2Controller.stop(this)
                return
            }
            AndroidVpn.stop(this)
            return
        }
        if (planning) return
        val profile = configRepository.activeProfile()
        if (profile == null) {
            toast(getString(R.string.select_config_first))
            return
        }
        planning = true
        lifecycleScope.launch {
            val plan = try {
                withContext(Dispatchers.IO) { planner.plan(profile) }
            } finally {
                planning = false
            }
            when (plan) {
                is StartPlanner.Plan.Ikev2 -> startIkev2(plan.remark, plan.profile)
                is StartPlanner.Plan.Failure -> toast(plan.message)
                is StartPlanner.Plan.Start -> {
                    if (plan.proxyOnly) {
                        AndroidVpn.start(this@MainActivity, plan.configJson, plan.remark, plan.socksPort, plan.kind, proxyOnly = true)
                        return@launch
                    }
                    val consent = VpnService.prepare(this@MainActivity)
                    if (consent != null) {
                        pendingStart = plan
                        vpnPermissionLauncher.launch(consent)
                    } else {
                        AndroidVpn.start(this@MainActivity, plan.configJson, plan.remark, plan.socksPort, plan.kind)
                    }
                }
            }
        }
    }

    private fun startIkev2(remark: String, ikev2: com.zvpn.domain.config.Ikev2Profile) {
        val controller = com.zvpn.core.Ikev2Controller
        if (!controller.isSupportedSdk) {
            toast(getString(R.string.ikev2_requires_android11))
            return
        }
        if (!controller.isSupported(this)) {
            toast(getString(R.string.ikev2_requires_ipsec))
            return
        }

        if (VpnManager.status.value.state.isActive) AndroidVpn.stop(this)
        VpnManager.onStarting(remark)

        controller.note("connect requested for $remark (server=${ikev2.server}, auth=${ikev2.effectiveAuth})")
        lifecycleScope.launch(Dispatchers.IO) {
            val consent = try {
                controller.provision(this@MainActivity, ikev2)
            } catch (e: com.zvpn.core.Ikev2Exception) {
                controller.note("provision refused", e.cause ?: e)
                withContext(Dispatchers.Main) { VpnManager.onError(getString(e.messageRes)) }
                return@launch
            } catch (e: Exception) {
                controller.note("provision threw", e)
                withContext(Dispatchers.Main) {
                    VpnManager.onError(e.message ?: getString(R.string.ikev2_failed_generic))
                }
                return@launch
            }
            if (consent != null) {
                withContext(Dispatchers.Main) {
                    pendingIkev2Remark = remark
                    ikev2ConsentLauncher.launch(consent)
                }
            } else {
                controller.startProvisioned(this@MainActivity, remark)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { billingManager.destroy() }
    }
}
