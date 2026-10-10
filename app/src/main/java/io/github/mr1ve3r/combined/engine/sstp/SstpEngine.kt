package io.github.mr1ve3r.combined.engine.sstp

import android.os.ParcelFileDescriptor
import io.github.mr1ve3r.combined.core.trust.CertificateFingerprint
import io.github.mr1ve3r.combined.core.trust.CertificateSummary
import io.github.mr1ve3r.combined.core.trust.CertificateValidator
import io.github.mr1ve3r.combined.core.trust.PreflightProblem
import io.github.mr1ve3r.combined.core.trust.PreflightReport
import io.github.mr1ve3r.combined.core.trust.TrustManagerFactoryProvider
import io.github.mr1ve3r.combined.core.trust.TrustPreflight
import io.github.mr1ve3r.combined.engine.EngineError
import io.github.mr1ve3r.combined.engine.EngineException
import io.github.mr1ve3r.combined.engine.EngineLogEvent
import io.github.mr1ve3r.combined.engine.EngineProfile
import io.github.mr1ve3r.combined.engine.EngineState
import io.github.mr1ve3r.combined.engine.LogLevel
import io.github.mr1ve3r.combined.engine.PppAuthMethod
import io.github.mr1ve3r.combined.engine.Protocol
import io.github.mr1ve3r.combined.engine.Route
import io.github.mr1ve3r.combined.engine.SocketProtector
import io.github.mr1ve3r.combined.engine.TrustPolicy
import io.github.mr1ve3r.combined.engine.TunnelParams
import io.github.mr1ve3r.combined.engine.VpnEngine
import io.github.mr1ve3r.combined.engine.sstp.client.PPP_AUTH_TIMEOUT
import io.github.mr1ve3r.combined.engine.sstp.client.SSTP_REQUEST_TIMEOUT
import io.github.mr1ve3r.combined.engine.sstp.client.SstpClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.ConfigClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.IpcpClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.Ipv6cpClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.LcpClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.PPP_NEGOTIATION_TIMEOUT
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.PppClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.auth.ChapMsChapV2Client
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.auth.EapMsAuthClient
import io.github.mr1ve3r.combined.engine.sstp.client.ppp.auth.PapClient
import io.github.mr1ve3r.combined.engine.sstp.io.IncomingManager
import io.github.mr1ve3r.combined.engine.sstp.io.OutgoingManager
import io.github.mr1ve3r.combined.engine.sstp.terminal.IpTerminal
import io.github.mr1ve3r.combined.engine.sstp.terminal.SslTerminal
import io.github.mr1ve3r.combined.engine.sstp.unit.sstp.SSTP_MESSAGE_TYPE_CALL_ABORT
import io.github.mr1ve3r.combined.engine.sstp.unit.sstp.SSTP_MESSAGE_TYPE_CALL_DISCONNECT
import io.github.mr1ve3r.combined.engine.sstp.unit.sstp.SSTP_MESSAGE_TYPE_CALL_DISCONNECT_ACK
import java.net.Inet4Address
import java.net.InetAddress
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SstpEngine internal constructor(
    private val certificates: CertificateSource,
    private val allowInsecureTrust: Boolean,
    private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
    private val terminalFactory: TerminalFactory,
) : VpnEngine {
    constructor(
        certificates: CertificateSource = CertificateSource.EMPTY,
        allowInsecureTrust: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(certificates, allowInsecureTrust, dispatcher, clock, TerminalFactory.DEFAULT)

    private val stateFlow = MutableStateFlow<EngineState>(EngineState.Idle)
    private val eventFlow =
        MutableSharedFlow<EngineLogEvent>(
            replay = 0,
            extraBufferCapacity = EVENT_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val state: StateFlow<EngineState> = stateFlow.asStateFlow()
    override val events: SharedFlow<EngineLogEvent> = eventFlow.asSharedFlow()

    private val lock = Any()
    private val disconnectRequested = AtomicBoolean(false)

    @Volatile
    private var redactor: Redactor = Redactor.NOTHING

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var bridge: SstpBridge? = null

    @Volatile
    private var terminal: SslTerminal? = null

    @Volatile
    private var sstpClient: SstpClient? = null

    @Volatile
    private var incomingManager: IncomingManager? = null

    @Volatile
    private var outgoingManager: OutgoingManager? = null

    @Volatile
    private var pppClient: PppClient? = null

    @Volatile
    private var ipTerminal: IpTerminal? = null

    @Volatile
    private var negotiated: TunnelParams? = null

    private var watchdog: Job? = null

    override suspend fun connect(profile: EngineProfile, protector: SocketProtector): TunnelParams {
        require(profile is EngineProfile.Sstp) {
            "SstpEngine cannot run a ${profile.javaClass.simpleName} profile"
        }

        val config = SstpEngineConfig.of(profile)
        redactor = Redactor.of(config)
        if (config.authMethods.isEmpty()) {
            val error = EngineError.Internal("The profile offers no PPP authentication method")
            fail(error)
            throw EngineException(error)
        }

        val trustManager = prepareTrust(config)

        val session = SstpSessionState(config.mru)
        val mailbox = ControlMailbox()
        val engineScope = CoroutineScope(SupervisorJob() + dispatcher + failureHandler(mailbox))
        val wiring = SstpBridge(config, session, mailbox, engineScope)
        synchronized(lock) {
            scope = engineScope
            bridge = wiring
        }

        try {
            openTransport(wiring, protector, trustManager)
            negotiate(wiring)
            val params = buildTunnelParams(wiring)
            negotiated = params
            watchAfterNegotiation(wiring)
            stateFlow.value = EngineState.Connecting(STAGE_AWAITING_TUN)
            return params
        } catch (e: EngineException) {
            reportAndClose(e.error)
            throw e
        } catch (e: Exception) {
            val error = EngineError.Internal("${e.javaClass.simpleName}: ${e.message}")
            reportAndClose(error)
            throw EngineException(error, e)
        }
    }

    override fun attachTun(fd: ParcelFileDescriptor) {
        val wiring = bridge ?: error("attachTun() before connect()")
        val params = negotiated ?: error("attachTun() before connect() returned")

        val tun = IpTerminal.of(fd, wiring.config.mtu)
        synchronized(lock) { ipTerminal = tun }
        wiring.ipTerminal = tun

        OutgoingManager(wiring, tun).also {
            it.launchJobMain()
            outgoingManager = it
        }

        stateFlow.value = EngineState.Connected(params, clock())
        log(LogLevel.INFO, "TUN attached; packets are flowing")
    }

    override suspend fun disconnect() {
        if (!disconnectRequested.compareAndSet(false, true)) return

        withContext(dispatcher) {
            withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
                runCatching { sstpClient?.sendLastPacket(SSTP_MESSAGE_TYPE_CALL_DISCONNECT) }
            }

            closeEverything()
        }
        finishTerminal(EngineState.Disconnected)
    }

    private suspend fun prepareTrust(config: SstpEngineConfig): X509TrustManager {
        val policy =
            TrustManagerFactoryProvider.effectivePolicy(config.trustPolicy, allowInsecureTrust) { from, to ->
                log(LogLevel.WARN, "This build does not offer $from; the profile is connecting with $to instead")
            }

        val preflightIds = (config.trustedCertificateIds + config.pinnedFingerprints).distinct()
        val wholeStore = if (CertificateValidator.consultsWholeStore(policy)) certificates.allSummaries() else emptyMap()
        val report =
            TrustPreflight.check(
                policy = policy,
                selectedCertificateIds = config.trustedCertificateIds,
                availableCertificates = certificates.summariesFor(preflightIds) + wholeStore,
                pinnedFingerprints = config.pinnedFingerprints,
                now = clock(),
                storeSize = if (CertificateValidator.consultsWholeStore(policy)) wholeStore.size else Int.MAX_VALUE,
            )
        reportPreflight(report)

        val selected =
            when (policy) {
                TrustPolicy.SYSTEM, TrustPolicy.PIN_LEAF, TrustPolicy.INSECURE -> emptyList()
                TrustPolicy.STORE_AUTO -> certificates.allCertificates()
                TrustPolicy.SYSTEM_PLUS_CUSTOM, TrustPolicy.CUSTOM_ONLY ->
                    certificates.certificatesFor(config.trustedCertificateIds)
            }
        logTrustBasis(policy, selected, config.pinnedFingerprints)

        return TrustManagerFactoryProvider.create(
            policy = policy,
            customCerts = selected,
            pinnedFingerprints = config.pinnedFingerprints,
            allowInsecure = allowInsecureTrust,
        )
    }

    private fun logTrustBasis(policy: TrustPolicy, anchors: List<X509Certificate>, pins: Set<String>) {
        when (policy) {
            TrustPolicy.SYSTEM ->
                log(
                    LogLevel.INFO,
                    "Trust: SYSTEM -- the chain is checked against the platform's CAs only; " +
                        "certificates stored in this app take no part",
                )

            TrustPolicy.PIN_LEAF -> {
                log(LogLevel.INFO, "Trust: PIN_LEAF -- the certificate the server presents first must match one of ${pins.size} pin(s):")
                pins.forEach { pin ->
                    log(LogLevel.INFO, "  pin ${CertificateFingerprint.formatForDisplay(CertificateFingerprint.normalise(pin))}")
                }
            }

            TrustPolicy.STORE_AUTO -> {
                log(
                    LogLevel.INFO,
                    "Trust: STORE_AUTO -- no certificate was picked for this profile, so every one of the " +
                        "${anchors.size} certificate(s) in this app may anchor the chain, and every one is also " +
                        "offered as a link in it. The one that vouches is named once the handshake succeeds.",
                )
                anchors.forEach(::logAnchor)
            }

            else -> {
                log(LogLevel.INFO, "Trust: $policy -- the chain is anchored on ${anchors.size} certificate(s) from this app:")
                anchors.forEach(::logAnchor)
            }
        }
    }

    private fun logAnchor(anchor: X509Certificate) {
        val summary = CertificateSummary.of(anchor)
        log(
            LogLevel.INFO,
            "  anchor ${if (summary.isCa) "CA" else "not a CA"}: subject=${summary.subjectDn} " +
                "sha256=${CertificateFingerprint.formatForDisplay(summary.sha256Fingerprint)}",
        )
    }

    private fun reportPreflight(report: PreflightReport) {
        report.confirmations.forEach { log(LogLevel.WARN, "Trust pre-flight: ${describe(it)}") }

        if (!report.canConnect) {
            val blocking = report.blocking.joinToString { it.messageKey }
            val error = EngineError.CertificateRejected(null, "The profile cannot be trusted as configured: $blocking")
            fail(error)
            throw EngineException(error)
        }
    }

    private fun describe(problem: PreflightProblem): String = when (problem) {
        is PreflightProblem.CertificatesMissing ->
            "the profile refers to ${problem.ids.size} certificate(s) the store no longer holds"

        is PreflightProblem.NoCertificatesSelected ->
            "${problem.policy} needs at least one certificate and the profile selects none"

        PreflightProblem.NoPinsConfigured ->
            "PIN_LEAF needs at least one fingerprint and the profile pins none"

        PreflightProblem.StoreIsEmpty ->
            "STORE_AUTO anchors on the certificates stored in this app and there are none; " +
                "import the server's certificate authority first"

        is PreflightProblem.WholeStoreIsTrusted ->
            "no certificate was picked for this profile, so any of the ${problem.certificateCount} " +
                "certificate(s) stored in this app may vouch for this server"

        is PreflightProblem.CertificatesIgnoredByPolicy ->
            "the ${problem.ids.size} certificate(s) this profile selects are not consulted under ${problem.policy}; " +
                "choose SYSTEM_PLUS_CUSTOM or CUSTOM_ONLY to verify against them"

        is PreflightProblem.PinnedCertificateIsCa ->
            "the pinned certificate ${problem.subjectCn ?: problem.id} is a CA, and a pin is compared against the " +
                "certificate the server presents; pin the server's own certificate, or anchor on this one with CUSTOM_ONLY"

        is PreflightProblem.AnchorIsNotACertificateAuthority ->
            "${problem.subjectCn ?: problem.id} is not a CA and cannot anchor a chain; pin it with PIN_LEAF instead"

        is PreflightProblem.CertificateExpired ->
            "the certificate ${problem.subjectCn ?: problem.id} expired"
    }

    private suspend fun openTransport(wiring: SstpBridge, protector: SocketProtector, trustManager: X509TrustManager) {
        stateFlow.value = EngineState.Connecting(STAGE_TRANSPORT)
        val ssl =
            terminalFactory.create(
                config = wiring.config,
                protector = protector,
                trustManager = trustManager,
                log = { level, message -> log(level, message) },
            )
        terminal = ssl

        withContext(dispatcher) { ssl.establish(wiring.state.guid) }
        wiring.transport = ssl
    }

    private suspend fun negotiate(wiring: SstpBridge) {
        stateFlow.value = EngineState.Connecting(STAGE_SSTP)
        val incoming = IncomingManager(wiring).also { it.launchJobMain() }
        incomingManager = incoming

        val sstp = SstpClient(wiring)
        sstpClient = sstp
        incoming.registerMailbox(sstp)
        sstp.launchJobRequest()
        expectProceeded(wiring, Where.SSTP_REQUEST, SSTP_REQUEST_TIMEOUT)
        sstp.launchJobControl()

        PppClient(wiring).also {
            pppClient = it
            incoming.registerMailbox(it)
            it.launchJobControl()
        }

        stateFlow.value = EngineState.Connecting(STAGE_PPP)
        LcpClient(wiring).also {
            incoming.registerMailbox(it)
            it.launchJobNegotiation()
            expectProceeded(wiring, Where.LCP, PPP_NEGOTIATION_TIMEOUT)
            incoming.unregisterMailbox(it)
        }

        authenticate(wiring, incoming)

        sstp.sendCallConnected()

        stateFlow.value = EngineState.Connecting(STAGE_IPCP)
        if (wiring.config.ipv4Enabled) {
            negotiateConfig(wiring, incoming, IpcpClient(wiring), Where.IPCP)
        }

        if (wiring.config.ipv6Enabled) {
            negotiateConfig(wiring, incoming, Ipv6cpClient(wiring), Where.IPV6CP)
        }
    }

    private suspend fun <T : ConfigClient<*>> negotiateConfig(wiring: SstpBridge, incoming: IncomingManager, client: T, where: Where) {
        incoming.registerMailbox(client)
        client.launchJobNegotiation()
        expectProceeded(wiring, where, PPP_NEGOTIATION_TIMEOUT)
        incoming.unregisterMailbox(client)
    }

    private suspend fun authenticate(wiring: SstpBridge, incoming: IncomingManager) {
        stateFlow.value = EngineState.Connecting(STAGE_AUTH)

        when (wiring.state.currentAuth) {
            PppAuthMethod.PAP -> PapClient(wiring).also {
                incoming.registerMailbox(it)
                it.launchJobAuth()
                expectProceeded(wiring, Where.PAP, PPP_AUTH_TIMEOUT)
                incoming.unregisterMailbox(it)
            }

            PppAuthMethod.MSCHAPV2, PppAuthMethod.CHAP -> ChapMsChapV2Client(wiring).also {
                incoming.registerMailbox(it)
                it.launchJobAuth()
                expectProceeded(wiring, Where.CHAP, PPP_AUTH_TIMEOUT)
            }

            PppAuthMethod.EAP_MSCHAPV2 -> EapMsAuthClient(wiring).also {
                incoming.registerMailbox(it)
                it.launchJobAuth()
                expectProceeded(wiring, Where.EAP, PPP_AUTH_TIMEOUT)
            }

            null -> throw EngineException(
                EngineError.PppNegotiationFailed("LCP", "LCP completed without agreeing an authentication method"),
            )
        }
    }

    private suspend fun expectProceeded(wiring: SstpBridge, where: Where, timeout: Long?) {
        val received =
            if (timeout != null) {
                withTimeoutOrNull(timeout) { wiring.mailbox.receive() } ?: ControlMessage(where, Result.ERR_TIMEOUT)
            } else {
                wiring.mailbox.receive()
            }

        if (received.result == Result.PROCEEDED && received.from == where) {
            log(LogLevel.DEBUG, "${where.name} proceeded")
            return
        }

        if (received.result == Result.PROCEEDED) {
            throw EngineException(
                EngineError.Internal("Expected ${where.name} to report, but ${received.from.name} did"),
            )
        }

        sendLastPacket(received)
        throw EngineException(SstpErrorMapping.toEngineError(received))
    }

    private suspend fun sendLastPacket(received: ControlMessage) {
        val type =
            if (received.result == Result.ERR_DISCONNECT_REQUESTED) {
                SSTP_MESSAGE_TYPE_CALL_DISCONNECT_ACK
            } else {
                SSTP_MESSAGE_TYPE_CALL_ABORT
            }

        withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
            runCatching { sstpClient?.sendLastPacket(type) }
        }
    }

    private fun watchAfterNegotiation(wiring: SstpBridge) {
        watchdog =
            wiring.scope.launch {
                val received = wiring.mailbox.receive()
                if (received.result == Result.PROCEEDED) return@launch

                val error = SstpErrorMapping.toEngineError(received)
                log(LogLevel.ERROR, "The tunnel ended: ${error.detail}")
                sendLastPacket(received)
                closeEverything()
                finishTerminal(EngineState.Failed(error))
            }
    }

    private fun buildTunnelParams(wiring: SstpBridge): TunnelParams {
        val config = wiring.config
        val assigned = wiring.state.currentIPv4

        if (assigned.all { it == ZERO_BYTE }) {
            throw EngineException(
                EngineError.PppNegotiationFailed("IPCP", "The server assigned no address"),
            )
        }

        val proposedDns =
            wiring.state.currentProposedDns
                .takeIf { proposed -> proposed.any { it != ZERO_BYTE } }
                ?.let { listOf(InetAddress.getByAddress(it)) }
                .orEmpty()

        val dnsServers =
            config.customDns
                .mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                .ifEmpty { proposedDns }

        val params =
            TunnelParams(
                localAddress = InetAddress.getByAddress(assigned),
                prefixLength = HOST_PREFIX_LENGTH,
                dnsServers = dnsServers,
                mtu = config.mtu,
                excludedRoutes = transportExclusion(config),
            )

        log(
            LogLevel.INFO,
            "Negotiated address=${params.localAddress.hostAddress}/$HOST_PREFIX_LENGTH " +
                "mtu=${params.mtu} dnsServers=${params.dnsServers.size}",
        )
        return params
    }

    private fun transportExclusion(config: SstpEngineConfig): List<Route> {
        val peer = config.proxy?.host ?: config.server

        return try {
            val resolved = InetAddress.getByName(peer)
            if (resolved is Inet4Address) {
                listOf(Route(resolved, HOST_PREFIX_LENGTH))
            } else {
                log(LogLevel.WARN, "The transport peer is not IPv4; excludeRoute not applied")
                emptyList()
            }
        } catch (e: Exception) {
            log(LogLevel.WARN, "Could not resolve the transport peer for excludeRoute: ${e.message}")
            emptyList()
        }
    }

    private fun failureHandler(mailbox: ControlMailbox) = CoroutineExceptionHandler { _, throwable ->
        log(LogLevel.ERROR, "A client failed: ${throwable.javaClass.simpleName}: ${throwable.message}")
        mailbox.tryReport(Where.INCOMING, Result.ERR_PARSING_FAILED, throwable.javaClass.simpleName)
    }

    private fun reportAndClose(error: EngineError) {
        log(LogLevel.ERROR, "The connection attempt failed: ${error.detail}")
        closeEverything()
        finishTerminal(EngineState.Failed(error))
    }

    private fun closeEverything() {
        watchdog?.cancel()
        outgoingManager?.cancel()
        incomingManager?.cancel()
        sstpClient?.cancel()
        pppClient?.cancel()
        bridge?.mailbox?.close()
        terminal?.close()
        ipTerminal?.close()

        synchronized(lock) {
            scope?.cancel()
            scope = null
            ipTerminal = null
        }
    }

    private fun fail(error: EngineError) {
        finishTerminal(EngineState.Failed(error))
    }

    private fun finishTerminal(terminalState: EngineState) {
        synchronized(lock) {
            val current = stateFlow.value
            if (current is EngineState.Failed || current is EngineState.Disconnected) return
            stateFlow.value = terminalState
        }
    }

    private fun log(level: LogLevel, message: String) {
        eventFlow.tryEmit(
            EngineLogEvent(
                timestamp = clock(),
                level = level,
                protocol = Protocol.SSTP,
                tag = TAG,
                message = redactor.scrub(message),
            ),
        )
    }

    internal fun interface TerminalFactory {
        fun create(
            config: SstpEngineConfig,
            protector: SocketProtector,
            trustManager: X509TrustManager,
            log: (LogLevel, String) -> Unit,
        ): SslTerminal

        companion object {
            val DEFAULT: TerminalFactory =
                TerminalFactory { config, protector, trustManager, log ->
                    SslTerminal(config, protector, trustManager, log)
                }
        }
    }

    companion object {
        private const val TAG = "SstpEngine"
        const val STAGE_TRANSPORT: String = "tls_handshake"
        const val STAGE_SSTP: String = "sstp_call_setup"
        const val STAGE_PPP: String = "ppp_negotiation"
        const val STAGE_AUTH: String = "authentication"
        const val STAGE_IPCP: String = "ipcp"
        const val STAGE_AWAITING_TUN: String = "awaiting_tun"
        private const val EVENT_BUFFER = 256
        private const val DISCONNECT_TIMEOUT_MS = 3_000L
        private const val HOST_PREFIX_LENGTH = 32
        private const val ZERO_BYTE: Byte = 0
    }
}

internal class Redactor(private val secrets: List<String>) {
    fun scrub(message: String): String = secrets.fold(message) { text, secret -> text.replace(secret, MASK) }

    companion object {
        private const val MASK = "***"
        val NOTHING: Redactor = Redactor(emptyList())

        fun of(config: SstpEngineConfig): Redactor = Redactor(
            listOfNotNull(config.password, config.proxy?.password, config.proxy?.username)
                .filter { it.isNotEmpty() },
        )
    }
}
