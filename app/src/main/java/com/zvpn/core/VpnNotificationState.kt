package com.zvpn.core

import com.zvpn.domain.model.NotifChip

data class VpnNotificationState(
    val server: String = "",
    val phase: VpnNotifications.Phase = VpnNotifications.Phase.Connecting(VpnNotifications.Stage.Preparing),
    val protocolLabel: String = "",
    val downBps: Long = 0,
    val upBps: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    val connectedAt: Long = 0,
    val showSpeed: Boolean = true,
    val livePromotion: Boolean = true,
    val chip: NotifChip = NotifChip.Speed,
    val showDetailsOnLockScreen: Boolean = false,
)
