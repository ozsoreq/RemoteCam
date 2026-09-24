package app.afar

import app.afar.net.CameraStatus
import app.afar.net.Cmd
import app.afar.net.Lens
import app.afar.net.PhotoHeader
import app.afar.net.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolTest {

    private fun roundTrip(cmd: Cmd): Cmd? =
        (Protocol.decode(Protocol.encodeCmd(cmd)) as? Protocol.CmdMsg)?.cmd

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
            Cmd.Status(
                CameraStatus(
                    battery = 42, charging = true, hot = true, storageOk = false,
                    lens = Lens.Front, lenses = listOf(Lens.Ultrawide, Lens.Main, Lens.Front),
                    previewPaused = true, lowPower = true, burst = false, fps = 18,
                    idleLeft = 7, safeMode = false,
                ),
            ),
            Cmd.Pong(99L),
            Cmd.Countdown(5),
            Cmd.Countdown(-1),
            Cmd.Captured("id1"),
            Cmd.ShutterRejected("Camera storage full"),
            Cmd.Deleted("id2"),
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
        assertEquals(header, h)
        assertArrayEquals(jpeg, bytes)
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
