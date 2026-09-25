package app.holdthatpose.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.components.Overline
import app.holdthatpose.ui.components.PrimaryButton
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.components.pressable
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class Panel(val step: String, val title: String, val body: String)

private val panels = listOf(
    Panel("01", "Two phones,\none camera.", "Open Hold That Pose on both. One is the Camera, the other the Remote. No internet needed."),
    Panel("02", "Prop one.\nHold the other.", "Prop the Camera on a rock or ledge. Walk into the shot and watch yourself live."),
    Panel("03", "Tap. Pose.\nDone.", "Tap the shutter, hide the Remote during the countdown. The photo lands on both phones."),
)

/** First-run, three-panel how-to with a hand-drawn illustration per step. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { panels.size }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.8f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("hold that pose", style = PoseType.TitleSmall.copy(fontStyle = FontStyle.Italic), color = PoseColors.Paper)
                Box(Modifier.weight(1f))
                Text(
                    "Skip",
                    style = PoseType.Label,
                    color = PoseColors.PaperDim,
                    modifier = Modifier.clip(CircleShape).pressable(onClick = onDone).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp)
                        .graphicsLayer {
                            alpha = 1f - offset * 0.7f
                            translationX = offset * 40f
                        },
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Illustration(page, Modifier.size(260.dp))
                    }
                    Overline("Step ${panels[page].step}", color = PoseColors.Sky)
                    VSpace(12.dp)
                    Text(panels[page].title, style = PoseType.Title.copy(fontSize = PoseType.Title.fontSize * 1.15f), color = PoseColors.Paper)
                    VSpace(14.dp)
                    Text(panels[page].body, style = PoseType.Body, color = PoseColors.PaperDim)
                    VSpace(24.dp)
                }
            }

            Row(Modifier.padding(horizontal = 26.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(panels.size) { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 26.dp else 6.dp, label = "dot")
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) PoseColors.Sky else PoseColors.PaperGhost),
                    )
                }
            }
            VSpace(26.dp)
            val last = pager.currentPage == panels.lastIndex
            PrimaryButton(
                if (last) "Get started" else "Next",
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.padding(horizontal = 22.dp),
                icon = PoseIcons.Arrow,
            )
            VSpace(18.dp)
        }
    }
}

@Composable
internal fun Illustration(page: Int, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "illu")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        when (page) {
            0 -> twoPhones(phase)
            1 -> framing(phase)
            else -> countdown(phase, measurer)
        }
    }
}

private fun DrawScope.phone(center: Offset, w: Float, h: Float, stroke: Brush, fill: Color = Color.Transparent) {
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(fill, tl, Size(w, h), CornerRadius(w * 0.16f))
    drawRoundRect(stroke, tl, Size(w, h), CornerRadius(w * 0.16f), style = Stroke(2.dp.toPx()))
}

private fun DrawScope.twoPhones(phase: Float) {
    val c = center
    val far = Offset(c.x + size.width * 0.2f, c.y - size.height * 0.12f)
    val near = Offset(c.x - size.width * 0.16f, c.y + size.height * 0.1f)
    phone(far, size.width * 0.24f, size.width * 0.44f, Brush.linearGradient(listOf(Color.White.copy(0.5f), Color.White.copy(0.15f))))
    drawCircle(Color.White.copy(0.5f), radius = 5.dp.toPx(), center = far + Offset(0f, -size.width * 0.14f), style = Stroke(1.5.dp.toPx()))
    phone(near, size.width * 0.32f, size.width * 0.58f, PoseColors.AccentBrush, fill = PoseColors.Ink2)
    // radio waves between them
    for (i in 0 until 3) {
        val p = (phase + i / 3f) % 1f
        val mid = Offset((far.x + near.x) / 2, (far.y + near.y) / 2)
        drawCircle(
            PoseColors.Sky.copy(alpha = (1 - p) * 0.55f),
            radius = size.width * 0.05f + p * size.width * 0.16f,
            center = mid,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

private fun DrawScope.framing(phase: Float) {
    val w = size.width * 0.56f
    val h = w * 1.9f
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(PoseColors.Ink2, tl, Size(w, h), CornerRadius(w * 0.14f))
    drawRoundRect(PoseColors.AccentBrush, tl, Size(w, h), CornerRadius(w * 0.14f), style = Stroke(2.dp.toPx()))
    val inset = w * 0.08f
    val vx = tl.x + inset
    val vy = tl.y + inset * 2.2f
    val vw = w - inset * 2
    val vh = vw * 4f / 3f
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2A2E3A), Color(0xFF14161B)), startY = vy, endY = vy + vh), Offset(vx, vy), Size(vw, vh), CornerRadius(inset))
    // horizon + mountains
    val horizonY = vy + vh * 0.62f
    drawLine(Color.White.copy(0.18f), Offset(vx, horizonY), Offset(vx + vw, horizonY), 1.dp.toPx())
    // grid
    for (i in 1..2) {
        drawLine(Color.White.copy(0.28f), Offset(vx + vw * i / 3, vy), Offset(vx + vw * i / 3, vy + vh), 1.dp.toPx())
        drawLine(Color.White.copy(0.28f), Offset(vx, vy + vh * i / 3), Offset(vx + vw, vy + vh * i / 3), 1.dp.toPx())
    }
    // the person, gently swaying into the thirds line
    val px = vx + vw * (0.62f + 0.05f * kotlin.math.sin(phase * 2 * Math.PI).toFloat())
    val headY = vy + vh * 0.5f
    drawCircle(PoseColors.Sky, radius = vw * 0.055f, center = Offset(px, headY))
    drawLine(PoseColors.Sky, Offset(px, headY + vw * 0.07f), Offset(px, headY + vw * 0.3f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(PoseColors.Sky, Offset(px, headY + vw * 0.3f), Offset(px - vw * 0.06f, headY + vw * 0.46f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(PoseColors.Sky, Offset(px, headY + vw * 0.3f), Offset(px + vw * 0.06f, headY + vw * 0.46f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(PoseColors.Sky, Offset(px, headY + vw * 0.13f), Offset(px - vw * 0.08f, headY + vw * 0.02f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(PoseColors.Sky, Offset(px, headY + vw * 0.13f), Offset(px + vw * 0.08f, headY + vw * 0.24f), 3.dp.toPx(), StrokeCap.Round)
    // shutter
    drawCircle(Color.White.copy(0.9f), radius = w * 0.1f, center = Offset(center.x, tl.y + h - inset * 2.6f), style = Stroke(2.dp.toPx()))
    drawCircle(PoseColors.AccentBrush, radius = w * 0.075f, center = Offset(center.x, tl.y + h - inset * 2.6f))
}

private fun DrawScope.countdown(phase: Float, measurer: TextMeasurer) {
    val r = size.minDimension * 0.36f
    drawCircle(Color.White.copy(0.12f), radius = r, style = Stroke(2.dp.toPx()))
    drawArc(
        PoseColors.AccentBrush,
        startAngle = -90f,
        sweepAngle = 360f * phase,
        useCenter = false,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
    )
    drawCircle(
        Color.White.copy(0.2f),
        radius = r * 1.25f,
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 8.dp.toPx()))),
    )
    val n = 3 - (phase * 3).toInt().coerceAtMost(2)
    val layout = measurer.measure("$n", PoseType.Countdown.copy(fontSize = (r * 1.25f / density / fontScale).sp, lineHeight = (r * 1.25f / density / fontScale).sp, color = PoseColors.Paper))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
