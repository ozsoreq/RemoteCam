package app.holdthatpose

import app.holdthatpose.data.Prefs
import app.holdthatpose.net.LinkQuality
import app.holdthatpose.session.formatDuration
import app.holdthatpose.session.signalBars
import app.holdthatpose.session.stricterTimeout
import app.holdthatpose.session.tiltFromGravity
import app.holdthatpose.session.uprightToBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicTest {

    // ── Auto-disconnect rules ─────────────────────────────

    @Test
    fun safeModeNeverAllowsOff() {
        assertEquals(60, Prefs.effectiveTimeout(safeMode = true, configured = 0))
        assertEquals(120, Prefs.effectiveTimeout(safeMode = true, configured = 120))
        assertEquals(0, Prefs.effectiveTimeout(safeMode = false, configured = 0))
        assertEquals(30, Prefs.effectiveTimeout(safeMode = false, configured = 30))
    }

    @Test
    fun cameraEnforcesTheStricterTimeout() {
        assertEquals(30, stricterTimeout(30, 600))
        assertEquals(30, stricterTimeout(600, 30))
        assertEquals(60, stricterTimeout(0, 60)) // "off" on one phone never wins
        assertEquals(60, stricterTimeout(60, 0))
        assertEquals(0, stricterTimeout(0, 0))
    }

    @Test
    fun durationsReadNaturally() {
        assertEquals("30 s", formatDuration(30))
        assertEquals("1 min", formatDuration(60))
        assertEquals("10 min", formatDuration(600))
        assertEquals("Off", formatDuration(0))
    }

    // ── Tap-to-focus mapping ──────────────────────────────

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 1e-6f)
        assertEquals(expected.second, actual.second, 1e-6f)
    }

    @Test
    fun focusPointMapsBackToSensor() {
        // Top-left of the upright frame…
        assertPoint(0f to 0f, uprightToBuffer(0f, 0f, 0))
        assertPoint(0f to 1f, uprightToBuffer(0f, 0f, 90))   // sensor rotated 90° cw → its bottom-left
        assertPoint(1f to 1f, uprightToBuffer(0f, 0f, 180))
        assertPoint(1f to 0f, uprightToBuffer(0f, 0f, 270))
        // …and the centre never moves.
        listOf(0, 90, 180, 270).forEach { assertPoint(0.5f to 0.5f, uprightToBuffer(0.5f, 0.5f, it)) }
    }

    @Test
    fun focusMappingInvertsTheBufferRotation() {
        // Rotating buffer point (bx, by) by 90° cw gives upright (1 - by, bx); mapping back must return it.
        val bx = 0.2f
        val by = 0.7f
        assertPoint(bx to by, uprightToBuffer(1 - by, bx, 90))
        assertPoint(bx to by, uprightToBuffer(by, 1 - bx, 270))
        assertPoint(bx to by, uprightToBuffer(1 - bx, 1 - by, 180))
    }

    // ── Level / rotation ──────────────────────────────────

    @Test
    fun uprightPhoneIsLevel() {
        val (roll, quadrant) = tiltFromGravity(0f, 9.81f)
        assertEquals(0f, roll, 1e-4f)
        assertEquals(0, quadrant)
    }

    @Test
    fun smallTiltIsReportedInDegrees() {
        val g = 9.81f
        val a = Math.toRadians(5.0)
        val (roll, quadrant) = tiltFromGravity((g * Math.sin(a)).toFloat(), (g * Math.cos(a)).toFloat())
        assertEquals(5f, roll, 1e-3f)
        assertEquals(0, quadrant)
    }

    @Test
    fun landscapeQuadrantsMatchSurfaceRotation() {
        // Right edge up (rotated counter-clockwise) = Surface.ROTATION_90.
        assertEquals(90, tiltFromGravity(9.81f, 0f).second)
        // Left edge up (rotated clockwise) = Surface.ROTATION_270.
        assertEquals(270, tiltFromGravity(-9.81f, 0f).second)
        assertEquals(180, tiltFromGravity(0f, -9.81f).second)
        assertEquals(0f, tiltFromGravity(9.81f, 0f).first, 1e-4f)
    }

    // ── Signal bars ───────────────────────────────────────

    @Test
    fun signalBarsFollowLatency() {
        assertEquals(4, signalBars(true, LinkQuality.High, 20))
        assertEquals(3, signalBars(true, LinkQuality.High, 100))
        assertEquals(2, signalBars(true, LinkQuality.High, 200))
        assertEquals(1, signalBars(true, LinkQuality.High, 900))
        assertEquals(1, signalBars(true, LinkQuality.High, null))
        assertEquals(0, signalBars(false, LinkQuality.High, 20))
    }

    @Test
    fun bluetoothOnlyLinkCapsAtTwoBars() {
        assertEquals(2, signalBars(true, LinkQuality.Low, 20))
    }
}
