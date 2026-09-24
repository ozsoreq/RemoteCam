package app.afar.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import app.afar.data.Prefs
import app.afar.media.Beeper
import app.afar.media.PhotoStore
import app.afar.net.CameraStatus
import app.afar.net.Cmd
import app.afar.net.Connection
import app.afar.net.Lens
import app.afar.net.LinkQuality
import app.afar.net.NearbyLink
import app.afar.net.Peer
import app.afar.net.Protocol
import app.afar.net.Role
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

/** One photo received from the Camera. */
data class Shot(val id: String, val uri: Uri?, val image: Bitmap, val takenAt: Long)

/** Live preview frame plus the Camera's tilt, already decoded for drawing. */
data class LiveFrame(val bitmap: Bitmap, val roll: Float, val bumped: Boolean, val lowQuality: Boolean, val front: Boolean)

sealed interface LinkPhase {
    data object Idle : LinkPhase
    data object Live : LinkPhase
    /** Link dropped; retrying for up to 30 s. [secondsLeft] counts down for the UI. */
    data class Reconnecting(val secondsLeft: Int) : LinkPhase
    data object Lost : LinkPhase
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

    private val _awaitingPhoto = MutableStateFlow(false)
    val awaitingPhoto: StateFlow<Boolean> = _awaitingPhoto.asStateFlow()

    private val _phase = MutableStateFlow<LinkPhase>(LinkPhase.Idle)
    val phase: StateFlow<LinkPhase> = _phase.asStateFlow()

    private val _queuedShutter = MutableStateFlow<Int?>(null)
    val queuedShutter: StateFlow<Int?> = _queuedShutter.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private var lastPongAt = 0L

    /** Set when the user deliberately disconnects, so pairing doesn't snap straight back. */
    var suppressAutoConnect = false
    val peerName: String? get() = (link.connection.value as? Connection.Connected)?.peer?.name ?: prefs.lastPeerName

    /** Starts looking for Cameras; the last paired one is reconnected without asking. */
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
        link.send(Cmd.Hello(link.deviceName))

        s.launch { link.commands.collect { onCommand(it) } }
        s.launch {
            var count = 0
            var windowStart = SystemClock.elapsedRealtime()
            link.frames.collectLatest { msg ->
                val bmp = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(msg.jpeg, msg.offset, msg.jpeg.size - msg.offset)
                } ?: return@collectLatest
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
        s.launch {
            link.photos.collect { (header, jpeg) ->
                val saved = withContext(Dispatchers.IO) { store.saveToGallery(jpeg, PhotoStore.fileName(header.takenAt)) }
                val bmp = withContext(Dispatchers.Default) { PhotoStore.decodeThumb(jpeg, 1600) } ?: return@collect
                _shots.update { listOf(Shot(header.id, saved, bmp, header.takenAt)) + it }
                _awaitingPhoto.value = false
                beeper.haptic()
            }
        }
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
    }

    // region Commands to the Camera

    fun shutter(timer: Int, burst: Boolean) {
        if (_countdown.value != null) {
            link.send(Cmd.CancelCountdown)
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
        if (link.send(Cmd.Shutter(timer, burst))) {
            // Optimistic countdown so the UI reacts instantly; the Camera's ticks keep it honest.
            _countdown.value = if (timer > 0) timer else 0
        }
    }

    fun setLens(lens: Lens) {
        link.send(Cmd.SetLens(lens))
        _status.update { it?.copy(lens = lens) }
    }

    fun focus(x: Float, y: Float) {
        link.send(Cmd.Focus(x, y))
    }

    fun delete(shot: Shot) {
        _shots.update { list -> list.filterNot { it.id == shot.id } }
        scope?.launch {
            shot.uri?.let { withContext(Dispatchers.IO) { store.delete(it) } }
        }
        if (!link.send(Cmd.Delete(shot.id))) {
            _notices.tryEmit("Deleted here · Camera copy kept")
        }
    }

    fun retryConnection() {
        beginReconnect()
    }

    // endregion

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
                    _awaitingPhoto.value = true
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
            is Cmd.Captured -> _awaitingPhoto.value = true
            is Cmd.ShutterRejected -> {
                _countdown.value = null
                _awaitingPhoto.value = false
                _notices.tryEmit(cmd.reason)
            }
            else -> Unit
        }
    }

    private fun beginReconnect() {
        val s = scope ?: return
        reconnectJob?.cancel()
        _countdown.value = null
        reconnectJob = s.launch {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            link.autoAccept = { peer, _ -> peer.installId == prefs.lastPeerId }
            link.startDiscovery()
            val ticker = launch {
                while (isActive) {
                    val left = ((deadline - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0)
                    _phase.value = LinkPhase.Reconnecting(left)
                    delay(1_000)
                }
            }
            val found = withTimeoutOrNull(30_000) {
                link.discovered.first { list -> list.any { it.installId == prefs.lastPeerId } }
                    .first { it.installId == prefs.lastPeerId }
            }
            if (found != null) {
                link.connect(found, Role.Remote)
                val connected = withTimeoutOrNull((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(3_000)) {
                    link.connection.first { it is Connection.Connected }
                }
                if (connected != null) {
                    ticker.cancel()
                    return@launch
                }
            }
            ticker.cancel()
            link.stopDiscovery()
            link.disconnect()
            _phase.value = LinkPhase.Lost
            _queuedShutter.value = null
        }
    }

    private fun onReconnected() {
        if (_phase.value == LinkPhase.Live) return
        reconnectJob?.cancel()
        link.stopDiscovery()
        _phase.value = LinkPhase.Live
        link.send(Cmd.Hello(link.deviceName))
        _queuedShutter.value?.let { timer ->
            _queuedShutter.value = null
            link.send(Cmd.Shutter(timer, prefs.burst))
            _countdown.value = timer
        }
    }

    /** 0…4 bars from link type and round-trip time. */
    fun signalBars(quality: LinkQuality, rtt: Long?): Int {
        if (_phase.value != LinkPhase.Live) return 0
        val byRtt = when {
            rtt == null -> 1
            rtt < 60 -> 4
            rtt < 140 -> 3
            rtt < 300 -> 2
            else -> 1
        }
        return if (quality == LinkQuality.Low) byRtt.coerceAtMost(2) else byRtt
    }
}
