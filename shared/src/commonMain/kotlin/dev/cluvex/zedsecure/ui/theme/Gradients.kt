package dev.cluvex.zedsecure.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object ZedGradients {
    // Primary Connected Gradient: Deep Lime -> Primary Lime
    val connected: Brush
        get() = Brush.linearGradient(
            colors = listOf(DeepLime, PrimaryLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    // Controlled Energy Pulse Gradient: Primary Lime -> Electric Lime
    val pulse: Brush
        get() = Brush.linearGradient(
            colors = listOf(PrimaryLime, ElectricLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    // Premium Surface Depth Gradient: Deep Obsidian -> Surface Black
    val surfaceDepth: Brush
        get() = Brush.verticalGradient(
            colors = listOf(DeepObsidian, SurfaceBlack),
        )

    private val sessionPalettes: List<List<Color>> = listOf(
        listOf(DeepLime, PrimaryLime),
        listOf(DeepLime, SoftLime, PrimaryLime),
        listOf(DeepObsidian, SurfaceBlack, DeepLime),
        listOf(PrimaryLime, ElectricLime),
        listOf(PrimaryBlack, SurfaceBlack, PrimaryLime),
    )

    fun forSession(sessionId: Int): Brush = Brush.linearGradient(
        colors = paletteFor(sessionId),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    private fun paletteFor(sessionId: Int): List<Color> =
        sessionPalettes[((sessionId % sessionPalettes.size) + sessionPalettes.size) % sessionPalettes.size]

    fun connectedStops(sessionId: Int): List<Color> =
        listOf(DeepObsidian) + paletteFor(sessionId)

    val connectingStops: List<Color> = listOf(DeepObsidian, DeepLime, PrimaryLime)

    // Lighter, clearly visible container gradient for idle/disconnected morphing blob button
    val idle: Brush
        get() = Brush.linearGradient(
            colors = listOf(Color(0xFF262E27), Color(0xFF19211A)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    val connecting: Brush
        get() = Brush.linearGradient(
            colors = listOf(DeepLime, PrimaryLime, ElectricLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
}
