package app.afar

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Saves a PNG of the current screen; CI pulls them off the emulator as an artifact. */
fun ComposeTestRule.screenshot(name: String) {
    waitForIdle()
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val dir = File(ctx.getExternalFilesDir(null), "screens").apply { mkdirs() }
    val bmp = onRoot().captureToImage().asAndroidBitmap()
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    // Small JPEG thumbnail that CI prints into the job log.
    val w = 360
    val thumb = Bitmap.createScaledBitmap(bmp, w, bmp.height * w / bmp.width, true)
    File(dir, "$name.jpg").outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 70, it) }
}
