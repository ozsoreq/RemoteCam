package app.holdthatpose.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType

/** Frosted, hairline-bordered panel used for every floating control. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = PoseColors.Glass,
    border: Brush = PoseColors.HairlineBrush,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(tint)
            .border(1.dp, border, shape),
        content = content,
    )
}

/** Click with a soft spring "press" instead of a ripple, plus a light haptic tick. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    val hf = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
            if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .height(58.dp)
            .fillMaxWidth()
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(if (enabled) PoseColors.AccentBrush else Brush.linearGradient(listOf(PoseColors.Ink3, PoseColors.Ink3)))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = PoseType.Label.copy(fontSize = PoseType.Label.fontSize * 1.1f), color = if (enabled) PoseColors.Ink else PoseColors.PaperFaint)
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) PoseColors.Ink else PoseColors.PaperFaint)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Glass(
        modifier.height(54.dp).pressable(onClick = onClick),
        shape = CircleShape,
        tint = PoseColors.GlassLight,
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(19.dp), tint = PoseColors.Paper)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = PoseType.Label, color = PoseColors.Paper, textAlign = TextAlign.Center)
        }
    }
}

/** Round glass button for overlay toggles; [active] lights it with the accent. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    val tint by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "active")
    Glass(
        modifier.size(size).pressable(enabled = enabled, pressedScale = 0.88f, onClick = onClick),
        shape = CircleShape,
        tint = if (active) PoseColors.Sky.copy(alpha = 0.16f) else PoseColors.Glass,
        border = if (active) Brush.linearGradient(listOf(PoseColors.Sky.copy(0.7f), PoseColors.Azure.copy(0.3f))) else PoseColors.HairlineBrush,
    ) {
        Icon(
            icon,
            contentDescription,
            Modifier.align(Alignment.Center).size(size * 0.46f),
            tint = androidx.compose.ui.graphics.lerp(
                if (enabled) PoseColors.Paper else PoseColors.PaperFaint,
                PoseColors.Sky,
                tint,
            ),
        )
    }
}

enum class Tone(val color: Color) { Neutral(PoseColors.Paper), Good(PoseColors.Mint), Warn(PoseColors.Amber), Bad(PoseColors.Danger), Accent(PoseColors.Sky) }

/** Soft glowing status dot; pulses when [pulse] (e.g. while searching or reconnecting). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulse: Boolean = false, dotSize: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val halo by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "halo",
    )
    Canvas(modifier.size(dotSize * 2.4f)) {
        val r = dotSize.toPx() / 2
        if (pulse) {
            drawCircle(color.copy(alpha = 0.35f * (1 - halo)), radius = r + r * 1.4f * halo)
        } else {
            drawCircle(color.copy(alpha = 0.18f), radius = r * 1.9f)
        }
        drawCircle(color, radius = r)
    }
}

/** Pill-shaped notice for warnings on top of the viewfinder. */
@Composable
fun NoticePill(text: String, tone: Tone, modifier: Modifier = Modifier, pulse: Boolean = false) {
    Glass(modifier, shape = CircleShape) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(tone.color, pulse = pulse, dotSize = 7.dp)
            Spacer(Modifier.width(4.dp))
            Text(text, style = PoseType.Caption, color = PoseColors.Paper)
        }
    }
}

/** Slow-drifting blue glow behind the non-camera screens. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier.fillMaxSize().background(PoseColors.Ink)) {
        val w = size.width
        val h = size.height
        fun glow(center: Offset, radius: Float, color: Color) = drawCircle(
            Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
        glow(Offset(w * (0.15f + 0.5f * a), h * (0.02f + 0.08f * b)), w * 0.95f, PoseColors.Azure.copy(alpha = 0.26f * intensity))
        glow(Offset(w * (0.95f - 0.4f * b), h * (0.12f + 0.1f * a)), w * 0.8f, PoseColors.Sky.copy(alpha = 0.20f * intensity))
        glow(Offset(w * (0.2f + 0.3f * b), h * (0.95f - 0.06f * a)), w * 1.0f, Color(0xFF6A4BFF).copy(alpha = 0.12f * intensity))
        // Gentle vignette so type always sits on deep ink.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.55f to PoseColors.Ink.copy(alpha = 0.55f), 1f to PoseColors.Ink))
    }
}

/** Uppercase letter-spaced label, e.g. section headers. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = PoseColors.PaperFaint) {
    Text(text.uppercase(), modifier, style = PoseType.Overline, color = color)
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
