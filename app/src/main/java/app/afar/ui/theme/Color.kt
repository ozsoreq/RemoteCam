package app.afar.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Afar palette: near-black "ink" surfaces, warm off-white type and a single
 * golden-hour accent (apricot → coral) used sparingly for the things that matter:
 * the shutter, the countdown and the active state.
 */
object AfarColors {
    val Ink = Color(0xFF07080A)
    val Ink2 = Color(0xFF0E1014)
    val Ink3 = Color(0xFF16181D)

    val Paper = Color(0xFFF4F1EC)
    val PaperDim = Paper.copy(alpha = 0.62f)
    val PaperFaint = Paper.copy(alpha = 0.38f)
    val PaperGhost = Paper.copy(alpha = 0.14f)

    val Glass = Color(0xFF101216).copy(alpha = 0.58f)
    val GlassLight = Color.White.copy(alpha = 0.06f)
    val Hairline = Color.White.copy(alpha = 0.10f)
    val HairlineStrong = Color.White.copy(alpha = 0.18f)

    val Apricot = Color(0xFFFFC58A)
    val Coral = Color(0xFFFF6B5E)
    val Ember = Color(0xFFFF8F6B)

    val Mint = Color(0xFF7CE0A3)
    val Amber = Color(0xFFFFC24B)
    val Danger = Color(0xFFFF5A5F)

    val AccentBrush: Brush
        get() = Brush.linearGradient(listOf(Apricot, Coral))

    val AccentBrushVertical: Brush
        get() = Brush.verticalGradient(listOf(Apricot, Coral))

    val HairlineBrush: Brush
        get() = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)),
        )
}
