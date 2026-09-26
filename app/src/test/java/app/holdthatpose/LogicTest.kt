package app.holdthatpose

import app.holdthatpose.data.Prefs
import app.holdthatpose.net.CameraStatus
import app.holdthatpose.net.Cmd
import app.holdthatpose.net.Lens
import app.holdthatpose.net.LinkQuality
import app.holdthatpose.session.CAP_GRACE_MS
import app.holdthatpose.session.LinkCodes
import app.holdthatpose.session.SESSION_CAP_MS
import app.holdthatpose.session.acceptPhoto
import app.holdthatpose.session.capLeft
import app.holdthatpose.session.countsAsActivity
import app.holdthatpose.session.formatDuration
import app.holdthatpose.session.linkErrorMessage
import app.holdthatpose.session.signalBars
import app.holdthatpose.session.stricterTimeout
import app.holdthatpose.session.tiltFromGravity
import app.holdthatpose.session.uprightToBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    // ── Auto-disconnect rules ─────────────────────────────

    @Test
    fun safeModeNeverAllowsOff() {
        assertEquals(60, Prefs.effectiveTimeout(safeMode = true, configured = 0))
        assertEquals(120, Prefs.effectiveTimeout(safeMode = true, configured = 120))
        // Safe mode off: "off" no longer exists; the maximum is 10 min.
        assertEquals(600, Prefs.effectiveTimeout(safeMode = false, configured = 0))
        assertEquals(30, Prefs.effectiveTimeout(safeMode = false, configured = 30))
        assertEquals(600, Prefs.effectiveTimeout(safeMode = false, configured = 3_600))
        assertFalse(0 in Prefs.IDLE_CHOICES)
    }

    // ── Session cap ───────────────────────────────────────

    @Test
    fun capAsksOnlyAfterThirtyMinutes() {
        assertEquals(-1, capLeft(0, SESSION_CAP_MS, CAP_GRACE_MS))
        assertEquals(-1, capLeft(SESSION_CAP_MS - 1, SESSION_CAP_MS, CAP_GRACE_MS))
        assertEquals(60, capLeft(SESSION_CAP_MS, SESSION_CAP_MS, CAP_GRACE_MS))
        assertEquals(60, capLeft(SESSION_CAP_MS + 1, SESSION_CAP_MS, CAP_GRACE_MS))
        assertEquals(1, capLeft(SESSION_CAP_MS + CAP_GRACE_MS - 1, SESSION_CAP_MS, CAP_GRACE_MS))
        assertEquals(0, capLeft(SESSION_CAP_MS + CAP_GRACE_MS, SESSION_CAP_MS, CAP_GRACE_MS))
        assertEquals(0, capLeft(SESSION_CAP_MS * 5, SESSION_CAP_MS, CAP_GRACE_MS))
    }

    // ── What keeps a session alive ────────────────────────

    @Test
    fun onlyDeliberateCommandsCountAsActivity() {
        listOf(
            Cmd.Shutter(3, true), Cmd.CancelCountdown, Cmd.SetLens(Lens.Main), Cmd.Focus(0.5f, 0.5f),
            Cmd.KeepAlive, Cmd.Delete("x"),
        ).forEach { assertTrue("$it", countsAsActivity(it)) }
        listOf(
            Cmd.Ping(1), Cmd.Hello("x", 60), Cmd.Status(CameraStatus()), Cmd.PhotoAck("x"), Cmd.Pong(1),
        ).forEach { assertFalse("$it", countsAsActivity(it)) }
    }

    // ── Photo intake on the Remote ────────────────────────

    @Test
    fun photosAreAcceptedOnlyWhenAskedFor() {
        // Announced by Captured.
        assertTrue(acceptPhoto("a", received = emptySet(), captured = setOf("a"), requested = 1))
        // Link dropped at the moment of capture: no Captured, but we did press the shutter.
        assertTrue(acceptPhoto("a", received = emptySet(), captured = emptySet(), requested = 1))
        // Unsolicited: never asked for anything.
        assertFalse(acceptPhoto("a", received = emptySet(), captured = emptySet(), requested = 0))
        // More photos than shutters.
        assertFalse(acceptPhoto("b", received = setOf("a"), captured = emptySet(), requested = 1))
        // Duplicate (resent after a drop).
        assertFalse(acceptPhoto("a", received = setOf("a"), captured = setOf("a"), requested = 2))
        assertFalse(acceptPhoto("", received = emptySet(), captured = emptySet(), requested = 3))
    }

    // ── Link error messages ───────────────────────────────

    @Test
    fun linkErrorsAreActionable() {
        assertEquals("Couldn't search", linkErrorMessage("Couldn't search", null))
        assertEquals("Needs Google Play services", linkErrorMessage("x", LinkCodes.API_UNAVAILABLE))
        assertEquals("Needs Google Play services", linkErrorMessage("x", LinkCodes.SERVICE_MISSING))
        assertEquals("Needs Google Play services", linkErrorMessage("x", LinkCodes.SERVICE_VERSION_UPDATE_REQUIRED))
        assertEquals("Turn on Bluetooth and Wi-Fi", linkErrorMessage("x", LinkCodes.STATUS_RADIO_ERROR))
        assertEquals("Turn on Location", linkErrorMessage("x", LinkCodes.MISSING_SETTING_LOCATION_MUST_BE_ON))
        assertEquals("Permission needed", linkErrorMessage("x", 8030))
        assertEquals("Couldn't reach it", linkErrorMessage("Couldn't reach it", 13))
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
