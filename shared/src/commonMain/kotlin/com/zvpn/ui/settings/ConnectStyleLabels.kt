package com.zvpn.ui.settings

import com.zvpn.domain.model.ConnectButtonStyle
import com.zvpn.shared.resources.Res
import com.zvpn.shared.resources.connect_style_bar
import com.zvpn.shared.resources.connect_style_hero
import com.zvpn.shared.resources.connect_style_icon
import com.zvpn.shared.resources.connect_style_pill
import com.zvpn.shared.resources.connect_style_ring
import com.zvpn.shared.resources.connect_style_switch
import org.jetbrains.compose.resources.StringResource

fun ConnectButtonStyle.labelRes(): StringResource = when (this) {
    ConnectButtonStyle.Pill -> Res.string.connect_style_pill
    ConnectButtonStyle.Hero -> Res.string.connect_style_hero
    ConnectButtonStyle.Ring -> Res.string.connect_style_ring
    ConnectButtonStyle.Bar -> Res.string.connect_style_bar
    ConnectButtonStyle.Icon -> Res.string.connect_style_icon
    ConnectButtonStyle.Switch -> Res.string.connect_style_switch
}
