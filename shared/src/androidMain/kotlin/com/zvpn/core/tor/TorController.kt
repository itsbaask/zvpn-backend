package com.zvpn.core.tor

import android.content.Context
import android.os.Build
import android.util.Log
import com.zvpn.domain.model.AppSettings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class TorController(
    private val context: Context,
    private val settings: AppSettings,
    private val onBootstrapped: () -> Unit,
    private val onStopped: (String?) -> Unit,
    private val upstreamSocksPort: Int? = null,
    private val onProgress: (Int) -> Unit = {},
) {
    private val active = AtomicBoolean(false)
    private var process: Process? = null
    private var reader: Thread? = null

    val socksPort: Int get() = TorConfigBuilder.SOCKS_PORT
    val isRunning: Boolean get() = active.get()

    fun start(): Boolean {
        if (active.get()) stop()

        val supportedAbis = Build.SUPPORTED_ABIS
        Log.i(TAG, "Device supported ABIs: ${supportedAbis.joinToString()}")
        val hasValidAbi = supportedAbis.any { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86_64" }
        if (!hasValidAbi) {
            val err = "Unsupported device architecture ABIs: ${supportedAbis.joinToString()}"
            Log.e(TAG, err)
            onStopped(err)
            return false
        }

        return try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val torBinary = File(nativeLibDir, "libtor.so")
            if (!torBinary.exists()) {
                val err = "Tor binary (libtor.so) missing in nativeLibraryDir ($nativeLibDir). Ensure extractNativeLibs=true in manifest."
                Log.e(TAG, err)
                onStopped(err)
                return false
            }

            val torDir = File(context.filesDir, "tor").apply { mkdirs() }
            extractAssetSafe("geoip", torDir)
            extractAssetSafe("geoip6", torDir)
            extractAssetSafe("bridges_default.lst", torDir)

            val dataDir = File(context.filesDir, "tor_data").apply { mkdirs() }
            val torrc = File(torDir, "torrc").apply {
                writeText(TorConfigBuilder.build(settings, nativeLibDir, torDir, dataDir, upstreamSocksPort))
            }

            val cmd = mutableListOf(
                torBinary.absolutePath,
                "-f", torrc.absolutePath,
                "--pidfile", File(dataDir, "tor.pid").absolutePath,
            )

            val webtunnel = settings.torBridgesMode != "none" && settings.torBridgeTransport == "webtunnel"
            if (settings.torFakeSniEnabled && !webtunnel) {
                val hosts = settings.torFakeSniHosts.split(',', '\n').map { it.trim() }
                    .filter { it.isNotEmpty() }.ifEmpty { DEFAULT_FAKE_SNI }.joinToString(",")
                cmd += "--fake-hosts"
                cmd += hosts
            }

            Log.i(TAG, "Launching Tor binary: ${torBinary.absolutePath}")
            val pb = ProcessBuilder(cmd)
            pb.environment()["LD_LIBRARY_PATH"] = nativeLibDir
            pb.redirectErrorStream(true)
            val p = pb.start()
            process = p
            active.set(true)

            reader = Thread {
                var bootstrapped = false
                runCatching {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        Log.i(TAG, line)
                        BOOTSTRAP_PCT.find(line)?.groupValues?.get(1)?.toIntOrNull()
                            ?.let { pct -> runCatching { onProgress(pct) } }
                        if (!bootstrapped && (line.contains("Bootstrapped 100%") || line.contains("Done establishing"))) {
                            bootstrapped = true
                            Log.i(TAG, "Tor successfully bootstrapped 100%")
                            Thread { runCatching { onBootstrapped() } }
                                .apply { isDaemon = true; start() }
                        }
                    }
                }

                if (active.compareAndSet(true, false)) {
                    val code = runCatching { p.waitFor() }.getOrDefault(-1)
                    Log.i(TAG, "Tor exited ($code)")
                    onStopped(if (bootstrapped) null else "Tor exited before connecting (exit code $code)")
                }
            }.apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            active.set(false)
            Log.e(TAG, "Tor failed to start", e)
            onStopped(e.message ?: "Tor failed to start")
            false
        }
    }

    fun stop() {
        active.set(false)
        runCatching { process?.destroy() }
        process = null
        reader = null
    }

    private fun extractAssetSafe(name: String, destDir: File) {
        val dest = File(destDir, name)
        if (dest.exists() && dest.length() > 0) return
        runCatching {
            context.assets.open("tor/$name").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }.onFailure {
            if (!dest.exists()) {
                dest.writeText("")
            }
        }
    }

    private companion object {
        const val TAG = "Tor"
        val BOOTSTRAP_PCT = Regex("""Bootstrapped (\d{1,3})%""")
        val DEFAULT_FAKE_SNI = listOf(
            "play.googleapis.com", "drive.google.com", "cdn.ampproject.org", "api.github.com",
            "ajax.aspnetcdn.com", "verizon.com", "eset.com",
        )
    }
}
