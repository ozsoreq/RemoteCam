package app.holdthatpose.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Apple-ish "emphasized decelerate" curve used for entrances. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Staggered rise-and-fade entrance: element [index] starts 70 ms after the previous one. */
@Composable
fun Modifier.enter(index: Int, baseDelay: Long = 80): Modifier {
    val progress = remember { Animatable(0f) }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(baseDelay + index * 70L)
        progress.animateTo(1f, tween(900, easing = EaseOutExpo))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1 - progress.value) * rise
    }
}
