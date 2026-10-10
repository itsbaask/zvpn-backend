package com.zvpn.core.tor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zvpn.MainActivity
import com.zvpn.R
import com.zvpn.core.VpnManager
import com.zvpn.core.VpnNotifications
import com.zvpn.core.ZVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TorForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var torController: TorController? = null
    private var userStopped = false
    private var restartAttempts = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        VpnNotifications(this).createChannel()
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            userStopped = true
            stopTorService()
            return START_NOT_STICKY
        }

        if (!TorStateHolder.state.value.let { it is TorServiceState.Connected || it is TorServiceState.Connecting }) {
            userStopped = false
            acquireWakeLock()
            startTor()
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZVPN::TorWakeLock").apply {
                acquire(10 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    private fun startTor() {
        val settings = com.zvpn.domain.model.AppSettings()
        torController = TorController(
            context = this,
            settings = settings,
            onBootstrapped = {
                restartAttempts = 0
                TorStateHolder.update(TorServiceState.Connected)
                updateNotification("Tor Connected • Browser Protected")
                releaseWakeLock()
            },
            onStopped = { reason ->
                TorStateHolder.update(TorServiceState.Error(reason ?: "Tor stopped"))
                releaseWakeLock()
                if (!userStopped) {
                    scheduleRestart()
                } else {
                    stopSelf()
                }
            },
            onProgress = { pct ->
                TorStateHolder.update(TorServiceState.Connecting(pct))
                updateNotification("Connecting to Tor… $pct%")
            }
        )

        startForeground(VpnNotifications.NOTIFICATION_ID, buildNotification("Starting Tor…"))
        scope.launch {
            torController?.start()
        }
    }

    private fun scheduleRestart() {
        if (userStopped) return
        restartAttempts++
        val delayMs = 1000L * (1L shl restartAttempts.coerceAtMost(5))
        scope.launch {
            delay(delayMs)
            if (!userStopped) {
                acquireWakeLock()
                startTor()
            }
        }
    }

    private fun stopTorService() {
        userStopped = true
        torController?.stop()
        torController = null
        releaseWakeLock()
        TorStateHolder.update(TorServiceState.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        userStopped = true
        torController?.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val stopTorIntent = Intent(this, TorForegroundService::class.java).setAction(ACTION_STOP)
        val stopTorPendingIntent = PendingIntent.getService(
            this, 10, stopTorIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val brandColor = runCatching { ContextCompat.getColor(this, R.color.vpn_brand) }.getOrDefault(0xFF5646D6.toInt())

        val builder = NotificationCompat.Builder(this, VpnNotifications.CHANNEL_ID)
            .setContentTitle("ZVPN Unified Proxy")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(brandColor)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_close, "Stop Tor", stopTorPendingIntent)

        if (VpnManager.status.value.state.isActive) {
            val stopVpnIntent = Intent(this, ZVpnService::class.java)
                .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_STOP)
            val stopVpnPendingIntent = PendingIntent.getService(
                this, 11, stopVpnIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_close, "Disconnect VPN", stopVpnPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        getSystemService(NotificationManager::class.java)?.notify(VpnNotifications.NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_STOP = "com.zvpn.action.STOP_TOR"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    runCatching {
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }

            val intent = Intent(context, TorForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TorForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
