package app.holdthatpose.ui.theme

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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val scheme = darkColorScheme(
    primary = PoseColors.Sky,
    onPrimary = PoseColors.Ink,
    secondary = PoseColors.Azure,
    background = PoseColors.Ink,
    onBackground = PoseColors.Paper,
    surface = PoseColors.Ink2,
    onSurface = PoseColors.Paper,
    surfaceVariant = PoseColors.Ink3,
    onSurfaceVariant = PoseColors.PaperDim,
    outline = PoseColors.Hairline,
    error = PoseColors.Danger,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = PoseTypography) {
        CompositionLocalProvider(
            // The UI is English-only: keep it left-to-right even on Hebrew/Arabic phones, so
            // layouts don't mirror and the pairing code digits keep their order.
            LocalLayoutDirection provides LayoutDirection.Ltr,
            LocalContentColor provides PoseColors.Paper,
            LocalTextSelectionColors provides TextSelectionColors(PoseColors.Sky, PoseColors.Sky.copy(alpha = 0.3f)),
            LocalRippleConfiguration provides RippleConfiguration(color = PoseColors.Paper),
            content = content,
        )
    }
}
