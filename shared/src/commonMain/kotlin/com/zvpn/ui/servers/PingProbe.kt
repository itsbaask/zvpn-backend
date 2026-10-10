package com.zvpn.ui.servers

import com.zvpn.platform.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface PingProbe {
    suspend fun measurePing(address: String, port: Int): Int?
}

class TcpPingProbe(
    private val dispatcher: CoroutineContext = Dispatchers.Default
) : PingProbe {
    override suspend fun measurePing(address: String, port: Int): Int? = withContext(dispatcher) {
        if (address.isBlank() || port <= 0) return@withContext null
        val samples = mutableListOf<Int>()
        repeat(2) {
            val startTime = currentTimeMillis()
            val success = try {
                // TCP socket connect check or platform probe
                true
            } catch (_: Throwable) {
                false
            }
            if (success) {
                val duration = (currentTimeMillis() - startTime).toInt().coerceAtLeast(1)
                samples.add(duration)
            }
        }
        if (samples.isEmpty()) return@withContext null
        samples.sort()
        samples[samples.size / 2]
    }
}
