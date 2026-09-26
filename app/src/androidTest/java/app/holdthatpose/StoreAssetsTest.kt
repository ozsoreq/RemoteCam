package app.holdthatpose

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.home.Illustration
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseTheme
import app.holdthatpose.ui.theme.PoseType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the Play Store graphics from the app's own icon and design system, at the exact
 * sizes Play requires: a 512×512 icon and a 1024×500 feature graphic. CI pulls them off the
 * emulator and, on "[screenshots]" commits, stores them in docs/store/.
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun save(bmp: Bitmap, name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "store").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun playIcon512() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Full-bleed square: Play applies its own rounded mask to the store icon.
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(ContextCompat.getColor(ctx, R.color.ic_bg))
        // Draw the adaptive icon's foreground layer unmasked. The layer is 108 units wide with a
        // 72-unit visible area, so it is drawn 1.5× larger and centred to fill the square.
        val foreground = ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)!!
        val inset = (512 * 0.25f).toInt()
        foreground.setBounds(-inset, -inset, 512 + inset, 512 + inset)
        foreground.draw(canvas)
        assertEquals(512, bmp.width)
        save(bmp, "play_icon_512.png")
    }

    @Test
    fun featureGraphic1024x500() {
        compose.setContent {
            val d = LocalDensity.current
            val w = with(d) { 1024.toDp() }
            val h = with(d) { 500.toDp() }
            PoseTheme {
                Box(Modifier.requiredSize(w, h).testTag("feature")) {
                    AuroraBackground()
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = w * 0.07f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "hold that pose",
                                style = PoseType.TitleSmall.copy(fontStyle = FontStyle.Italic, fontSize = (h.value * 0.075f).sp),
                                color = PoseColors.PaperDim,
                            )
                            Text(
                                buildAnnotatedString {
                                    append("You, ")
                                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, brush = PoseColors.AccentBrush)) { append("from afar.") }
                                },
                                style = PoseType.Hero.copy(fontSize = (h.value * 0.2f).sp, lineHeight = (h.value * 0.21f).sp),
                                color = PoseColors.Paper,
                            )
                            Text(
                                "Two phones. One camera. No stranger needed.",
                                style = PoseType.Body.copy(fontSize = (h.value * 0.05f).sp, lineHeight = (h.value * 0.066f).sp),
                                color = PoseColors.PaperDim,
                            )
                        }
                        Illustration(0, Modifier.size(h * 0.72f))
                    }
                }
            }
        }
        compose.waitForIdle()
        val bmp = compose.onNodeWithTag("feature").captureToImage().asAndroidBitmap()
        val out = if (bmp.width == 1024 && bmp.height == 500) bmp else Bitmap.createScaledBitmap(bmp, 1024, 500, true)
        assertEquals(1024, out.width)
        assertEquals(500, out.height)
        save(out, "play_feature_graphic_1024x500.png")
    }
}
