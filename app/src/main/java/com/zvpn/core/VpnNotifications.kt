package com.zvpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.zvpn.MainActivity
import com.zvpn.R
import com.zvpn.domain.model.NotifChip
import com.zvpn.ui.format.formatBytes
import com.zvpn.ui.format.formatRate

class VpnNotifications(private val ctx: Context) {
    enum class Stage { Preparing, Interface, Engine, Tunnel, Connected }

    sealed interface Phase {
        data class Connecting(val stage: Stage) : Phase
        data object Connected : Phase
        data class Failed(val reason: String, val failureReason: FailureReason = FailureReason.parse(reason), val retryable: Boolean = true) : Phase
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return

        // Low importance channel for ongoing service notification
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(serviceChannel)

        // High importance channel for failure/alert notifications
        val alertChannel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            ctx.getString(R.string.notif_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = ctx.getString(R.string.notif_channel_alerts_desc)
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(alertChannel)
    }

    fun areNotificationsEnabled(): Boolean {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) {
            return false
        }
        return true
    }

    fun isChannelBlocked(channelId: String = CHANNEL_ID): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun build(state: VpnNotificationState): Notification {
        return build(
            server = state.server,
            phase = state.phase,
            protocolLabel = state.protocolLabel,
            downBps = state.downBps,
            upBps = state.upBps,
            totalDown = state.totalDown,
            totalUp = state.totalUp,
            connectedAt = state.connectedAt,
            showSpeed = state.showSpeed,
            livePromotion = state.livePromotion,
            chip = state.chip,
            showDetailsOnLockScreen = state.showDetailsOnLockScreen
        )
    }

    /**
     * Notification shown when the user's point balance reaches 0 during an active VPN session.
     * The tunnel has been stopped. User is prompted to watch an ad or subscribe.
     */
    fun buildNoPointsNotification(): Notification {
        return NotificationCompat.Builder(ctx, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("VPN Disconnected — No Points")
            .setContentText("Your point balance ran out. Watch an ad or subscribe to continue.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your point balance reached 0 and the VPN tunnel was stopped.\nWatch an ad to earn +100 points and reconnect, or upgrade to Premium for unlimited access."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(openApp())
            .build()
    }

    fun build(
        server: String,
        phase: Phase,
        protocolLabel: String = "",
        downBps: Long = 0,
        upBps: Long = 0,
        totalDown: Long = 0,
        totalUp: Long = 0,
        connectedAt: Long = 0,
        showSpeed: Boolean = true,
        livePromotion: Boolean = true,
        chip: NotifChip = NotifChip.Speed,
        showDetailsOnLockScreen: Boolean = false,
    ): Notification {
        val resolvedServer = server.ifBlank { ctx.getString(R.string.app_name) }
        val title = BidiText.auto(resolvedServer)

        val brandColor = runCatching { ContextCompat.getColor(ctx, R.color.vpn_brand) }.getOrDefault(0xFF5646D6.toInt())

        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setColor(brandColor)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(if (showDetailsOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)

        if (!showDetailsOnLockScreen) {
            val publicNotification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(ctx.getString(R.string.notif_public_title))
                .setContentText(ctx.getString(R.string.notif_public_text))
                .setColor(brandColor)
                .build()
            b.setPublicVersion(publicNotification)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (Build.VERSION.SDK_INT >= 35 && livePromotion) {
            val manager = ctx.getSystemService(NotificationManager::class.java)
            val canPromote = manager?.canPostPromotedNotifications() == true
            if (canPromote) {
                b.setRequestPromotedOngoing(true)
            }
        }

        return when (phase) {
            is Phase.Connecting -> b.applyConnecting(phase.stage, protocolLabel).build()
            Phase.Connected -> b.applyConnected(
                protocolLabel, downBps, upBps, totalDown, totalUp, connectedAt, showSpeed, chip,
                resolvedServer,
            ).build()
            is Phase.Failed -> {
                val alertBuilder = NotificationCompat.Builder(ctx, CHANNEL_ID_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setColor(runCatching { ContextCompat.getColor(ctx, R.color.vpn_error) }.getOrDefault(0xFFB3261E.toInt()))
                alertBuilder.applyFailed(phase).build()
            }
        }
    }

    private fun NotificationCompat.Builder.applyConnecting(
        stage: Stage,
        protocolLabel: String,
    ): NotificationCompat.Builder {
        val stages = Stage.entries
        val stageCount = stages.size
        val segmentLen = 100 / maxOf(stageCount, 1)
        val step = stage.ordinal.coerceIn(0, stageCount - 1)
        val progress = (step + 1) * segmentLen
        val stageText = ctx.getString(stageLabel(stage))

        if (Build.VERSION.SDK_INT >= 35) {
            val segmentColors = listOf(
                R.color.vpn_segment_1,
                R.color.vpn_segment_2,
                R.color.vpn_segment_3,
                R.color.vpn_segment_4,
            ).map { runCatching { ContextCompat.getColor(ctx, it) }.getOrDefault(0xFF5646D6.toInt()) }

            val style = NotificationCompat.ProgressStyle()
                .setProgressSegments(
                    segmentColors.map { NotificationCompat.ProgressStyle.Segment(segmentLen).setColor(it) },
                )
                .setProgressPoints(
                    (1 until stageCount).map { i ->
                        NotificationCompat.ProgressStyle.Point(i * segmentLen).setColor(ContextCompat.getColor(ctx, R.color.vpn_point))
                    },
                )
                .setProgress(progress)
                .setProgressTrackerIcon(IconCompat.createWithResource(ctx, R.drawable.ic_bolt))
                .setProgressStartIcon(IconCompat.createWithResource(ctx, R.drawable.ic_home))
                .setProgressEndIcon(IconCompat.createWithResource(ctx, R.drawable.ic_public))
            setStyle(style)
        } else {
            setProgress(100, progress, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setShortCriticalText(truncateChip(ctx.getString(R.string.notif_chip_connecting)))
        }

        return setContentText(stageText)
            .setSubText(protocolLabel.takeIf { it.isNotBlank() }?.let(BidiText::ltr))
            .setShowWhen(false)
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_cancel), stopTunnel())
    }

    private fun NotificationCompat.Builder.applyConnected(
        protocolLabel: String,
        downBps: Long,
        upBps: Long,
        totalDown: Long,
        totalUp: Long,
        connectedAt: Long,
        showSpeed: Boolean,
        chip: NotifChip,
        configName: String,
    ): NotificationCompat.Builder {
        val (dl, dlU) = formatRate(downBps)
        val (ul, ulU) = formatRate(upBps)

        val speeds = BidiText.ltr(ctx.getString(R.string.notif_speeds, "$dl $dlU", "$ul $ulU"))

        val totals = ctx.getString(
            R.string.notif_totals,
            BidiText.ltr(formatBytes(totalDown)),
            BidiText.ltr(formatBytes(totalUp)),
        )
        val headline = if (showSpeed) speeds else ctx.getString(R.string.state_connected)
        val detail = if (showSpeed) "$speeds\n$totals" else totals

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setShortCriticalText(truncateChip(chipText(chip, showSpeed, "$dl$dlU", totalDown + totalUp, connectedAt, configName)))
        }

        return setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSubText(protocolLabel.takeIf { it.isNotBlank() }?.let(BidiText::ltr))
            .setShowWhen(connectedAt > 0)
            .setWhen(connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setUsesChronometer(connectedAt > 0)
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_disconnect), stopTunnel())
    }

    private fun chipText(
        chip: NotifChip,
        showSpeed: Boolean,
        downRate: String,
        totalBytes: Long,
        connectedAt: Long,
        configName: String,
    ): String = when (chip) {
        NotifChip.Speed ->
            if (showSpeed) BidiText.ltr("${ctx.getString(R.string.speed_down_arrow)} $downRate").toString() else ctx.getString(R.string.notif_chip_on)
        NotifChip.ConfigName -> BidiText.auto(shorten(configName)).toString()
        NotifChip.Duration -> BidiText.ltr(elapsedShort(connectedAt)).toString()
        NotifChip.Total -> BidiText.ltr(formatBytes(totalBytes)).toString()
        NotifChip.None -> ctx.getString(R.string.notif_chip_on)
    }

    private fun shorten(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ctx.getString(R.string.app_name)
        return truncateChip(trimmed)
    }

    private fun truncateChip(text: String): String {
        if (text.length <= CHIP_MAX_CHARS) return text
        return text.take(CHIP_MAX_CHARS - 1) + "…"
    }

    private fun elapsedShort(connectedAt: Long): String {
        if (connectedAt <= 0) return ctx.getString(R.string.time_0s)
        val seconds = ((System.currentTimeMillis() - connectedAt) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}${ctx.getString(R.string.time_unit_s)}"
            seconds < 3600 -> "${seconds / 60}${ctx.getString(R.string.time_unit_m)}"
            else -> "${seconds / 3600}${ctx.getString(R.string.time_unit_h)}"
        }
    }

    private fun NotificationCompat.Builder.applyFailed(
        phase: Phase.Failed,
    ): NotificationCompat.Builder {
        val localizedReason = phase.failureReason.localizedMessage(ctx)
        val reasonText = BidiText.auto(localizedReason)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setShortCriticalText(truncateChip(ctx.getString(R.string.notif_chip_off)))
        }

        val b = setContentTitle(ctx.getString(R.string.notif_title_disconnected))
            .setContentText(reasonText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasonText))
            .setShowWhen(false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 1000L) // Auto dismiss after 10 minutes

        if (phase.retryable) {
            b.addAction(R.drawable.ic_sync, ctx.getString(R.string.action_retry), reconnect())
        }
        return b.addAction(R.drawable.ic_close, ctx.getString(R.string.action_dismiss), stopTunnel())
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            ctx, REQ_OPEN,
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopTunnel(): PendingIntent =
        PendingIntent.getService(
            ctx, REQ_STOP,
            Intent(ctx, ZVpnService::class.java)
                .putExtra(VpnManager.EXTRA_COMMAND, VpnManager.CMD_STOP)
                .setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun reconnect(): PendingIntent =
        PendingIntent.getActivity(
            ctx, REQ_RETRY,
            Intent(ctx, MainActivity::class.java).apply {
                action = ACTION_CONNECT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setPackage(ctx.packageName)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stageLabel(stage: Stage): Int = when (stage) {
        Stage.Preparing -> R.string.notif_stage_preparing
        Stage.Interface -> R.string.notif_stage_interface
        Stage.Engine -> R.string.notif_stage_engine
        Stage.Tunnel -> R.string.notif_stage_tunnel
        Stage.Connected -> R.string.state_connected
    }

    companion object {
        private const val CHIP_MAX_CHARS = 7 // System chips get truncated around 7 characters

        const val CHANNEL_ID = "zed_vpn"
        const val CHANNEL_ID_ALERTS = "zed_vpn_alerts"
        const val NOTIFICATION_ID = 1

        const val ACTION_CONNECT = "com.zvpn.action.CONNECT"

        private const val REQ_OPEN = 0
        private const val REQ_STOP = 1
        private const val REQ_RETRY = 2
    }
}
