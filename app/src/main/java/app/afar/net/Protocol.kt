package app.afar.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Wire format between the two phones.
 *
 * Every BYTES payload starts with a one-byte tag so commands and preview frames can share
 * the link without a second channel:
 *  - [TAG_CMD]   → UTF-8 JSON command (tiny, never queued behind video thanks to frame back-pressure)
 *  - [TAG_FRAME] → preview frame: roll (float32) · flags (u8) · JPEG bytes
 *
 * Photos travel as a STREAM payload: int32 header length · JSON header · JPEG bytes, so the
 * receiver never has to pair a stream with a separately-delivered metadata message.
 */
object Protocol {
    const val SERVICE_ID = "app.afar.camera.v1"

    const val TAG_CMD: Byte = 1
    const val TAG_FRAME: Byte = 2

    const val FLAG_BUMPED = 1
    const val FLAG_LOW_QUALITY = 2
    const val FLAG_FRONT = 4

    fun encodeCmd(cmd: Cmd): ByteArray {
        val json = cmd.toJson().toString().toByteArray(Charsets.UTF_8)
        return ByteArray(json.size + 1).also {
            it[0] = TAG_CMD
            System.arraycopy(json, 0, it, 1, json.size)
        }
    }

    fun encodeFrame(roll: Float, flags: Int, jpeg: ByteArray, jpegLength: Int = jpeg.size): ByteArray {
        val out = ByteBuffer.allocate(1 + 4 + 1 + jpegLength)
        out.put(TAG_FRAME).putFloat(roll).put(flags.toByte()).put(jpeg, 0, jpegLength)
        return out.array()
    }

    sealed interface Decoded
    data class FrameMsg(val roll: Float, val flags: Int, val jpeg: ByteArray, val offset: Int) : Decoded
    data class CmdMsg(val cmd: Cmd) : Decoded

    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.isEmpty()) return null
        return when (bytes[0]) {
            TAG_FRAME -> {
                if (bytes.size < 7) return null
                val buf = ByteBuffer.wrap(bytes, 1, 5)
                FrameMsg(roll = buf.float, flags = buf.get().toInt() and 0xFF, jpeg = bytes, offset = 6)
            }
            TAG_CMD -> runCatching {
                Cmd.fromJson(JSONObject(String(bytes, 1, bytes.size - 1, Charsets.UTF_8)))
            }.getOrNull()?.let { CmdMsg(it) }
            else -> null
        }
    }

    fun photoStreamBytes(header: PhotoHeader, jpeg: ByteArray): ByteArray {
        val h = header.toJson().toString().toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + h.size + jpeg.size).putInt(h.size).put(h).put(jpeg).array()
    }

    fun readPhotoStream(input: InputStream): Pair<PhotoHeader, ByteArray> {
        val data = DataInputStream(input.buffered())
        val headerLen = data.readInt()
        require(headerLen in 1..64_000) { "bad header" }
        val header = ByteArray(headerLen).also { data.readFully(it) }
        val jpeg = data.readBytes()
        return PhotoHeader.fromJson(JSONObject(String(header, Charsets.UTF_8))) to jpeg
    }
}

data class PhotoHeader(val id: String, val width: Int, val height: Int, val takenAt: Long) {
    fun toJson() = JSONObject().put("id", id).put("w", width).put("h", height).put("at", takenAt)

    companion object {
        fun fromJson(o: JSONObject) = PhotoHeader(o.getString("id"), o.optInt("w"), o.optInt("h"), o.optLong("at"))
    }
}

enum class Lens(val wire: String, val label: String) {
    Ultrawide("uw", "0.5×"),
    Main("main", "1×"),
    Front("front", "Front");

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: Main
    }
}

/** Camera health, sent every ~2 s so the Remote can show battery, heat and storage. */
data class CameraStatus(
    val battery: Int = -1,
    val charging: Boolean = false,
    val hot: Boolean = false,
    val storageOk: Boolean = true,
    val lens: Lens = Lens.Main,
    val lenses: List<Lens> = listOf(Lens.Main),
    val previewPaused: Boolean = false,
    val lowPower: Boolean = false,
    val burst: Boolean = true,
    val fps: Int = 0,
)

sealed interface Cmd {
    // Remote → Camera
    data class Hello(val name: String) : Cmd
    data class Shutter(val timer: Int, val burst: Boolean) : Cmd
    data object CancelCountdown : Cmd
    data class SetLens(val lens: Lens) : Cmd
    data class Focus(val x: Float, val y: Float) : Cmd
    data class Ping(val ts: Long) : Cmd
    data class Delete(val id: String) : Cmd

    // Camera → Remote
    data class Status(val status: CameraStatus) : Cmd
    data class Pong(val ts: Long) : Cmd
    data class Countdown(val remaining: Int) : Cmd
    data class Captured(val id: String) : Cmd
    data class ShutterRejected(val reason: String) : Cmd
    data class Deleted(val id: String) : Cmd

    fun toJson(): JSONObject = when (this) {
        is Hello -> obj("hello").put("name", name)
        is Shutter -> obj("shutter").put("timer", timer).put("burst", burst)
        CancelCountdown -> obj("cancel")
        is SetLens -> obj("lens").put("lens", lens.wire)
        is Focus -> obj("focus").put("x", x.toDouble()).put("y", y.toDouble())
        is Ping -> obj("ping").put("ts", ts)
        is Delete -> obj("delete").put("id", id)
        is Status -> obj("status").apply {
            put("bat", status.battery); put("chg", status.charging); put("hot", status.hot)
            put("sto", status.storageOk); put("lens", status.lens.wire)
            put("lenses", JSONArray(status.lenses.map { it.wire })); put("paused", status.previewPaused)
            put("lowp", status.lowPower); put("burst", status.burst); put("fps", status.fps)
        }
        is Pong -> obj("pong").put("ts", ts)
        is Countdown -> obj("count").put("n", remaining)
        is Captured -> obj("captured").put("id", id)
        is ShutterRejected -> obj("rejected").put("reason", reason)
        is Deleted -> obj("deleted").put("id", id)
    }

    companion object {
        private fun obj(type: String) = JSONObject().put("t", type)

        fun fromJson(o: JSONObject): Cmd? = when (o.optString("t")) {
            "hello" -> Hello(o.optString("name"))
            "shutter" -> Shutter(o.optInt("timer"), o.optBoolean("burst", true))
            "cancel" -> CancelCountdown
            "lens" -> SetLens(Lens.from(o.optString("lens")))
            "focus" -> Focus(o.optDouble("x").toFloat(), o.optDouble("y").toFloat())
            "ping" -> Ping(o.optLong("ts"))
            "delete" -> Delete(o.optString("id"))
            "status" -> Status(
                CameraStatus(
                    battery = o.optInt("bat", -1),
                    charging = o.optBoolean("chg"),
                    hot = o.optBoolean("hot"),
                    storageOk = o.optBoolean("sto", true),
                    lens = Lens.from(o.optString("lens")),
                    lenses = o.optJSONArray("lenses")?.let { a -> (0 until a.length()).map { Lens.from(a.getString(it)) } }
                        ?: listOf(Lens.Main),
                    previewPaused = o.optBoolean("paused"),
                    lowPower = o.optBoolean("lowp"),
                    burst = o.optBoolean("burst", true),
                    fps = o.optInt("fps"),
                ),
            )
            "pong" -> Pong(o.optLong("ts"))
            "count" -> Countdown(o.optInt("n"))
            "captured" -> Captured(o.optString("id"))
            "rejected" -> ShutterRejected(o.optString("reason"))
            "deleted" -> Deleted(o.optString("id"))
            else -> null
        }
    }
}
