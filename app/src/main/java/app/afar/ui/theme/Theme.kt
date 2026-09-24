package app.afar.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val scheme = darkColorScheme(
    primary = AfarColors.Apricot,
    onPrimary = AfarColors.Ink,
    secondary = AfarColors.Coral,
    background = AfarColors.Ink,
    onBackground = AfarColors.Paper,
    surface = AfarColors.Ink2,
    onSurface = AfarColors.Paper,
    surfaceVariant = AfarColors.Ink3,
    onSurfaceVariant = AfarColors.PaperDim,
    outline = AfarColors.Hairline,
    error = AfarColors.Danger,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = AfarTypography) {
        CompositionLocalProvider(
            LocalContentColor provides AfarColors.Paper,
            LocalTextSelectionColors provides TextSelectionColors(AfarColors.Apricot, AfarColors.Apricot.copy(alpha = 0.3f)),
            LocalRippleConfiguration provides RippleConfiguration(color = AfarColors.Paper),
            content = content,
        )
    }
}
