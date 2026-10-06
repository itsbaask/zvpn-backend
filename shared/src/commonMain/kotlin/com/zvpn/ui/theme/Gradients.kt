package com.zvpn.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object ZGradients {
    val connected: Brush
        get() = Brush.linearGradient(
            colors = listOf(ZViolet, ZHotPink, ZLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    private val sessionPalettes: List<List<Color>> = listOf(
        listOf(ZViolet, ZHotPink, ZLime),
        listOf(ZDeepViolet, ZViolet, ZCyan),
        listOf(ZHotPink, ZViolet, ZCyan),
        listOf(ZViolet, ZCyan, ZLime),
        listOf(ZDeepViolet, ZHotPink, ZViolet),
    )

    fun forSession(sessionId: Int): Brush = Brush.linearGradient(
        colors = paletteFor(sessionId),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    private fun paletteFor(sessionId: Int): List<Color> =
        sessionPalettes[((sessionId % sessionPalettes.size) + sessionPalettes.size) % sessionPalettes.size]

    fun connectedStops(sessionId: Int): List<Color> =
        listOf(ZDeepViolet) + paletteFor(sessionId)

    val connectingStops: List<Color> = listOf(ZDeepViolet, ZViolet, ZHotPink)

    val idle: Brush
        get() = Brush.linearGradient(
            colors = listOf(ZDeepViolet, ZViolet, ZHotPink.copy(alpha = 0.7f)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    val connecting: Brush
        get() = Brush.linearGradient(
            colors = listOf(ZViolet, ZCyan, ZLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
}
