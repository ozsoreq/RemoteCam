package app.holdthatpose.ui.remote

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.holdthatpose.session.Shot
import app.holdthatpose.ui.components.EaseOutExpo
import app.holdthatpose.ui.components.Glass
import app.holdthatpose.ui.components.GlassIconButton
import app.holdthatpose.ui.components.HSpace
import app.holdthatpose.ui.components.Overline
import app.holdthatpose.ui.components.PrimaryButton
import app.holdthatpose.ui.components.SecondaryButton
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

/** Swipe through returned photos; Keep, Share, Delete (here or on both phones) or Retake. */
@Composable
fun ReviewScreen(
    shots: List<Shot>,
    start: Int,
    onClose: () -> Unit,
    onDelete: (shot: Shot, alsoCamera: Boolean) -> Unit,
    onRetake: () -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (shots.size - 1).coerceAtLeast(0))) { shots.size }
    val current = shots.getOrNull(pager.currentPage)
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    var askDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = askDelete) { askDelete = false }

    // Android 9 and older save to file:// Uris, which can't be shared with other apps.
    val shareable = current?.uri?.takeIf { it.scheme == "content" }

    Box(Modifier.fillMaxSize().background(PoseColors.Ink)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(PoseIcons.Back, "Back to camera", onClose, size = 40.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline("${pager.currentPage + 1} of ${shots.size}", color = PoseColors.Sky)
                    Text(current?.let { timeFormat.format(Date(it.takenAt)) } ?: "", style = PoseType.Label, color = PoseColors.Paper)
                }
                if (shareable != null) {
                    GlassIconButton(
                        PoseIcons.Share,
                        "Share",
                        {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("image/jpeg")
                                .putExtra(Intent.EXTRA_STREAM, shareable)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching {
                                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        size = 40.dp,
                    )
                    HSpace(8.dp)
                }
                GlassIconButton(PoseIcons.Trash, "Delete", { if (current != null) askDelete = true }, size = 40.dp)
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

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("Retake", onRetake, Modifier.weight(1f), icon = PoseIcons.Retake)
                PrimaryButton("Keep", onClose, Modifier.weight(1f), icon = PoseIcons.Check)
            }
        }

        // Delete: here (default) or on both phones. Undo is offered for 5 s afterwards.
        AnimatedVisibility(askDelete, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { askDelete = false } },
            )
        }
        AnimatedVisibility(
            askDelete,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(420, easing = EaseOutExpo)) { it } + fadeIn(),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(),
        ) {
            Glass(
                Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding(),
                shape = RoundedCornerShape(32.dp),
                tint = PoseColors.Ink2.copy(alpha = 0.97f),
            ) {
                Column(Modifier.padding(24.dp)) {
                    PrimaryButton("Delete here", {
                        askDelete = false
                        current?.let { onDelete(it, false) }
                    }, icon = PoseIcons.Trash)
                    VSpace(10.dp)
                    SecondaryButton("Delete on both", {
                        askDelete = false
                        current?.let { onDelete(it, true) }
                    }, Modifier.fillMaxWidth())
                    VSpace(10.dp)
                    SecondaryButton("Cancel", { askDelete = false }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
