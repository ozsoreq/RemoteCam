package app.afar.ui.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.afar.session.Shot
import app.afar.ui.components.GlassIconButton
import app.afar.ui.components.Overline
import app.afar.ui.components.PrimaryButton
import app.afar.ui.components.SecondaryButton
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

/** Swipe through returned photos; Keep, Delete (on both phones) or Retake. */
@Composable
fun ReviewScreen(
    shots: List<Shot>,
    start: Int,
    onClose: () -> Unit,
    onDelete: (Shot) -> Unit,
    onRetake: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (shots.size - 1).coerceAtLeast(0))) { shots.size }
    val current = shots.getOrNull(pager.currentPage)
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Box(Modifier.fillMaxSize().background(AfarColors.Ink)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(AfarIcons.Back, "Back to camera", onClose, size = 40.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline("${pager.currentPage + 1} of ${shots.size}", color = AfarColors.Apricot)
                    Text(current?.let { timeFormat.format(Date(it.takenAt)) } ?: "", style = AfarType.Label, color = AfarColors.Paper)
                }
                GlassIconButton(AfarIcons.Trash, "Delete on both phones", { current?.let(onDelete) }, size = 40.dp)
            }

            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 12.dp) { page ->
                val shot = shots[page]
                val bmp = remember(shot.id) { shot.image.asImageBitmap() }
                val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Image(
                        bmp,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .graphicsLayer {
                                val s = 1f - 0.06f * offset
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.5f * offset
                            },
                    )
                }
            }

            Text(
                "Saved on both phones",
                style = AfarType.Caption,
                color = AfarColors.PaperFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("Retake", onRetake, Modifier.weight(1f), icon = AfarIcons.Retake)
                PrimaryButton("Keep", onClose, Modifier.weight(1f), icon = AfarIcons.Check)
            }
        }
    }
}
