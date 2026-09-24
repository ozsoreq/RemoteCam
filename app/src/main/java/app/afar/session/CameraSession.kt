package app.afar.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import app.afar.data.Prefs
import app.afar.media.Beeper
import app.afar.media.PhotoStore
import app.afar.net.CameraStatus
import app.afar.net.Cmd
import app.afar.net.Connection
import app.afar.net.LinkQuality
import app.afar.net.NearbyLink
import app.afar.net.PhotoHeader
import app.afar.net.Protocol
import app.afar.net.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Everything the phone on the rock does: advertise, accept the Remote, stream frames,
 * run the countdown, capture a mini-burst, keep the sharpest frame and ship a copy back.
 *
 * Lives in application scope (not a screen) so a link drop mid-countdown still captures,
 * saves, and syncs the photo once the Remote reconnects.
 */
const val WARNING_SECONDS = 10

fun formatDuration(seconds: Int): String = when {
    seconds <= 0 -> "Off"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "$seconds s"
}

/** The stricter (shorter) of two auto-disconnect settings; 0 means "off" and loses to any limit. */
fun stricterTimeout(a: Int, b: Int): Int = listOf(a, b).filter { it > 0 }.minOrNull() ?: 0

class CameraSession(
    private val context: Context,
    private val link: NearbyLink,
    private val prefs: Prefs,
    private val store: PhotoStore,
    private val beeper: Beeper,
) {
    val controller = CameraController(context)
    val level = LevelSensor(context)

    private var scope: CoroutineScope? = null

    private val _countdown = MutableStateFlow<Int?>(null)
    /** Seconds left, 0 at the moment of capture, null when idle. */
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    private val _captureFlash = MutableStateFlow(0)
    /** Increments on every capture so the UI can blink the screen. */
    val captureFlash: StateFlow<Int> = _captureFlash.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _lastSaved = MutableStateFlow<Long?>(null)
    val lastSaved: StateFlow<Long?> = _lastSaved.asStateFlow()

    private val _idleLeft = MutableStateFlow(-1)
    /** Seconds until an idle session is dropped; -1 when no timeout is running. */
    val idleLeft: StateFlow<Int> = _idleLeft.asStateFlow()

    private val _ended = MutableStateFlow<String?>(null)
    /** Why the last session ended; while non-null the Camera stays hidden until restarted. */
    val ended: StateFlow<String?> = _ended.asStateFlow()

    /** Remote currently in session; only it may silently reconnect after a short drop. */
    private var sessionRemoteId: String? = null
    private var remoteIdleTimeout = 0
    private var lastActivity = 0L

    private var countdownJob: Job? = null
    private val pendingPhotos = ArrayDeque<Pair<PhotoHeader, ByteArray>>()
    private val shots = mutableMapOf<String, Uri>()

    // Frame pipeline state (analysis thread only).
    private var lastFrameAt = 0L
    private var jpegQuality = 70
    private val jpegOut = ByteArrayOutputStream(64 * 1024)
    private var framesThisSecond = 0
    private var fpsWindowStart = 0L
    @Volatile private var fps = 0
    @Volatile private var lowPower = false
    @Volatile private var hot = false

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        // Safe mode: a new Remote needs an explicit Accept on this phone. Only the Remote
        // already in session may reconnect silently after a short drop.
        link.autoAccept = { peer, _ ->
            peer.role == Role.Remote && (!prefs.safeMode || peer.installId == sessionRemoteId)
        }
        _ended.value = null
        link.startAdvertising(Role.Camera)
        level.start()
        controller.frameSink = ::onFrame
        controller.onError = { link.send(Cmd.ShutterRejected(it)) }

        s.launch {
            link.connection.collect { c ->
                when (c) {
                    is Connection.Connected -> {
                        link.stopAdvertising()
                        if (sessionRemoteId != c.peer.installId && prefs.safeMode) beeper.chime(up = true)
                        sessionRemoteId = c.peer.installId
                        prefs.lastPeerId = c.peer.installId
                        prefs.lastPeerName = c.peer.name
                        lastActivity = SystemClock.elapsedRealtime()
                        sendStatus()
                        flushPhotos()
                    }
                    Connection.None -> {
                        _idleLeft.value = -1
                        if (_ended.value == null) link.startAdvertising(Role.Camera)
                    }
                    is Connection.Pending -> Unit
                }
            }
        }
        s.launch { link.commands.collect { onCommand(it) } }
        // Tell the Remote right away when the lens actually changes (or falls back).
        s.launch { controller.lens.collect { sendStatus() } }
        s.launch {
            while (isActive) {
                controller.setDeviceRotation(level.quadrant)
                delay(250)
            }
        }
        s.launch {
            var tick = 0
            while (isActive) {
                val warning = checkIdle()
                // Normally every 2 s; every second while the "disconnecting in…" warning runs.
                if (warning || tick % 2 == 0) sendStatus()
                tick++
                delay(1_000)
            }
        }
    }

    /** Stricter of this phone's and the Remote's auto-disconnect settings (0 = off). */
    private val idleTimeout: Int
        get() = stricterTimeout(prefs.effectiveIdleTimeout, remoteIdleTimeout)

    /** Drops the session after [idleTimeout] s without a Remote command. Returns true while warning. */
    private fun checkIdle(): Boolean {
        val timeout = idleTimeout
        if (link.connection.value !is Connection.Connected || timeout <= 0) {
            _idleLeft.value = -1
            return false
        }
        // A running countdown or capture is activity.
        if (countdownJob?.isActive == true || _saving.value) lastActivity = SystemClock.elapsedRealtime()
        val left = timeout - ((SystemClock.elapsedRealtime() - lastActivity) / 1000).toInt()
        _idleLeft.value = left.coerceAtLeast(0)
        if (left <= 0) {
            endSession("Ended after ${formatDuration(timeout)} without activity")
            return false
        }
        return left <= WARNING_SECONDS
    }

    /**
     * Closes the session from the Camera side: tells the Remote not to reconnect, drops the
     * link and stops advertising until someone restarts it on this phone.
     */
    fun endSession(reason: String) {
        val s = scope ?: return
        countdownJob?.cancel()
        _countdown.value = null
        _idleLeft.value = -1
        _ended.value = reason
        sessionRemoteId = null
        remoteIdleTimeout = 0
        link.stopAdvertising()
        link.send(Cmd.SessionEnded(reason))
        if (prefs.safeMode) beeper.chime(up = false)
        s.launch {
            delay(400) // let the goodbye reach the Remote
            link.disconnect()
        }
    }

    /** Makes the Camera discoverable again after a session ended. */
    fun restart() {
        _ended.value = null
        link.startAdvertising(Role.Camera)
    }

    fun acceptRemote() = link.acceptPending()

    fun declineRemote() = link.rejectPending()

    fun stop() {
        // Tell the Remote this was deliberate so it doesn't try to reconnect, and give the
        // message a moment to leave before the link is torn down (unless a new session starts).
        val wasConnected = link.connection.value is Connection.Connected
        if (wasConnected) link.send(Cmd.SessionEnded("The Camera was closed"))
        countdownJob?.cancel()
        sessionRemoteId = null
        remoteIdleTimeout = 0
        _idleLeft.value = -1
        scope?.cancel()
        scope = null
        controller.frameSink = null
        controller.detach()
        level.stop()
        if (wasConnected) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (scope == null) link.stopAll() }, 350)
        } else {
            link.stopAll()
        }
        _countdown.value = null
    }

    private fun onCommand(cmd: Cmd) {
        if (cmd !is Cmd.Ping) lastActivity = SystemClock.elapsedRealtime()
        when (cmd) {
            is Cmd.Hello -> remoteIdleTimeout = cmd.idleTimeout
            Cmd.KeepAlive -> Unit
            is Cmd.Shutter -> shutter(cmd.timer, cmd.burst)
            Cmd.CancelCountdown -> cancelCountdown()
            is Cmd.SetLens -> controller.setLens(cmd.lens)
            is Cmd.Focus -> controller.focusUpright(cmd.x.coerceIn(0f, 1f), cmd.y.coerceIn(0f, 1f))
            is Cmd.Ping -> link.send(Cmd.Pong(cmd.ts))
            is Cmd.Delete -> scope?.launch {
                val uri = shots.remove(cmd.id)
                if (uri != null) withContext(Dispatchers.IO) { store.delete(uri) }
                link.send(Cmd.Deleted(cmd.id))
            }
            else -> Unit
        }
    }

    fun shutter(timer: Int, burst: Boolean) {
        val s = scope ?: return
        if (countdownJob?.isActive == true) return
        if (!store.hasSpace()) {
            link.send(Cmd.ShutterRejected("Camera phone storage is full"))
            return
        }
        if (!controller.bound.value || controller.paused) {
            link.send(Cmd.ShutterRejected("Camera is paused — check the Camera phone"))
            return
        }
        countdownJob = s.launch {
            for (n in timer downTo 1) {
                _countdown.value = n
                link.send(Cmd.Countdown(n))
                beeper.tick()
                delay(1_000)
            }
            _countdown.value = 0
            link.send(Cmd.Countdown(0))
            // Once the countdown hits zero the photo is taken no matter what happens to the link.
            withContext(NonCancellable) { capture(burst) }
            _countdown.value = null
        }
    }

    private fun cancelCountdown() {
        if (_countdown.value == 0) return
        countdownJob?.cancel()
        _countdown.value = null
        link.send(Cmd.Countdown(-1))
    }

    private suspend fun capture(burst: Boolean) {
        val id = UUID.randomUUID().toString().take(10)
        val takenAt = System.currentTimeMillis()
        beeper.shutter()
        _captureFlash.value++
        _saving.value = true
        try {
            val count = if (burst) 3 else 1
            val files = List(count) { File(context.cacheDir, "shot-$id-$it.jpg") }
            val ok = coroutineScope {
                files.mapIndexed { i, f ->
                    async {
                        delay(i * 300L)
                        controller.takePicture(f)
                    }
                }.awaitAll()
            }
            val taken = files.filterIndexed { i, _ -> ok[i] }
            if (taken.isEmpty()) {
                link.send(Cmd.ShutterRejected("The camera couldn't take the photo"))
                return
            }
            val best = withContext(Dispatchers.Default) {
                if (taken.size == 1) taken[0] else taken.maxBy { PhotoStore.sharpness(it) }
            }
            val uri = withContext(Dispatchers.IO) { store.saveToGallery(best, PhotoStore.fileName(takenAt)) }
            if (uri != null) shots[id] = uri
            link.send(Cmd.Captured(id))
            _lastSaved.value = takenAt

            val review = withContext(Dispatchers.Default) { PhotoStore.reviewCopy(best) }
            withContext(Dispatchers.IO) { files.forEach { it.delete() } }
            if (review != null) {
                pendingPhotos.addLast(PhotoHeader(id, review.second, review.third, takenAt) to review.first)
                flushPhotos()
            }
        } finally {
            _saving.value = false
        }
    }

    /** Sends queued review copies; anything unsent waits for the next reconnect. */
    private fun flushPhotos() {
        while (pendingPhotos.isNotEmpty() && link.connection.value is Connection.Connected) {
            val (header, bytes) = pendingPhotos.first()
            if (!link.sendPhoto(header, bytes)) break
            pendingPhotos.removeFirst()
        }
    }

    private fun sendStatus() {
        if (link.connection.value !is Connection.Connected) return
        val h = readHealth(context)
        lowPower = h.battery in 0 until 5 && !h.charging
        hot = h.hot
        link.send(
            Cmd.Status(
                CameraStatus(
                    battery = h.battery,
                    charging = h.charging,
                    hot = h.hot,
                    storageOk = store.hasSpace(),
                    lens = controller.lens.value,
                    lenses = controller.lenses.value,
                    previewPaused = !controller.bound.value || controller.paused,
                    lowPower = lowPower,
                    fps = fps,
                    idleLeft = _idleLeft.value,
                    safeMode = prefs.safeMode,
                ),
            ),
        )
    }

    /**
     * Preview frames: upright, ~640 px (320 px on a Bluetooth-only link), JPEG whose quality
     * adapts to stay under the Nearby bytes-payload ceiling.
     */
    private fun onFrame(image: ImageProxy) {
        if (lowPower) return
        if (!link.canSendFrame()) return
        val lowLink = link.quality.value == LinkQuality.Low
        val targetFps = when {
            lowLink -> 8
            hot -> 10
            else -> 20
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 1000L / targetFps) return

        val longSide = if (lowLink) 320 else 640
        val src = image.toBitmap()
        val scale = longSide.toFloat() / max(src.width, src.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }
        val upright = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        jpegOut.reset()
        upright.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegOut)
        if (upright !== src) upright.recycle()
        src.recycle()

        val size = jpegOut.size()
        val limit = com.google.android.gms.nearby.connection.ConnectionsClient.MAX_BYTES_DATA_SIZE - 16
        if (size > limit) {
            jpegQuality = (jpegQuality - 12).coerceAtLeast(25)
            return
        }
        if (size > limit * 0.8) jpegQuality = (jpegQuality - 4).coerceAtLeast(25)
        else if (size < limit * 0.5) jpegQuality = (jpegQuality + 2).coerceAtMost(82)

        var flags = 0
        if (level.recentlyBumped) flags = flags or Protocol.FLAG_BUMPED
        if (lowLink) flags = flags or Protocol.FLAG_LOW_QUALITY
        if (controller.lens.value == app.afar.net.Lens.Front) flags = flags or Protocol.FLAG_FRONT

        val bytes = jpegOut.toByteArray()
        if (link.trySendFrame(Protocol.encodeFrame(level.roll, flags, bytes))) {
            lastFrameAt = now
            framesThisSecond++
            if (now - fpsWindowStart >= 1000) {
                fps = framesThisSecond
                framesThisSecond = 0
                fpsWindowStart = now
            }
        }
    }
}
