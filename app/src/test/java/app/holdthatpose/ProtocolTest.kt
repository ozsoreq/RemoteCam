package app.holdthatpose

import app.holdthatpose.net.CameraStatus
import app.holdthatpose.net.Cmd
import app.holdthatpose.net.Lens
import app.holdthatpose.net.PhotoHeader
import app.holdthatpose.net.Protocol
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream

class ProtocolTest {

    private fun roundTrip(cmd: Cmd): Cmd? =
        (Protocol.decode(Protocol.encodeCmd(cmd)) as? Protocol.CmdMsg)?.cmd

    private fun decodeJson(json: String): Cmd? =
        (Protocol.decode(byteArrayOf(Protocol.TAG_CMD) + json.toByteArray(Charsets.UTF_8)) as? Protocol.CmdMsg)?.cmd

    @Test
    fun everyCommandSurvivesTheWire() {
        val all = listOf(
            Cmd.Hello("Pixel 8", 60),
            Cmd.KeepAlive,
            Cmd.Shutter(3, burst = true),
            Cmd.Shutter(0, burst = false),
            Cmd.CancelCountdown,
            Cmd.SetLens(Lens.Ultrawide),
            Cmd.SetLens(Lens.Front),
            Cmd.Focus(0.25f, 0.75f),
            Cmd.Ping(123456789L),
            Cmd.Delete("abc123"),
            Cmd.PhotoAck("abc123"),
            Cmd.Status(
                CameraStatus(
                    battery = 42, charging = true, hot = true, storageOk = false,
                    lens = Lens.Front, lenses = listOf(Lens.Ultrawide, Lens.Main, Lens.Front),
                    previewPaused = true, lowPower = true, burst = false, fps = 18,
                    idleLeft = 7, safeMode = false,
                    muted = true, capLeft = 42, sessionSec = 1_805,
                ),
            ),
            Cmd.Status(CameraStatus()),
            Cmd.Pong(99L),
            Cmd.Countdown(5),
            Cmd.Countdown(-1),
            Cmd.Captured("id1"),
            Cmd.ShutterRejected("Camera storage full"),
            Cmd.Deleted("id2"),
            Cmd.Deleted("id3", ok = false),
            Cmd.SessionEnded("Ended after 1 min without activity"),
        )
        all.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun unicodeSurvives() {
        val hello = Cmd.Hello("הטלפון של עוז", 30)
        assertEquals(hello, roundTrip(hello))
    }

    @Test
    fun frameRoundTrip() {
        val jpeg = ByteArray(1000) { (it * 7).toByte() }
        val flags = Protocol.FLAG_BUMPED or Protocol.FLAG_FRONT
        val msg = Protocol.decode(Protocol.encodeFrame(-3.5f, flags, jpeg)) as Protocol.FrameMsg
        assertEquals(-3.5f, msg.roll, 0f)
        assertEquals(flags, msg.flags)
        assertArrayEquals(jpeg, msg.jpeg.copyOfRange(msg.offset, msg.jpeg.size))
    }

    @Test
    fun framePayloadStaysSmall() {
        // 6-byte header on top of the JPEG, nothing else.
        assertEquals(1006, Protocol.encodeFrame(0f, 0, ByteArray(1000)).size)
    }

    @Test
    fun photoStreamRoundTrip() {
        val header = PhotoHeader("shot-1", 1600, 1200, 1_700_000_000_000)
        val jpeg = ByteArray(250_000) { it.toByte() }
        val (h, bytes) = Protocol.readPhotoStream(ByteArrayInputStream(Protocol.photoStreamBytes(header, jpeg)))
        assertEquals(header.copy(len = jpeg.size, crc = Protocol.crc(jpeg)), h)
        assertArrayEquals(jpeg, bytes)
    }

    private fun assertRejected(stream: ByteArray) {
        val ok = runCatching { Protocol.readPhotoStream(ByteArrayInputStream(stream)) }.isSuccess
        assertFalse("stream should have been rejected", ok)
    }

    @Test
    fun truncatedPhotoStreamThrows() {
        val stream = Protocol.photoStreamBytes(PhotoHeader("a", 1, 1, 0), ByteArray(10_000) { 7 })
        assertRejected(stream.copyOf(stream.size - 1))
        assertRejected(stream.copyOf(stream.size / 2))
    }

    @Test
    fun corruptedPhotoStreamThrows() {
        val stream = Protocol.photoStreamBytes(PhotoHeader("a", 1, 1, 0), ByteArray(10_000) { 7 })
        stream[stream.size - 10] = 8
        assertRejected(stream)
    }

    /** Just the length-prefixed JSON header, no body. */
    private fun rawHeader(header: JSONObject): ByteArray {
        val h = header.toString().toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(h.size)
            write(h)
            flush()
        }
        return out.toByteArray()
    }

    @Test
    fun oversizedLengthIsRejectedWithoutReadingTheBody() {
        val huge = JSONObject().put("id", "x").put("len", Protocol.MAX_PHOTO_BYTES + 1).put("crc", 0)
        val head = ByteArrayInputStream(rawHeader(huge))
        var bodyReads = 0L
        // An endless source: a reader that tried to consume the body would spin for a long time.
        val endless = object : InputStream() {
            override fun read(): Int {
                val b = head.read()
                if (b >= 0) return b
                bodyReads++
                return 0
            }
        }
        try {
            Protocol.readPhotoStream(endless)
            fail("oversized stream accepted")
        } catch (expected: IllegalArgumentException) {
            // good
        }
        assertTrue("read past the header: $bodyReads", bodyReads < 64 * 1024)
    }

    @Test
    fun missingOrZeroLengthIsRejected() {
        // Older senders without "len" are refused rather than read to EOF.
        assertRejected(rawHeader(JSONObject().put("id", "x")) + ByteArray(100))
        assertRejected(rawHeader(JSONObject().put("id", "x").put("len", 0).put("crc", 0)))
    }

    @Test
    fun timerIsClampedToKnownSteps() {
        fun wireTimer(t: Int): Int {
            val cmd = decodeJson(JSONObject().put("t", "shutter").put("timer", t).toString())
            return (cmd as Cmd.Shutter).timer
        }
        assertEquals(0, wireTimer(0))
        assertEquals(3, wireTimer(3))
        assertEquals(5, wireTimer(7))
        assertEquals(10, wireTimer(10))
        assertEquals(10, wireTimer(2_000_000_000))
        assertEquals(0, wireTimer(-3))
        assertEquals(0, wireTimer(2))
        assertEquals(5, Protocol.clampTimer(9))
    }

    @Test
    fun badFocusIsDroppedAndGoodFocusIsClamped() {
        assertNull(decodeJson("{\"t\":\"focus\"}"))
        assertNull(decodeJson("{\"t\":\"focus\",\"x\":0.5}"))
        assertNull(decodeJson("{\"t\":\"focus\",\"x\":\"NaN\",\"y\":0.5}"))
        assertNull(decodeJson("{\"t\":\"focus\",\"x\":\"abc\",\"y\":0.5}"))
        assertEquals(Cmd.Focus(1f, 0f), decodeJson("{\"t\":\"focus\",\"x\":7.5,\"y\":-2}"))
        assertEquals(Cmd.Focus(0.25f, 0.5f), decodeJson("{\"t\":\"focus\",\"x\":0.25,\"y\":0.5}"))
    }

    @Test
    fun olderPeersGetSafeDefaults() {
        assertEquals(Cmd.Deleted("a", ok = true), decodeJson("{\"t\":\"deleted\",\"id\":\"a\"}"))
        val st = (decodeJson("{\"t\":\"status\"}") as Cmd.Status).status
        assertFalse(st.muted)
        assertEquals(-1, st.capLeft)
        assertEquals(0, st.sessionSec)
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(Protocol.decode(ByteArray(0)))
        assertNull(Protocol.decode(byteArrayOf(9, 1, 2, 3)))
        assertNull(Protocol.decode(byteArrayOf(Protocol.TAG_CMD) + "not json".toByteArray()))
        assertNull(Protocol.decode(byteArrayOf(Protocol.TAG_CMD) + """{"t":"unknown"}""".toByteArray()))
        assertNull(Protocol.decode(byteArrayOf(Protocol.TAG_FRAME, 0, 0)))
    }

    @Test
    fun unknownLensFallsBackToMain() {
        assertEquals(Lens.Main, Lens.from("telephoto"))
        assertTrue(Lens.entries.all { Lens.from(it.wire) == it })
    }
}
