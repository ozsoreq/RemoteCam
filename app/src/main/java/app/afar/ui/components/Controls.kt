package app.afar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType
import kotlin.math.abs

/**
 * The shutter: a warm disc inside a hairline ring. During a countdown it morphs into a
 * rounded "stop" square and the ring fills with progress, so the same button cancels.
 */
@Composable
fun ShutterButton(
    counting: Boolean,
    progress: Float,
    enabled: Boolean,
    queued: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inner by animateDpAsState(if (counting) 30.dp else 66.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "inner")
    val corner by animateDpAsState(if (counting) 9.dp else 33.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "corner")
    val sweep by animateFloatAsState(progress, tween(900, easing = FastOutSlowInEasing), label = "sweep")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "alpha")

    Box(
        modifier
            .size(88.dp)
            .pressable(enabled = enabled, pressedScale = 0.9f, haptic = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(88.dp)) {
            val stroke = 3.dp.toPx()
            val r = size.minDimension / 2 - stroke
            drawCircle(AfarColors.Paper.copy(alpha = 0.9f * alpha), radius = r, style = Stroke(stroke))
            if (counting || queued) {
                drawArc(
                    brush = AfarColors.AccentBrush,
                    startAngle = -90f,
                    sweepAngle = if (queued) 360f else 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(size.width - stroke * 2, size.height - stroke * 2),
                    style = Stroke(stroke * 1.4f, cap = StrokeCap.Round),
                    alpha = if (queued) 0.55f else 1f,
                )
            }
        }
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(corner))
                .background(
                    if (counting) Brush.linearGradient(listOf(AfarColors.Paper, AfarColors.Paper))
                    else AfarColors.AccentBrush,
                    alpha = alpha,
                ),
        )
    }
}

/** Cycles 0 → 3 → 5 → 10 s. */
@Composable
fun TimerChip(seconds: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Glass(modifier.height(44.dp).pressable(pressedScale = 0.9f, onClick = onClick), shape = CircleShape) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AfarIcons.Timer, "Timer", Modifier.size(18.dp), tint = if (seconds > 0) AfarColors.Sky else AfarColors.PaperDim)
            Spacer(Modifier.width(6.dp))
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "timer",
            ) { s ->
                Text(if (s == 0) "Off" else "${s}s", style = AfarType.Mono.copy(fontSize = AfarType.Mono.fontSize * 1.1f), color = AfarColors.Paper)
            }
        }
    }
}

/** Four rising bars; lit count shows link strength. */
@Composable
fun SignalBars(bars: Int, modifier: Modifier = Modifier, color: Color = AfarColors.Paper) {
    Canvas(modifier.size(width = 16.dp, height = 12.dp)) {
        val gap = 1.8.dp.toPx()
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * (i + 1) / 4f)
            drawRoundRect(
                color = if (i < bars) color else color.copy(alpha = 0.22f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2.5f),
            )
        }
    }
}

/** Minimal battery glyph with percentage; turns amber/red when low. */
@Composable
fun BatteryIndicator(percent: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        percent in 0 until 10 && !charging -> AfarColors.Danger
        percent in 10 until 20 && !charging -> AfarColors.Amber
        charging -> AfarColors.Mint
        else -> AfarColors.Paper
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 11.dp)) {
            val s = 1.2.dp.toPx()
            val nub = 2.dp.toPx()
            val body = Size(size.width - nub - s, size.height - s)
            drawRoundRect(color.copy(alpha = 0.6f), Offset(s / 2, s / 2), body, CornerRadius(3.dp.toPx()), style = Stroke(s))
            drawRoundRect(color.copy(alpha = 0.6f), Offset(size.width - nub, size.height * 0.32f), Size(nub, size.height * 0.36f), CornerRadius(1.dp.toPx()))
            val pad = 2.2.dp.toPx()
            val fillW = (body.width - pad * 2) * (percent.coerceIn(0, 100) / 100f)
            drawRoundRect(color, Offset(pad + s / 2, pad + s / 2), Size(fillW, body.height - pad * 2), CornerRadius(1.5.dp.toPx()))
        }
        Spacer(Modifier.width(6.dp))
        Text(if (percent < 0) "–" else "$percent%", style = AfarType.Mono, color = color)
    }
}

/** Rule-of-thirds grid with a faint shadow so lines read on bright skies too. */
@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val shadow = Color.Black.copy(alpha = 0.18f)
        val line = Color.White.copy(alpha = 0.42f)
        val sw = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(shadow, Offset(x + sw, 0f), Offset(x + sw, size.height), sw)
            drawLine(shadow, Offset(0f, y + sw), Offset(size.width, y + sw), sw)
            drawLine(line, Offset(x, 0f), Offset(x, size.height), sw)
            drawLine(line, Offset(0f, y), Offset(size.width, y), sw)
        }
    }
}

/**
 * Horizon level for the far phone. The inner line tracks the Camera's tilt; it snaps
 * mint when level, and turns red if the phone was bumped or is badly tipped.
 */
@Composable
fun LevelOverlay(roll: Float, bumped: Boolean, modifier: Modifier = Modifier) {
    val level = abs(roll) < 1f
    val bad = bumped || abs(roll) > 8f
    val shown by animateFloatAsState(if (level) 0f else roll, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "roll")
    val color = when {
        bad -> AfarColors.Danger
        level -> AfarColors.Mint
        else -> AfarColors.Paper
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val half = size.width * 0.2f
            val gap = 10.dp.toPx()
            val sw = 1.6.dp.toPx()
            // fixed reference ticks
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - half - 18.dp.toPx(), cy), Offset(cx - half - 6.dp.toPx(), cy), sw, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx + half + 6.dp.toPx(), cy), Offset(cx + half + 18.dp.toPx(), cy), sw, StrokeCap.Round)
            rotate(shown, Offset(cx, cy)) {
                drawLine(color, Offset(cx - half, cy), Offset(cx - gap, cy), sw * 1.2f, StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + half, cy), sw * 1.2f, StrokeCap.Round)
            }
            drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

/** Accent ring that blooms at the tap point, like a pro camera's focus reticle. */
@Composable
fun FocusReticle(key: Any, modifier: Modifier = Modifier) {
    val scale = remember(key) { Animatable(1.5f) }
    val alpha = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        kotlinx.coroutines.delay(700)
        alpha.animateTo(0f, tween(400))
    }
    Canvas(modifier.size(76.dp)) {
        val r = size.minDimension / 2 * scale.value * 0.9f
        drawCircle(AfarColors.Sky.copy(alpha = alpha.value), radius = r, style = Stroke(1.6.dp.toPx()))
        drawCircle(AfarColors.Sky.copy(alpha = alpha.value), radius = 2.dp.toPx())
    }
}

/** Big editorial countdown numeral with a soft bloom, shared by both phones. */
@Composable
fun CountdownNumeral(value: Int?, modifier: Modifier = Modifier, color: Color = AfarColors.Paper) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (scaleIn(initialScale = 1.6f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) + fadeIn(tween(160))) togetherWith
                (scaleOut(targetScale = 0.6f, animationSpec = tween(220)) + fadeOut(tween(220)))
        },
        modifier = modifier,
        label = "countdown",
        contentAlignment = Alignment.Center,
    ) { n ->
        if (n != null && n > 0) {
            Text("$n", style = AfarType.Countdown, color = color)
        } else {
            Box(Modifier.size(1.dp))
        }
    }
}

/** Segmented lens picker: 0.5× · 1× · Front. */
@Composable
fun LensPicker(
    options: List<app.afar.net.Lens>,
    selected: app.afar.net.Lens,
    onSelect: (app.afar.net.Lens) -> Unit,
    modifier: Modifier = Modifier,
) {
    Glass(modifier.height(40.dp), shape = CircleShape) {
        Row(
            Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { lens ->
                val active = lens == selected
                val bg by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "lens")
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(AfarColors.Paper.copy(alpha = 0.95f * bg))
                        .pressable(pressedScale = 0.9f) { onSelect(lens) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (lens == app.afar.net.Lens.Front) {
                        Icon(AfarIcons.Flip, "Front camera", Modifier.size(16.dp), tint = if (active) AfarColors.Ink else AfarColors.Paper)
                    } else {
                        Text(lens.label, style = AfarType.Mono, color = if (active) AfarColors.Ink else AfarColors.Paper)
                    }
                }
            }
        }
    }
}

/**
 * The 4-digit pairing code as separate tiles. Always laid out left-to-right: on an RTL
 * phone a plain Row would reverse the digits and the two screens would disagree.
 */
@Composable
fun CodeDigits(code: String, modifier: Modifier = Modifier, tileScale: Float = 1f) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            code.forEach { ch ->
                Glass(
                    Modifier.size(width = 60.dp * tileScale, height = 78.dp * tileScale),
                    shape = RoundedCornerShape(18.dp),
                    tint = AfarColors.GlassLight,
                ) {
                    Text(
                        "$ch",
                        style = AfarType.Code.copy(fontSize = AfarType.Code.fontSize * 0.82f * tileScale),
                        color = AfarColors.Paper,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}
