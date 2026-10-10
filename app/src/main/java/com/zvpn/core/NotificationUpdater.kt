package com.zvpn.core

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock

class NotificationUpdater(private val context: Context, private val notificationId: Int = VpnNotifications.NOTIFICATION_ID) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private var lastState: VpnNotificationState? = null
    private var lastUpdateTimeMs: Long = 0
    private val minIntervalMs = 1000L // Max 1 update per second

    fun update(state: VpnNotificationState, builder: VpnNotifications) {
        val now = SystemClock.elapsedRealtime()
        if (state == lastState && (now - lastUpdateTimeMs < minIntervalMs)) {
            return // Coalesce identical consecutive states within the throttle interval
        }

        lastState = state
        lastUpdateTimeMs = now

        val notification = builder.build(state)
        notificationManager?.notify(notificationId, notification)
    }

    fun cancel() {
        notificationManager?.cancel(notificationId)
        lastState = null
    }
}
