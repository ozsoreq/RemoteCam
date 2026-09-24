package app.afar.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Afar palette: near-black "ink" surfaces, soft off-white type and a single
 * blue accent (sky → azure) used sparingly for the things that matter:
 * the shutter, the countdown and the active state.
 */
object AfarColors {
    val Ink = Color(0xFF06080C)
    val Ink2 = Color(0xFF0D1118)
    val Ink3 = Color(0xFF151A23)

    val Paper = Color(0xFFF1F4F9)
    val PaperDim = Paper.copy(alpha = 0.62f)
    val PaperFaint = Paper.copy(alpha = 0.38f)
    val PaperGhost = Paper.copy(alpha = 0.14f)

    val Glass = Color(0xFF0E131B).copy(alpha = 0.58f)
    val GlassLight = Color.White.copy(alpha = 0.06f)
    val Hairline = Color.White.copy(alpha = 0.10f)
    val HairlineStrong = Color.White.copy(alpha = 0.18f)

    val Sky = Color(0xFF8FD4FF)
    val Azure = Color(0xFF4A78FF)

    val Mint = Color(0xFF7CE0A3)
    val Amber = Color(0xFFFFC24B)
    val Danger = Color(0xFFFF5A5F)

    val AccentBrush: Brush
        get() = Brush.linearGradient(listOf(Sky, Azure))

    val AccentBrushVertical: Brush
        get() = Brush.verticalGradient(listOf(Sky, Azure))

    val HairlineBrush: Brush
        get() = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)),
        )
}
