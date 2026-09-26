package app.holdthatpose.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import app.holdthatpose.data.Prefs
import app.holdthatpose.media.Beeper
import app.holdthatpose.media.PhotoStore
import app.holdthatpose.net.CameraStatus
import app.holdthatpose.net.Cmd
import app.holdthatpose.net.Connection
import app.holdthatpose.net.Lens
import app.holdthatpose.net.LinkQuality
import app.holdthatpose.net.NearbyLink
import app.holdthatpose.net.Peer
import app.holdthatpose.net.Protocol
import app.holdthatpose.net.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import app.holdthatpose.net.PhotoHeader
import kotlin.math.max

/** 0…4 bars from link type and round-trip time; a Bluetooth-only link tops out at 2. */
fun signalBars(live: Boolean, quality: LinkQuality, rtt: Long?): Int {
    if (!live) return 0
    val byRtt = when {
        rtt == null -> 1
        rtt < 60 -> 4
        rtt < 140 -> 3
        rtt < 300 -> 2
        else -> 1
    }
    return if (quality == LinkQuality.Low) byRtt.coerceAtMost(2) else byRtt
}

/** One photo received from the Camera. [image] is a screen-sized copy for review. */
data class Shot(val id: String, val uri: Uri?, val image: Bitmap, val takenAt: Long)

/** A delete the user can still undo; [index] is where the shot was in the list. */
data class PendingDelete(val shot: Shot, val index: Int, val alsoCamera: Boolean)

/** Live preview frame plus the Camera's tilt, already decoded for drawing. */
data class LiveFrame(val bitmap: Bitmap, val roll: Float, val bumped: Boolean, val lowQuality: Boolean, val front: Boolean)

sealed interface LinkPhase {
    data object Idle : LinkPhase
    data object Live : LinkPhase
    /**
     * Link dropped; retrying for up to 30 s. [secondsLeft] counts down for the UI.
     * [awaitingAllow] = the Camera no longer knows us and someone has to tap Allow there.
     */
    data class Reconnecting(val secondsLeft: Int, val awaitingAllow: Boolean = false) : LinkPhase
    data object Lost : LinkPhase
    /** The Camera closed the session on purpose (idle timeout / stopped there). No reconnect. */
    data class Ended(val reason: String) : LinkPhase
}

/**
 * The phone in your hand: shows the stream, sends shutter/lens/focus, keeps the shutter
 * queued through short drops and saves every returned photo to this phone's gallery too.
 */
class RemoteSession(
    private val context: Context,
    private val link: NearbyLink,
    private val prefs: Prefs,
    private val store: PhotoStore,
    private val beeper: Beeper,
) {
    private var scope: CoroutineScope? = null
    private var reconnectJob: Job? = null
    /** Outlives [scope] so a delete chosen just before leaving still happens. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True while the Remote role is active (survives the activity being recreated). */
    val isRunning: Boolean get() = scope != null

    private val _frame = MutableStateFlow<LiveFrame?>(null)
    val frame: StateFlow<LiveFrame?> = _frame.asStateFlow()

    private val _status = MutableStateFlow<CameraStatus?>(null)
    val status: StateFlow<CameraStatus?> = _status.asStateFlow()

    private val _rtt = MutableStateFlow<Long?>(null)
    val rtt: StateFlow<Long?> = _rtt.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    private val _shots = MutableStateFlow<List<Shot>>(emptyList())
    val shots: StateFlow<List<Shot>> = _shots.asStateFlow()

    private val _pendingDelete = MutableStateFlow<PendingDelete?>(null)
    val pendingDelete: StateFlow<PendingDelete?> = _pendingDelete.asStateFlow()

    private val _awaitingPhoto = MutableStateFlow(false)
    val awaitingPhoto: StateFlow<Boolean> = _awaitingPhoto.asStateFlow()

    private val _phase = MutableStateFlow<LinkPhase>(LinkPhase.Idle)
    val phase: StateFlow<LinkPhase> = _phase.asStateFlow()

    private val _queuedShutter = MutableStateFlow<Int?>(null)
    val queuedShutter: StateFlow<Int?> = _queuedShutter.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private var lastPongAt = 0L
    private var lastKeepAlive = 0L
    private var countdownWatchdog: Job? = null
    private var awaitJob: Job? = null
    private var deleteJob: Job? = null
    /** Cancel tapped during a drop; sent first thing after reconnecting. */
    private var queuedCancel = false

    // Photo intake: only photos we asked for are saved.
    private var requested = 0
    private val captured = mutableSetOf<String>()
    private val received = mutableSetOf<String>()

    val peerName: String? get() = (link.connection.value as? Connection.Connected)?.peer?.name ?: prefs.lastPeerName

    /** Starts looking for Cameras. Connecting always takes a tap; the known Camera skips the code check. */
    fun startPairing() {
        link.autoAccept = { peer, _ -> peer.installId == prefs.lastPeerId }
        link.startDiscovery()
    }

    fun connect(peer: Peer) {
        link.connect(peer, Role.Remote)
    }

    /** Called once the link is live and the Remote screen is showing. */
    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        _phase.value = LinkPhase.Live
        link.stopDiscovery()
        (link.connection.value as? Connection.Connected)?.peer?.let {
            prefs.lastPeerId = it.installId
            prefs.lastPeerName = it.name
        }
        link.send(Cmd.Hello(link.deviceName, prefs.effectiveIdleTimeout))

        s.launch { link.commands.collect { onCommand(it) } }
        s.launch {
            var count = 0
            var windowStart = SystemClock.elapsedRealtime()
            link.frames.collectLatest { msg ->
                val bmp = withContext(Dispatchers.Default) { decodeFrame(msg) } ?: return@collectLatest
                _frame.value = LiveFrame(
                    bitmap = bmp,
                    roll = msg.roll,
                    bumped = msg.flags and Protocol.FLAG_BUMPED != 0,
                    lowQuality = msg.flags and Protocol.FLAG_LOW_QUALITY != 0,
                    front = msg.flags and Protocol.FLAG_FRONT != 0,
                )
                count++
                val now = SystemClock.elapsedRealtime()
                if (now - windowStart >= 1000) {
                    _fps.value = count
                    count = 0
                    windowStart = now
                }
            }
        }
        s.launch { link.photos.collect { (header, jpeg) -> onPhoto(header, jpeg) } }
        s.launch {
            link.connection.collect { c ->
                when (c) {
                    is Connection.Connected -> onReconnected()
                    Connection.None -> if (_phase.value == LinkPhase.Live) beginReconnect()
                    is Connection.Pending -> Unit
                }
            }
        }
        s.launch {
            while (isActive) {
                if (link.send(Cmd.Ping(SystemClock.elapsedRealtime()))) {
                    if (lastPongAt != 0L && SystemClock.elapsedRealtime() - lastPongAt > 6_000) _rtt.value = null
                }
                delay(2_000)
            }
        }
        s.launch {
            // Frames stop arriving → fade the fps readout so the UI can say so.
            while (isActive) {
                delay(1_500)
                if (_phase.value != LinkPhase.Live) _fps.value = 0
            }
        }
    }

    fun stop() {
        // A delete still inside its undo window was the user's choice: finish it.
        deleteJob?.cancel()
        deleteJob = null
        _pendingDelete.value?.let {
            _pendingDelete.value = null
            commitDelete(it)
        }
        reconnectJob?.cancel()
        scope?.cancel()
        scope = null
        link.stopAll()
        _phase.value = LinkPhase.Idle
        _frame.value = null
        _status.value = null
        _countdown.value = null
        _queuedShutter.value = null
        _rtt.value = null
        _awaitingPhoto.value = false
        _shots.value = emptyList()
        countdownWatchdog?.cancel()
        awaitJob?.cancel()
        queuedCancel = false
        requested = 0
        captured.clear()
        received.clear()
    }

    // region Commands to the Camera

    fun shutter(timer: Int, burst: Boolean) {
        if (_countdown.value != null) {
            if (_phase.value is LinkPhase.Reconnecting) {
                // Can't reach the Camera right now: cancel as soon as we're back.
                queuedCancel = true
                _queuedShutter.value = null
                _countdown.value = null
                countdownWatchdog?.cancel()
                _notices.tryEmit("Cancel queued")
            } else {
                link.send(Cmd.CancelCountdown)
            }
            return
        }
        val st = _status.value
        if (st != null && !st.storageOk) {
            _notices.tryEmit("Camera storage full")
            return
        }
        beeper.haptic(strong = true)
        if (_phase.value is LinkPhase.Reconnecting) {
            _queuedShutter.value = timer
            _notices.tryEmit("Shutter queued")
            return
        }
        sendShutter(timer, burst)
    }

    private fun sendShutter(timer: Int, burst: Boolean): Boolean {
        if (!link.send(Cmd.Shutter(timer, burst))) return false
        requested++
        // Optimistic countdown so the UI reacts instantly; the Camera's ticks keep it honest.
        _countdown.value = if (timer > 0) timer else 0
        // If the Camera's ticks never arrive, don't leave the shutter stuck in "cancel" mode.
        countdownWatchdog?.cancel()
        countdownWatchdog = scope?.launch {
            delay((timer + 6) * 1_000L)
            _countdown.value = null
        }
        return true
    }

    fun setLens(lens: Lens) {
        link.send(Cmd.SetLens(lens))
        _status.update { it?.copy(lens = lens) }
    }

    fun focus(x: Float, y: Float) {
        link.send(Cmd.Focus(x, y))
    }

    /**
     * Removes [shot] from review now and deletes it after [UNDO_MS] unless [undoDelete] is
     * called. [alsoCamera] also asks the Camera to delete its copy.
     */
    fun scheduleDelete(shot: Shot, alsoCamera: Boolean) {
        deleteJob?.cancel()
        deleteJob = null
        _pendingDelete.value?.let {
            _pendingDelete.value = null
            commitDelete(it)
        }
        val index = _shots.value.indexOfFirst { it.id == shot.id }
        if (index < 0) return
        _shots.update { list -> list.filterNot { it.id == shot.id } }
        val pending = PendingDelete(shot, index, alsoCamera)
        _pendingDelete.value = pending
        deleteJob = scope?.launch {
            delay(UNDO_MS)
            if (_pendingDelete.value === pending) {
                _pendingDelete.value = null
                commitDelete(pending)
            }
        }
    }

    fun undoDelete() {
        val pending = _pendingDelete.value ?: return
        deleteJob?.cancel()
        deleteJob = null
        _pendingDelete.value = null
        _shots.update { list ->
            list.toMutableList().apply { add(pending.index.coerceIn(0, size), pending.shot) }
        }
    }

    private fun commitDelete(pending: PendingDelete) {
        pending.shot.uri?.let { uri -> io.launch { store.delete(uri) } }
        if (pending.alsoCamera && !link.send(Cmd.Delete(pending.shot.id))) {
            _notices.tryEmit("Camera copy kept")
        }
    }

    /** "Keep going" on the idle warning: counts as activity on the Camera. */
    fun keepAlive() {
        link.send(Cmd.KeepAlive)
        lastKeepAlive = SystemClock.elapsedRealtime()
        // Hide the warning now; the Camera's next status confirms the new countdown.
        _status.update { it?.copy(idleLeft = -1) }
    }

    /**
     * Any touch on the Remote screen means someone is actively framing, so it counts as
     * activity for the Camera's auto-disconnect (throttled to one message per 5 s).
     */
    fun userActive() {
        if (SystemClock.elapsedRealtime() - lastKeepAlive >= 5_000 && _phase.value == LinkPhase.Live) keepAlive()
    }

    fun retryConnection() {
        beginReconnect()
    }

    // endregion

    private fun decodeFrame(msg: Protocol.FrameMsg): Bitmap? = runCatching {
        val length = msg.jpeg.size - msg.offset
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(msg.jpeg, msg.offset, length, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || max(bounds.outWidth, bounds.outHeight) > MAX_FRAME_SIDE) {
            null
        } else {
            BitmapFactory.decodeByteArray(msg.jpeg, msg.offset, length)
        }
    }.getOrNull()

    private suspend fun onPhoto(header: PhotoHeader, jpeg: ByteArray) {
        val id = header.id
        if (!acceptPhoto(id, received, captured, requested)) {
            // A resend of something we already have: confirm again so the Camera stops sending.
            if (id in received) link.send(Cmd.PhotoAck(id))
            return
        }
        val thumb = withContext(Dispatchers.Default) {
            runCatching {
                val isJpeg = jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (isJpeg) BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
                val sane = isJpeg && bounds.outWidth > 0 && bounds.outHeight > 0 &&
                    max(bounds.outWidth, bounds.outHeight) <= MAX_PHOTO_SIDE
                if (sane) PhotoStore.decodeScaled(jpeg, REVIEW_SIDE) else null
            }.getOrNull()
        }
        if (thumb == null || !store.hasSpace()) {
            setAwaiting(false)
            _notices.tryEmit("Photo saved on Camera")
            return
        }
        val saved = withContext(Dispatchers.IO) { store.saveToGallery(jpeg, PhotoStore.fileName(header.takenAt)) }
        received += id
        link.send(Cmd.PhotoAck(id))
        _shots.update { (listOf(Shot(id, saved, thumb, header.takenAt)) + it).take(MAX_SHOTS) }
        setAwaiting(false)
        beeper.haptic()
    }

    /** "Saving photo…" never outlives [AWAIT_PHOTO_TIMEOUT_MS]; the Camera keeps its copy anyway. */
    private fun setAwaiting(waiting: Boolean) {
        _awaitingPhoto.value = waiting
        awaitJob?.cancel()
        awaitJob = null
        if (waiting) {
            awaitJob = scope?.launch {
                delay(AWAIT_PHOTO_TIMEOUT_MS)
                _awaitingPhoto.value = false
                _notices.tryEmit("Photo saved on Camera")
            }
        }
    }

    private fun onCommand(cmd: Cmd) {
        when (cmd) {
            is Cmd.Status -> _status.value = cmd.status
            is Cmd.Pong -> {
                lastPongAt = SystemClock.elapsedRealtime()
                _rtt.value = lastPongAt - cmd.ts
            }
            is Cmd.Countdown -> when {
                cmd.remaining < 0 -> _countdown.value = null
                cmd.remaining == 0 -> {
                    _countdown.value = 0
                    setAwaiting(true)
                    beeper.haptic(strong = true)
                    scope?.launch {
                        delay(700)
                        if (_countdown.value == 0) _countdown.value = null
                    }
                }
                else -> {
                    _countdown.value = cmd.remaining
                    beeper.tick()
                    beeper.haptic()
                }
            }
            is Cmd.Captured -> {
                captured += cmd.id
                setAwaiting(true)
            }
            is Cmd.Deleted -> if (!cmd.ok) _notices.tryEmit("Camera copy kept")
            is Cmd.SessionEnded -> {
                reconnectJob?.cancel()
                _countdown.value = null
                _queuedShutter.value = null
                queuedCancel = false
                _phase.value = LinkPhase.Ended(cmd.reason)
                // The Camera may be mid-disconnect already; make sure we don't linger or retry.
                link.disconnect()
            }
            is Cmd.ShutterRejected -> {
                _countdown.value = null
                setAwaiting(false)
                _notices.tryEmit(cmd.reason)
            }
            else -> Unit
        }
    }

    /**
     * Keeps trying to get back to the last Camera for 30 s: find it, connect, wait; on failure
     * wait a moment and try again. If the Camera asks for Allow (its re-entry window has
     * passed), the Remote says so and waits at least another 30 s for someone to tap it.
     */
    private fun beginReconnect() {
        val s = scope ?: return
        reconnectJob?.cancel()
        reconnectJob = s.launch {
            var deadline = SystemClock.elapsedRealtime() + RECONNECT_MS
            var extended = false
            val target = prefs.lastPeerId
            link.autoAccept = { peer, _ -> peer.installId == prefs.lastPeerId }
            val ticker = launch {
                while (isActive) {
                    val c = link.connection.value
                    val awaitingAllow = c is Connection.Pending && c.accepted && c.code.isNotEmpty()
                    if (awaitingAllow && !extended) {
                        extended = true
                        deadline = maxOf(deadline, SystemClock.elapsedRealtime() + RECONNECT_MS)
                    }
                    val left = ((deadline - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0)
                    _phase.value = LinkPhase.Reconnecting(left, awaitingAllow)
                    delay(500)
                }
            }
            var connected = false
            while (target != null && SystemClock.elapsedRealtime() < deadline) {
                link.startDiscovery()
                val found = withTimeoutOrNull((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)) {
                    link.discovered.first { list -> list.any { it.installId == target } }
                        .first { it.installId == target }
                } ?: continue
                if (link.connection.value is Connection.None) link.connect(found, Role.Remote)
                val wait = minOf(deadline - SystemClock.elapsedRealtime(), 8_000L).coerceAtLeast(1_000L)
                val result = withTimeoutOrNull(wait) {
                    link.connection.first { it is Connection.Connected || it is Connection.None }
                }
                if (result is Connection.Connected) {
                    connected = true
                    break
                }
                if (result == Connection.None) delay(1_500)
            }
            ticker.cancel()
            if (connected || link.connection.value is Connection.Connected) return@launch
            link.stopDiscovery()
            link.disconnect()
            _phase.value = LinkPhase.Lost
            _queuedShutter.value = null
            queuedCancel = false
        }
    }

    private fun onReconnected() {
        if (_phase.value == LinkPhase.Live) return
        reconnectJob?.cancel()
        link.stopDiscovery()
        _phase.value = LinkPhase.Live
        link.send(Cmd.Hello(link.deviceName, prefs.effectiveIdleTimeout))
        if (queuedCancel) {
            queuedCancel = false
            link.send(Cmd.CancelCountdown)
        }
        _queuedShutter.value?.let { timer ->
            _queuedShutter.value = null
            sendShutter(timer, prefs.burst)
        }
    }

    fun signalBars(quality: LinkQuality, rtt: Long?): Int = signalBars(_phase.value == LinkPhase.Live, quality, rtt)

    private companion object {
        const val MAX_SHOTS = 12
        const val MAX_FRAME_SIDE = 2048
        const val MAX_PHOTO_SIDE = 8192
        const val REVIEW_SIDE = 1080
        const val UNDO_MS = 5_000L
        const val RECONNECT_MS = 30_000L
    }
}
