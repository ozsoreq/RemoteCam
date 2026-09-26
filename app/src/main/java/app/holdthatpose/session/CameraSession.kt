package app.holdthatpose.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import app.holdthatpose.data.Prefs
import app.holdthatpose.media.AlarmGuard
import app.holdthatpose.media.Beeper
import app.holdthatpose.media.PhotoStore
import app.holdthatpose.net.CameraStatus
import app.holdthatpose.net.Cmd
import app.holdthatpose.net.Connection
import app.holdthatpose.net.Lens
import app.holdthatpose.net.LinkQuality
import app.holdthatpose.net.NearbyLink
import app.holdthatpose.net.PhotoHeader
import app.holdthatpose.net.Protocol
import app.holdthatpose.net.Role
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val alarm = AlarmGuard(context)

    private var scope: CoroutineScope? = null

    /** True while the Camera role is active (survives the activity being recreated). */
    val isRunning: Boolean get() = scope != null

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

    private val _capLeft = MutableStateFlow(-1)
    /** Safe mode: seconds left to tap Continue after the session limit; -1 when not asking. */
    val capLeft: StateFlow<Int> = _capLeft.asStateFlow()

    private val _sessionSec = MutableStateFlow(0)
    /** Seconds since the current Remote connected (for the LIVE timer). */
    val sessionSec: StateFlow<Int> = _sessionSec.asStateFlow()

    private val _muted = MutableStateFlow(false)
    /** The alarm stream is silent, so countdown beeps and chimes won't be heard. */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _ended = MutableStateFlow<String?>(null)
    /** Why the last session ended; while non-null the Camera stays hidden until restarted. */
    val ended: StateFlow<String?> = _ended.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short messages for the Camera screen ("Deleted by Remote", "Couldn't save"). */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /**
     * Remote currently in session. Only it may silently reconnect, and only within
     * [REENTRY_WINDOW_MS] of a drop; after that it is forgotten and needs Allow again.
     */
    private var sessionRemoteId: String? = null
    private var reentryJob: Job? = null
    private var remoteIdleTimeout = 0
    private var lastActivity = 0L

    /** When the current Remote first connected (LIVE timer). 0 = no session. */
    private var sessionStartedAt = 0L
    /** Base of the safe-mode session limit; moved on Continue and while a shot is running. */
    private var capStartedAt = 0L
    private var capChimed = false
    private var lastCheckAt = 0L

    private var countdownJob: Job? = null
    private val outbox = PhotoOutbox()
    private val registry = ShotRegistry<Uri>()
    private var lastLensAt = 0L
    private var lastFocusAt = 0L

    // Frame pipeline state (analysis thread only).
    private var lastFrameAt = 0L
    private var jpegQuality = 70
    private val jpegOut = ByteArrayOutputStream(64 * 1024)
    private var framesThisSecond = 0
    private var fpsWindowStart = 0L
    @Volatile private var fps = 0
    @Volatile private var lowPower = false
    @Volatile private var hot = false

    private val connectedPeerId: String?
        get() = (link.connection.value as? Connection.Connected)?.peer?.installId

    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        // A new Remote always needs Allow on this phone, whatever the safe-mode setting. Only
        // the Remote already in session may come back silently (within the re-entry window).
        link.autoAccept = { peer, _ ->
            peer.role == Role.Remote && peer.installId.isNotEmpty() && peer.installId == sessionRemoteId
        }
        _ended.value = null
        alarm.engage()
        _muted.value = alarm.muted
        link.startAdvertising(Role.Camera)
        level.start()
        controller.frameSink = ::onFrame
        controller.onError = { link.send(Cmd.ShutterRejected(it)) }

        // Burst files left behind by a crash or kill mid-capture.
        s.launch(Dispatchers.IO) {
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("shot-") && it.name.endsWith(".jpg") }
                ?.forEach { it.delete() }
        }

        s.launch {
            link.connection.collect { c ->
                when (c) {
                    is Connection.Connected -> onConnected(c)
                    Connection.None -> {
                        _idleLeft.value = -1
                        _capLeft.value = -1
                        SessionService.update(context, Role.Camera, null)
                        if (_ended.value == null) {
                            link.startAdvertising(Role.Camera)
                            val id = sessionRemoteId
                            if (id != null && reentryJob?.isActive != true) {
                                link.preferredPeerId = id
                                reentryJob = s.launch {
                                    delay(REENTRY_WINDOW_MS)
                                    forgetRemote()
                                }
                            }
                        }
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
                val urgent = checkIdle()
                // Normally every 2 s; every second while a warning or the "Still OK?" check runs.
                if (urgent || tick % 2 == 0) sendStatus()
                tick++
                delay(1_000)
            }
        }
    }

    private fun onConnected(c: Connection.Connected) {
        link.stopAdvertising()
        reentryJob?.cancel()
        reentryJob = null
        link.preferredPeerId = null
        val now = SystemClock.elapsedRealtime()
        val returning = c.peer.installId.isNotEmpty() && c.peer.installId == sessionRemoteId
        if (returning) {
            beeper.softChime()
        } else {
            // A different Remote: nothing from the previous one may reach it.
            beeper.chime(up = true)
            outbox.clear()
            registry.clear()
            remoteIdleTimeout = 0
            sessionStartedAt = now
            capStartedAt = now
            capChimed = false
        }
        sessionRemoteId = c.peer.installId
        prefs.lastPeerId = c.peer.installId
        prefs.lastPeerName = c.peer.name
        lastActivity = now
        outbox.newLink()
        SessionService.update(context, Role.Camera, "LIVE · ${c.peer.name}")
        sendStatus()
        flushPhotos()
    }

    /** The in-session Remote is gone for good: drop its queue, its photo ids and its trust. */
    private fun forgetRemote() {
        sessionRemoteId = null
        link.preferredPeerId = null
        outbox.clear()
        registry.clear()
        remoteIdleTimeout = 0
        sessionStartedAt = 0L
        capStartedAt = 0L
        capChimed = false
        _sessionSec.value = 0
    }

    /** Stricter of this phone's and the Remote's auto-disconnect settings. */
    private val idleTimeout: Int
        get() = stricterTimeout(prefs.effectiveIdleTimeout, remoteIdleTimeout)

    /**
     * Runs every second: auto-disconnect after [idleTimeout] s without a deliberate Remote
     * command, and (safe mode) the 30-min "Still OK?" check. Returns true while either warns.
     */
    private fun checkIdle(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val delta = if (lastCheckAt == 0L) 0L else now - lastCheckAt
        lastCheckAt = now
        _muted.value = alarm.muted
        val busy = countdownJob?.isActive == true || _saving.value
        val connected = link.connection.value is Connection.Connected
        _sessionSec.value = if (connected && sessionStartedAt > 0) ((now - sessionStartedAt) / 1000).toInt() else 0
        if (!connected) {
            _idleLeft.value = -1
            _capLeft.value = -1
            return false
        }

        // Session limit (safe mode only). Never ends a shot: while busy the clock doesn't run.
        var capWarning = false
        if (prefs.safeMode && capStartedAt > 0) {
            if (busy) capStartedAt += delta
            val cap = capLeft(now - capStartedAt, SESSION_CAP_MS, CAP_GRACE_MS)
            _capLeft.value = cap
            if (cap >= 0 && !capChimed) {
                capChimed = true
                beeper.chime(up = true)
            }
            if (cap == 0) {
                endSession("Session limit reached")
                return false
            }
            capWarning = cap >= 0
        } else {
            _capLeft.value = -1
        }

        val timeout = idleTimeout
        if (timeout <= 0) {
            _idleLeft.value = -1
            return capWarning
        }
        // A running countdown or capture is activity.
        if (busy) lastActivity = now
        val left = timeout - ((now - lastActivity) / 1000).toInt()
        _idleLeft.value = left.coerceAtLeast(0)
        if (left <= 0) {
            endSession("Ended after ${formatDuration(timeout)} without activity")
            return false
        }
        return capWarning || left <= WARNING_SECONDS
    }

    /** "Continue" on the Camera's "Still OK?" card: restarts the session limit. */
    fun continueSession() {
        val now = SystemClock.elapsedRealtime()
        capStartedAt = now
        capChimed = false
        lastActivity = now
        _capLeft.value = -1
        sendStatus()
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
        _capLeft.value = -1
        _ended.value = reason
        reentryJob?.cancel()
        reentryJob = null
        forgetRemote()
        link.stopAdvertising()
        link.send(Cmd.SessionEnded(reason))
        beeper.chime(up = false)
        SessionService.update(context, Role.Camera, null)
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

    /** "Try again" after advertising failed to start. */
    fun retryAdvertising() {
        link.clearError()
        link.stopAdvertising()
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
        reentryJob?.cancel()
        reentryJob = null
        forgetRemote()
        _idleLeft.value = -1
        _capLeft.value = -1
        scope?.cancel()
        scope = null
        controller.frameSink = null
        controller.detach()
        level.stop()
        alarm.release()
        lastCheckAt = 0L
        if (wasConnected) {
            val gen = link.generation
            Handler(Looper.getMainLooper()).postDelayed({ link.stopAllIf(gen) }, 350)
        } else {
            link.stopAll()
        }
        _countdown.value = null
    }

    private fun onCommand(cmd: Cmd) {
        val now = SystemClock.elapsedRealtime()
        if (countsAsActivity(cmd)) {
            lastActivity = now
            // Clear the "Disconnecting in…" warning on the Remote right away.
            if (_idleLeft.value in 0..WARNING_SECONDS) {
                _idleLeft.value = idleTimeout
                sendStatus()
            }
        }
        when (cmd) {
            is Cmd.Hello -> remoteIdleTimeout = cmd.idleTimeout
            Cmd.KeepAlive -> Unit
            is Cmd.Shutter -> shutter(cmd.timer, cmd.burst)
            Cmd.CancelCountdown -> cancelCountdown()
            is Cmd.SetLens -> {
                val busy = countdownJob?.isActive == true || _saving.value
                if (busy || now - lastLensAt < 1_000) {
                    // Refused: the status makes the Remote put its lens picker back.
                    sendStatus()
                } else {
                    lastLensAt = now
                    controller.setLens(cmd.lens)
                }
            }
            is Cmd.Focus -> if (now - lastFocusAt >= 100) {
                lastFocusAt = now
                controller.focusUpright(cmd.x.coerceIn(0f, 1f), cmd.y.coerceIn(0f, 1f))
            }
            is Cmd.Ping -> link.send(Cmd.Pong(cmd.ts))
            is Cmd.PhotoAck -> outbox.ack(cmd.id)
            is Cmd.Delete -> deleteForRemote(cmd.id)
            else -> Unit
        }
    }

    /** A Remote may delete only the photos it asked for in this session. */
    private fun deleteForRemote(id: String) {
        val s = scope ?: return
        val taken = registry.takeIfOwned(id, connectedPeerId)
        outbox.ack(id)
        s.launch {
            val uri = taken?.uri
            val ok = if (uri != null) withContext(Dispatchers.IO) { store.delete(uri) } else false
            link.send(Cmd.Deleted(id, ok))
            if (ok) _notices.tryEmit("Deleted by Remote")
        }
    }

    fun shutter(timer: Int, burst: Boolean) {
        val s = scope ?: return
        if (countdownJob?.isActive == true) {
            val counting = (_countdown.value ?: 0) > 0
            link.send(Cmd.ShutterRejected(if (counting) "Countdown already running" else "Still saving the last photo"))
            return
        }
        if (!store.hasSpace()) {
            link.send(Cmd.ShutterRejected("Camera phone storage is full"))
            return
        }
        if (!controller.bound.value || controller.paused) {
            link.send(Cmd.ShutterRejected("Camera is paused — check the Camera phone"))
            return
        }
        val seconds = Protocol.clampTimer(timer)
        countdownJob = s.launch {
            for (n in seconds downTo 1) {
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
        val owner = sessionRemoteId
        val takenAt = System.currentTimeMillis()
        beeper.shutter()
        _captureFlash.value++
        _saving.value = true
        val count = if (burst) 3 else 1
        val files = List(count) { File(context.cacheDir, "shot-$id-$it.jpg") }
        try {
            // A lost CameraX callback must never keep "saving" (and the session) alive forever.
            val finished = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                captureAndSave(id, owner, takenAt, files)
                true
            }
            if (finished == null) link.send(Cmd.ShutterRejected("The camera couldn't take the photo"))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { files.forEach { it.delete() } }
            _saving.value = false
        }
    }

    private suspend fun captureAndSave(id: String, owner: String?, takenAt: Long, files: List<File>) {
        val ok = coroutineScope {
            files.mapIndexed { i, f ->
                async {
                    delay(i * 300L)
                    withTimeoutOrNull(SHOT_TIMEOUT_MS) { controller.takePicture(f) } ?: false
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
        val name = PhotoStore.fileName(takenAt)
        val uri = withContext(Dispatchers.IO) { store.saveToGallery(best, name) }
        if (uri == null) {
            // Keep the full-res photo in app storage rather than losing it with the burst files.
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "unsaved").apply { mkdirs() }
                    best.copyTo(File(dir, "$name.jpg"), overwrite = true)
                }
            }
            link.send(Cmd.ShutterRejected("Couldn't save on the Camera"))
            _notices.tryEmit("Couldn't save")
            return
        }
        if (owner != null) registry.put(id, uri, owner)
        link.send(Cmd.Captured(id))
        _lastSaved.value = takenAt

        val review = withContext(Dispatchers.Default) { PhotoStore.reviewCopy(best) }
        // Only the Remote that asked for it, and only while it's still in session.
        if (review != null && owner != null && owner == sessionRemoteId) {
            outbox.add(owner, PhotoHeader(id, review.second, review.third, takenAt), review.first)
            flushPhotos()
        }
    }

    /** Sends review copies to their owner; unacknowledged ones go again on the next link. */
    private fun flushPhotos() {
        val owner = sessionRemoteId ?: return
        if (connectedPeerId != owner) return
        for (entry in outbox.due(owner)) {
            if (!link.sendPhoto(entry.header, entry.bytes)) break
            outbox.markSent(entry.header.id)
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
                    muted = _muted.value,
                    capLeft = _capLeft.value,
                    sessionSec = _sessionSec.value,
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
        if (controller.lens.value == Lens.Front) flags = flags or Protocol.FLAG_FRONT

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
