package com.zvpn.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** MUST match the SOCKS port your Tor really listens on. */
private const val TOR_SOCKS_HOST = "127.0.0.1"
private const val TOR_SOCKS_PORT = 9250

private enum class TorGate { Checking, Ready, Blocked }

private val TRACKERS = setOf(
    "doubleclick.net", "googlesyndication.com", "google-analytics.com", "googletagmanager.com",
    "adservice.google.com", "connect.facebook.net", "scorecardresearch.com", "taboola.com",
    "outbrain.com", "hotjar.com", "criteo.com", "adnxs.com", "amazon-adsystem.com",
)

private fun isTracker(host: String) = TRACKERS.any { host == it || host.endsWith(".$it") }

private fun portOpen(): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress(TOR_SOCKS_HOST, TOR_SOCKS_PORT), 1500) }
    true
}.getOrDefault(false)

/** Real proof: ask the Tor Project, through the proxy, whether this request came from Tor. */
private fun verifyTor(): Boolean = runCatching {
    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(TOR_SOCKS_HOST, TOR_SOCKS_PORT))
    val c = URL("https://check.torproject.org/api/ip").openConnection(proxy) as HttpURLConnection
    c.connectTimeout = 15_000
    c.readTimeout = 15_000
    c.inputStream.bufferedReader().use { it.readText() }.contains("\"IsTor\":true")
}.getOrDefault(false)

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(tab: BrowserTabState, controller: BrowserController, modifier: Modifier) {
    val context = LocalContext.current
    var gate by remember { mutableStateOf(TorGate.Checking) }
    var attempt by remember { mutableIntStateOf(0) }
    val web = remember { mutableStateOf<WebView?>(null) }

    // Manual Tor Connection Toggle
    LaunchedEffect(controller.triggerToggleTor) {
        if (controller.triggerToggleTor == 0) return@LaunchedEffect

        if (controller.torState == BrowserTorState.Connected || controller.torState == BrowserTorState.Connecting) {
            controller.torState = BrowserTorState.Disconnected
            controller.torVerified = false
            controller.torProgress = 0
            gate = TorGate.Blocked
            runCatching {
                ProxyController.getInstance().clearProxyOverride({ it.run() }, { })
            }
        } else {
            attempt++
        }
    }

    // Fail closed: persistent proxy connection
    LaunchedEffect(attempt) {
        // Persistent connection check: if already connected and SOCKS port is open, preserve state!
        if (controller.torState == BrowserTorState.Connected && withContext(Dispatchers.IO) { portOpen() }) {
            gate = TorGate.Ready
            return@LaunchedEffect
        }

        if (attempt == 0 && !portOpen()) {
            controller.torState = BrowserTorState.Disconnected
            gate = TorGate.Blocked
            return@LaunchedEffect
        }

        gate = TorGate.Checking
        controller.torState = BrowserTorState.Connecting
        controller.torProgress = 15

        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        if (!supported) {
            controller.torVerified = false
            controller.torState = BrowserTorState.Disconnected
            gate = TorGate.Blocked
            return@LaunchedEffect
        }

        // Start local Tor process if SOCKS port 9250 is not open yet
        var isOpen = withContext(Dispatchers.IO) { portOpen() }
        if (!isOpen) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val clazz = Class.forName("com.zvpn.core.tor.TorController")
                    val settingsClazz = Class.forName("com.zvpn.domain.model.AppSettings")
                    val defaultSettings = settingsClazz.getConstructor().newInstance()

                    val ctor = clazz.constructors.first()
                    val instance = ctor.newInstance(
                        context,
                        defaultSettings,
                        { controller.torProgress = 100 },
                        { _: String? -> },
                        null,
                        { pct: Int -> controller.torProgress = pct }
                    )
                    val startMethod = clazz.getMethod("start")
                    startMethod.invoke(instance)
                }
            }

            var retry = 0
            while (!isOpen && retry < 20) {
                delay(1000)
                isOpen = withContext(Dispatchers.IO) { portOpen() }
                retry++
            }
        }

        if (!isOpen) {
            controller.torVerified = false
            controller.torState = BrowserTorState.Disconnected
            gate = TorGate.Blocked
            return@LaunchedEffect
        }

        val cfg = ProxyConfig.Builder().addProxyRule("socks5://$TOR_SOCKS_HOST:$TOR_SOCKS_PORT").build()
        suspendCoroutine<Unit> { cont ->
            ProxyController.getInstance().setProxyOverride(cfg, { it.run() }, { cont.resume(Unit) })
        }
        val ok = controller.torVerified == true || withContext(Dispatchers.IO) { verifyTor() }
        controller.torVerified = ok
        if (ok) {
            controller.torState = BrowserTorState.Connected
            gate = TorGate.Ready
        } else {
            controller.torState = BrowserTorState.Disconnected
            ProxyController.getInstance().clearProxyOverride({ it.run() }, { })
            gate = TorGate.Blocked
        }
    }

    DisposableEffect(tab.id) {
        onDispose {
            controller.attach(tab.id, null)
            web.value?.destroy()
            web.value = null
        }
    }

    when (gate) {
        TorGate.Checking -> if (tab.url.isNotBlank()) {
            Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Connecting to Tor… ${controller.torProgress}%", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Box(modifier.fillMaxSize())
        }

        TorGate.Blocked -> if (tab.url.isNotBlank()) {
            Column(modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Tor Disconnected", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the Z logo button at the top to connect Tor for private browsing.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { controller.toggleTor() }) { Text("Connect Tor Now") }
            }
        } else {
            Box(modifier.fillMaxSize())
        }

        TorGate.Ready -> AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = controller.javaScriptEnabled
                        domStorageEnabled = true
                        saveFormData = false
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setSupportMultipleWindows(false)
                        if (controller.desktopMode) {
                            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                            val s = r.url.scheme
                            return s != "http" && s != "https" // blocks intent:, file:, market: ...
                        }

                        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                            if (!isTracker(r.url.host.orEmpty())) return null
                            v.post { tab.blocked++; controller.totalBlocked++ }
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }

                        override fun onPageStarted(v: WebView, u: String, favicon: Bitmap?) {
                            tab.url = u; tab.loading = true; tab.progress = 0.05f
                        }

                        override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) {
                            tab.url = u; tab.canGoBack = v.canGoBack(); tab.canGoForward = v.canGoForward()
                        }

                        override fun onPageFinished(v: WebView, u: String) {
                            tab.loading = false; tab.progress = 1f
                            tab.canGoBack = v.canGoBack(); tab.canGoForward = v.canGoForward()
                            controller.recordVisit(u, v.title)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(v: WebView, p: Int) { tab.progress = p / 100f }
                        override fun onReceivedTitle(v: WebView, title: String?) { tab.title = title.orEmpty() }
                        override fun onGeolocationPermissionsShowPrompt(o: String, cb: GeolocationPermissions.Callback) {
                            cb.invoke(o, false, false)
                        }
                    }

                    controller.attach(tab.id, object : WebHandle {
                        override fun back() = goBack()
                        override fun forward() = goForward()
                        override fun reload() = this@apply.reload()
                        override fun stop() = stopLoading()
                        override fun load(url: String) = loadUrl(url)
                        override fun clearData() {
                            clearCache(true); clearHistory(); clearFormData()
                            WebStorage.getInstance().deleteAllData()
                            CookieManager.getInstance().removeAllCookies(null)
                        }
                    })
                    web.value = this
                    if (tab.url.startsWith("http://") || tab.url.startsWith("https://")) loadUrl(tab.url)
                }
            },
            update = { view ->
                view.settings.apply {
                    javaScriptEnabled = controller.javaScriptEnabled
                    userAgentString = if (controller.desktopMode) {
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        null
                    }
                    useWideViewPort = controller.desktopMode
                    loadWithOverviewMode = controller.desktopMode
                }
            },
        )
    }
}
