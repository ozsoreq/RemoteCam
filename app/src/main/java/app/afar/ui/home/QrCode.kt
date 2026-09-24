package app.afar.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code drawn as soft rounded modules with rounded finder eyes — scannable and pretty. */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, color: Color = Color.Black) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Canvas(modifier) { drawQr(matrix, color) }
}

private fun DrawScope.drawQr(m: BitMatrix, color: Color) {
    val n = m.width
    val cell = size.minDimension / n
    fun inFinder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
    for (y in 0 until n) for (x in 0 until n) {
        if (!m[x, y] || inFinder(x, y)) continue
        val pad = cell * 0.1f
        drawRoundRect(
            color,
            topLeft = Offset(x * cell + pad, y * cell + pad),
            size = Size(cell - 2 * pad, cell - 2 * pad),
            cornerRadius = CornerRadius(cell * 0.35f),
        )
    }
    listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEach { (fx, fy) ->
        val o = Offset(fx * cell, fy * cell)
        val stroke = cell
        drawRoundRect(
            color,
            topLeft = o + Offset(stroke / 2, stroke / 2),
            size = Size(7 * cell - stroke, 7 * cell - stroke),
            cornerRadius = CornerRadius(cell * 2f),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color,
            topLeft = o + Offset(2 * cell, 2 * cell),
            size = Size(3 * cell, 3 * cell),
            cornerRadius = CornerRadius(cell),
        )
    }
}
