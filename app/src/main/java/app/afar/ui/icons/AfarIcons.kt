package app.afar.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * A small hand-drawn icon set on a 24-unit grid with a 1.6 stroke, so every glyph in
 * the app shares one weight and rhythm (and we avoid shipping material-icons-extended).
 */
object AfarIcons {
    val Camera = icon("camera") {
        roundRect(3f, 7f, 18f, 13f, 3.2f)
        moveTo(8.2f, 7f); lineTo(9.6f, 4.6f); lineTo(14.4f, 4.6f); lineTo(15.8f, 7f)
        circle(12f, 13.4f, 3.4f)
    }
    val Remote = icon("remote") {
        roundRect(6.5f, 2.5f, 11f, 19f, 2.8f)
        moveTo(10.5f, 18.2f); lineTo(13.5f, 18.2f)
        circle(12f, 10.5f, 2.6f)
    }
    val Close = icon("close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }
    val Back = icon("back") {
        moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f)
    }
    val Arrow = icon("arrow") {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }
    val Grid = icon("grid") {
        roundRect(4f, 4f, 16f, 16f, 2.4f)
        moveTo(9.33f, 4f); lineTo(9.33f, 20f)
        moveTo(14.67f, 4f); lineTo(14.67f, 20f)
        moveTo(4f, 9.33f); lineTo(20f, 9.33f)
        moveTo(4f, 14.67f); lineTo(20f, 14.67f)
    }
    val Level = icon("level") {
        moveTo(2.5f, 12f); lineTo(8.5f, 12f)
        moveTo(15.5f, 12f); lineTo(21.5f, 12f)
        circle(12f, 12f, 2.6f)
    }
    val Mirror = icon("mirror") {
        moveTo(12f, 3f); lineTo(12f, 5f)
        moveTo(12f, 8f); lineTo(12f, 10f)
        moveTo(12f, 13f); lineTo(12f, 15f)
        moveTo(12f, 18f); lineTo(12f, 20.5f)
        moveTo(8.8f, 7f); lineTo(3.5f, 17f); lineTo(8.8f, 17f); close()
        moveTo(15.2f, 7f); lineTo(20.5f, 17f); lineTo(15.2f, 17f); close()
    }
    val Burst = icon("burst") {
        roundRect(3.5f, 8f, 12.5f, 12.5f, 2.2f)
        moveTo(7.5f, 5.5f); lineTo(16.5f, 5.5f); arcTo(2f, 2f, 0f, false, true, 18.5f, 7.5f); lineTo(18.5f, 16.5f)
        moveTo(11f, 3f); lineTo(19f, 3f); arcTo(2f, 2f, 0f, false, true, 21f, 5f); lineTo(21f, 13f)
    }
    val Timer = icon("timer") {
        circle(12f, 13.5f, 7.8f)
        moveTo(12f, 13.5f); lineTo(12f, 9.3f)
        moveTo(9.5f, 2.8f); lineTo(14.5f, 2.8f)
        moveTo(18.6f, 6.6f); lineTo(19.8f, 5.4f)
    }
    val Flip = icon("flip") {
        moveTo(4.2f, 10.5f); arcTo(8f, 8f, 0f, false, true, 18.6f, 7.2f)
        moveTo(19.2f, 3.8f); lineTo(18.8f, 7.4f); lineTo(15.3f, 7f)
        moveTo(19.8f, 13.5f); arcTo(8f, 8f, 0f, false, true, 5.4f, 16.8f)
        moveTo(4.8f, 20.2f); lineTo(5.2f, 16.6f); lineTo(8.7f, 17f)
    }
    val Check = icon("check") {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
    }
    val Trash = icon("trash") {
        moveTo(4f, 6.8f); lineTo(20f, 6.8f)
        moveTo(9.2f, 6.8f); lineTo(9.2f, 4.4f); lineTo(14.8f, 4.4f); lineTo(14.8f, 6.8f)
        moveTo(6.2f, 6.8f); lineTo(7.1f, 19.2f); arcTo(1.6f, 1.6f, 0f, false, false, 8.7f, 20.6f)
        lineTo(15.3f, 20.6f); arcTo(1.6f, 1.6f, 0f, false, false, 16.9f, 19.2f); lineTo(17.8f, 6.8f)
        moveTo(10.2f, 10.5f); lineTo(10.2f, 16.8f)
        moveTo(13.8f, 10.5f); lineTo(13.8f, 16.8f)
    }
    val Lock = icon("lock") {
        roundRect(5f, 10.5f, 14f, 10.5f, 2.6f)
        moveTo(8.2f, 10.5f); lineTo(8.2f, 7.6f); arcTo(3.8f, 3.8f, 0f, false, true, 15.8f, 7.6f); lineTo(15.8f, 10.5f)
        moveTo(12f, 14.6f); lineTo(12f, 16.8f)
    }
    val Qr = icon("qr") {
        roundRect(3.5f, 3.5f, 7f, 7f, 1.5f)
        roundRect(13.5f, 3.5f, 7f, 7f, 1.5f)
        roundRect(3.5f, 13.5f, 7f, 7f, 1.5f)
        moveTo(14f, 14f); lineTo(16.5f, 14f)
        moveTo(20f, 14f); lineTo(20.5f, 14f)
        moveTo(14f, 17.5f); lineTo(14f, 20.5f)
        moveTo(17.5f, 17.5f); lineTo(20.5f, 17.5f); lineTo(20.5f, 20.5f)
    }
    val Info = icon("info") {
        circle(12f, 12f, 8.8f)
        moveTo(12f, 11f); lineTo(12f, 16.5f)
        moveTo(12f, 7.7f); lineTo(12f, 7.8f)
    }
    val Retake = icon("retake") {
        moveTo(19.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 16.8f, 6.2f)
        moveTo(17.5f, 2.8f); lineTo(17.2f, 6.6f); lineTo(13.4f, 6.4f)
    }
    val Sun = icon("sun") {
        circle(12f, 12f, 3.8f)
        moveTo(12f, 2.8f); lineTo(12f, 4.6f)
        moveTo(12f, 19.4f); lineTo(12f, 21.2f)
        moveTo(2.8f, 12f); lineTo(4.6f, 12f)
        moveTo(19.4f, 12f); lineTo(21.2f, 12f)
        moveTo(5.5f, 5.5f); lineTo(6.8f, 6.8f)
        moveTo(17.2f, 17.2f); lineTo(18.5f, 18.5f)
        moveTo(5.5f, 18.5f); lineTo(6.8f, 17.2f)
        moveTo(17.2f, 6.8f); lineTo(18.5f, 5.5f)
    }
    val Settings = icon("settings") {
        moveTo(4f, 7f); lineTo(14f, 7f); moveTo(18f, 7f); lineTo(20f, 7f)
        circle(16f, 7f, 2f)
        moveTo(4f, 17f); lineTo(6f, 17f); moveTo(10f, 17f); lineTo(20f, 17f)
        circle(8f, 17f, 2f)
    }
    val Pin = icon("pin") {
        moveTo(12f, 21f)
        curveTo(12f, 21f, 5f, 14.6f, 5f, 9.6f)
        arcTo(7f, 7f, 0f, false, true, 19f, 9.6f)
        curveTo(19f, 14.6f, 12f, 21f, 12f, 21f)
        close()
        circle(12f, 9.6f, 2.4f)
    }
}

private fun icon(name: String, stroke: Float = 1.6f, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathBuilder().apply(block).nodes,
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = stroke,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    lineTo(x + w, y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    lineTo(x + r, y + h)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    lineTo(x, y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}
