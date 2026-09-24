package app.afar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.afar.R

/** Editorial serif for moments (titles, countdown digits), a quiet grotesk for everything else. */
val Serif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val Sans = FontFamily(
    manrope(300),
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    manrope(800),
)

object AfarType {
    val Hero = TextStyle(fontFamily = Serif, fontSize = 64.sp, lineHeight = 60.sp, letterSpacing = (-0.02).em)
    val Title = TextStyle(fontFamily = Serif, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em)
    val TitleSmall = TextStyle(fontFamily = Serif, fontSize = 26.sp, lineHeight = 30.sp)
    val Countdown = TextStyle(fontFamily = Serif, fontSize = 220.sp, lineHeight = 220.sp, letterSpacing = (-0.04).em)
    val Code = TextStyle(fontFamily = Serif, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = 0.12.em)

    val Body = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp)
    val BodyStrong = Body.copy(fontWeight = FontWeight(600))
    val Label = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.01.em)
    val Caption = TextStyle(fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp)
    val Overline = TextStyle(fontFamily = Sans, fontWeight = FontWeight(700), fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.16.em)
    val Mono = TextStyle(fontFamily = Sans, fontWeight = FontWeight(700), fontSize = 12.sp, letterSpacing = 0.04.em, fontFeatureSettings = "tnum")
}

val AfarTypography = Typography(
    displayLarge = AfarType.Hero,
    headlineLarge = AfarType.Title,
    headlineSmall = AfarType.TitleSmall,
    bodyLarge = AfarType.Body,
    bodyMedium = AfarType.Body,
    labelLarge = AfarType.Label,
    labelMedium = AfarType.Caption,
    labelSmall = AfarType.Overline,
)
