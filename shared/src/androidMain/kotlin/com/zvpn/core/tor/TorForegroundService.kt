package com.zvpn.core.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            userStopped = true
            stopTorService()
            return START_NOT_STICKY
        }

        if (!TorStateHolder.state.value.let { it is TorServiceState.Connected || it is TorServiceState.Connecting }) {
            userStopped = false
            startTor()
        }

        return START_STICKY
    }

    private fun startTor() {
        val settings = com.zvpn.domain.model.AppSettings()
        torController = TorController(
            context = this,
            settings = settings,
            onBootstrapped = {
                restartAttempts = 0
                TorStateHolder.update(TorServiceState.Connected)
                updateNotification("Tor connected & secured")
            },
            onStopped = { reason ->
                TorStateHolder.update(TorServiceState.Error(reason ?: "Tor stopped"))
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

        startForeground(NOTIFICATION_ID, buildNotification("Starting Tor…"))
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
                startTor()
            }
        }
    }

    private fun stopTorService() {
        torController?.stop()
        torController = null
        TorStateHolder.update(TorServiceState.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        userStopped = true
        torController?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tor Browser Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Tor routing active for secure browsing"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, TorForegroundService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openIntent = Intent().setClassName(this, "com.zvpn.MainActivity")
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconRes = resources.getIdentifier("ic_stat_notification", "drawable", packageName)
            .takeIf { it != 0 } ?: android.R.drawable.ic_menu_compass
        val closeRes = resources.getIdentifier("ic_close", "drawable", packageName)
            .takeIf { it != 0 } ?: android.R.drawable.ic_menu_close_clear_cancel

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZVPN Tor Proxy")
            .setContentText(text)
            .setSmallIcon(iconRes)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(closeRes, "Stop Tor", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "tor_service_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.zvpn.action.STOP_TOR"

        fun start(context: Context) {
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
