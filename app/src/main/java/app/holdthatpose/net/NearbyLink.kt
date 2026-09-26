package app.holdthatpose.net

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.holdthatpose.session.PENDING_TIMEOUT_MS
import app.holdthatpose.session.linkErrorMessage
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

enum class Role(val code: Char) { Camera('C'), Remote('R') }

/** A phone running Hold That Pose that we can see or are talking to. */
data class Peer(val endpointId: String, val role: Role, val installId: String, val name: String)

sealed interface Connection {
    data object None : Connection

    /** Nearby handshake started; [code] is the 4-digit token both screens display. */
    data class Pending(val peer: Peer, val code: String, val incoming: Boolean, val accepted: Boolean) : Connection
    data class Connected(val peer: Peer) : Connection
}

enum class LinkQuality { Unknown, Low, Medium, High }

/**
 * Thin, flow-based wrapper around Google Nearby Connections.
 *
 * P2P_POINT_TO_POINT finds the other phone over Bluetooth and then upgrades the link to
 * Wi-Fi Direct / hotspot when it can — no router, no internet, no accounts. If the upgrade
 * fails the link stays on Bluetooth and [quality] reports [LinkQuality.Low], which the
 * Camera uses to drop the preview to a low-res, low-fps stream.
 */
class NearbyLink(context: Context, private val installId: String) {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val deviceName: String = runCatching {
        Settings.Global.getString(context.contentResolver, "device_name")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: prettyModel()

    private val _connection = MutableStateFlow<Connection>(Connection.None)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _discovered = MutableStateFlow<List<Peer>>(emptyList())
    val discovered: StateFlow<List<Peer>> = _discovered.asStateFlow()

    private val _advertising = MutableStateFlow(false)
    val advertising: StateFlow<Boolean> = _advertising.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _quality = MutableStateFlow(LinkQuality.Unknown)
    val quality: StateFlow<LinkQuality> = _quality.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _commands = MutableSharedFlow<Cmd>(extraBufferCapacity = 64)
    val commands: SharedFlow<Cmd> = _commands.asSharedFlow()

    /** Newest frame wins; a slow consumer never builds up lag. */
    private val _frames = MutableSharedFlow<Protocol.FrameMsg>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<Protocol.FrameMsg> = _frames.asSharedFlow()

    private val _photos = MutableSharedFlow<Pair<PhotoHeader, ByteArray>>(extraBufferCapacity = 8)
    val photos: SharedFlow<Pair<PhotoHeader, ByteArray>> = _photos.asSharedFlow()

    private val _disconnects = MutableSharedFlow<Peer>(extraBufferCapacity = 4)
    val disconnects: SharedFlow<Peer> = _disconnects.asSharedFlow()

    /** Decides whether an incoming or outgoing handshake can skip the confirm step. */
    var autoAccept: (peer: Peer, incoming: Boolean) -> Boolean = { _, _ -> false }

    /**
     * Install id of the Remote allowed to replace an unconfirmed stranger's handshake (the
     * in-session Remote coming back after a drop). Set by the Camera session.
     */
    @Volatile var preferredPeerId: String? = null

    /** Bumped by every start; lets a delayed [stopAllIf] skip if a new session started meanwhile. */
    @Volatile var generation = 0
        private set

    /** Which side this phone is on in the current session (set on start / connect). */
    @Volatile private var localRole: Role? = null

    private val pendingPeers = mutableMapOf<String, Peer>()
    /** Handshakes this phone rejected itself, so their failure isn't shown as "declined". */
    private val rejectedLocally = mutableSetOf<String>()
    private val frameInFlight = AtomicLong(0L)
    @Volatile private var frameSentAt = 0L
    private val main = Handler(Looper.getMainLooper())

    fun localName(role: Role) = "${role.code}|$installId|$deviceName"

    // region Advertising / discovery

    fun startAdvertising(role: Role) {
        localRole = role
        generation++
        if (_advertising.value) return
        _advertising.value = true
        client.startAdvertising(
            localName(role), Protocol.SERVICE_ID, lifecycle,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(),
        ).addOnFailureListener { e ->
            if ((e as? com.google.android.gms.common.api.ApiException)?.statusCode != ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                _advertising.value = false
                fail("Couldn't start advertising", e)
            }
        }
    }

    fun stopAdvertising() {
        client.stopAdvertising()
        _advertising.value = false
    }

    fun startDiscovery() {
        generation++
        if (_discovering.value) return
        _discovering.value = true
        _discovered.value = emptyList()
        client.startDiscovery(
            Protocol.SERVICE_ID, discovery,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build(),
        ).addOnFailureListener { e ->
            if ((e as? com.google.android.gms.common.api.ApiException)?.statusCode != ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                _discovering.value = false
                fail("Couldn't search for nearby phones", e)
            }
        }
    }

    fun stopDiscovery() {
        client.stopDiscovery()
        _discovering.value = false
    }

    // endregion

    // region Connection

    fun connect(peer: Peer, role: Role) {
        if (_connection.value !is Connection.None) return
        localRole = role
        generation++
        pendingPeers[peer.endpointId] = peer
        setPending(Connection.Pending(peer, code = "", incoming = false, accepted = false))
        client.requestConnection(localName(role), peer.endpointId, lifecycle)
            .addOnFailureListener { e ->
                val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                if (code != ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    if ((_connection.value as? Connection.Pending)?.peer?.endpointId == peer.endpointId) {
                        _connection.value = Connection.None
                    }
                    fail("Couldn't reach ${peer.name}", e)
                }
            }
    }

    /** User confirmed the 4-digit code. */
    fun acceptPending() {
        val pending = _connection.value as? Connection.Pending ?: return
        if (pending.accepted) return
        setPending(pending.copy(accepted = true))
        client.acceptConnection(pending.peer.endpointId, payloads)
    }

    fun rejectPending() {
        val pending = _connection.value as? Connection.Pending ?: return
        rejectedLocally += pending.peer.endpointId
        if (pending.code.isEmpty() || pending.accepted) {
            // Nothing to reject (our request is still in flight) or we already accepted: hang up.
            client.disconnectFromEndpoint(pending.peer.endpointId)
        } else {
            client.rejectConnection(pending.peer.endpointId)
        }
        _connection.value = Connection.None
    }

    /**
     * Every handshake state goes through here so it gets a timeout: if nobody confirms within
     * [PENDING_TIMEOUT_MS], the handshake is dropped (the same Pending must still be current).
     */
    private fun setPending(p: Connection.Pending) {
        _connection.value = p
        main.postDelayed({
            if (_connection.value === p) {
                rejectedLocally += p.peer.endpointId
                client.disconnectFromEndpoint(p.peer.endpointId)
                _connection.value = Connection.None
            }
        }, PENDING_TIMEOUT_MS)
    }

    fun disconnect() {
        when (val c = _connection.value) {
            is Connection.Connected -> client.disconnectFromEndpoint(c.peer.endpointId)
            is Connection.Pending -> client.disconnectFromEndpoint(c.peer.endpointId)
            Connection.None -> Unit
        }
        _connection.value = Connection.None
        _quality.value = LinkQuality.Unknown
    }

    fun stopAll() {
        localRole = null
        client.stopAllEndpoints()
        _advertising.value = false
        _discovering.value = false
        _connection.value = Connection.None
        _discovered.value = emptyList()
        _quality.value = LinkQuality.Unknown
        frameInFlight.set(0)
    }

    /** [stopAll], unless something started a new advert/discovery/connect since [gen] was read. */
    fun stopAllIf(gen: Int) {
        if (gen == generation) stopAll()
    }

    fun clearError() {
        _error.value = null
    }

    // endregion

    // region Sending

    private val connectedId: String?
        get() = (_connection.value as? Connection.Connected)?.peer?.endpointId

    fun send(cmd: Cmd): Boolean {
        val id = connectedId ?: return false
        client.sendPayload(id, Payload.fromBytes(Protocol.encodeCmd(cmd)))
        return true
    }

    /**
     * Sends a preview frame only if the previous one has been delivered (or timed out), so
     * frames never queue up in front of commands and glass-to-glass lag stays flat.
     */
    fun trySendFrame(encoded: ByteArray): Boolean {
        val id = connectedId ?: return false
        val now = System.currentTimeMillis()
        if (frameInFlight.get() != 0L && now - frameSentAt < 1_000) return false
        if (encoded.size > ConnectionsClient.MAX_BYTES_DATA_SIZE) return false
        val payload = Payload.fromBytes(encoded)
        frameInFlight.set(payload.id)
        frameSentAt = now
        client.sendPayload(id, payload).addOnFailureListener { frameInFlight.set(0) }
        return true
    }

    fun canSendFrame(): Boolean =
        connectedId != null && (frameInFlight.get() == 0L || System.currentTimeMillis() - frameSentAt >= 1_000)

    fun sendPhoto(header: PhotoHeader, jpeg: ByteArray): Boolean {
        val id = connectedId ?: return false
        client.sendPayload(id, Payload.fromStream(ByteArrayInputStream(Protocol.photoStreamBytes(header, jpeg))))
        return true
    }

    // endregion

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peer = parsePeer(endpointId, info.endpointName) ?: return
            if (peer.installId == installId) return
            _discovered.update { list -> (list.filterNot { it.endpointId == endpointId || it.installId == peer.installId }) + peer }
        }

        override fun onEndpointLost(endpointId: String) {
            _discovered.update { list -> list.filterNot { it.endpointId == endpointId } }
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val peer = parsePeer(endpointId, info.endpointName)
                ?: pendingPeers[endpointId]
                ?: Peer(endpointId, Role.Remote, "", info.endpointName)
            // One handshake at a time: a second phone can't overwrite the code on screen.
            when (val current = _connection.value) {
                is Connection.Connected -> if (current.peer.endpointId != endpointId) {
                    client.rejectConnection(endpointId)
                    return
                }
                is Connection.Pending -> if (current.peer.endpointId != endpointId) {
                    val strangerWaiting = !current.accepted && current.peer.installId != preferredPeerId
                    val returning = peer.installId.isNotEmpty() && peer.installId == preferredPeerId
                    if (strangerWaiting && returning) {
                        // The in-session Remote is back: it wins over an unconfirmed stranger.
                        rejectedLocally += current.peer.endpointId
                        client.rejectConnection(current.peer.endpointId)
                    } else {
                        client.rejectConnection(endpointId)
                        return
                    }
                }
                Connection.None -> Unit
            }
            pendingPeers[endpointId] = peer
            val auto = autoAccept(peer, info.isIncomingConnection)
            setPending(Connection.Pending(peer, info.authenticationDigits, info.isIncomingConnection, accepted = auto))
            if (auto) client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val peer = pendingPeers[endpointId]
            val isCurrent = (_connection.value as? Connection.Pending)?.peer?.endpointId == endpointId
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK && peer != null) {
                if (!isCurrent || endpointId in rejectedLocally) {
                    // Cancelled, timed out or replaced on this phone meanwhile: don't let it in.
                    rejectedLocally.remove(endpointId)
                    client.disconnectFromEndpoint(endpointId)
                    return
                }
                _connection.value = Connection.Connected(peer)
                frameInFlight.set(0)
            } else {
                if ((_connection.value as? Connection.Pending)?.peer?.endpointId == endpointId) {
                    _connection.value = Connection.None
                }
                val rejectedHere = rejectedLocally.remove(endpointId)
                if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED &&
                    !rejectedHere && localRole == Role.Remote
                ) {
                    _error.value = "Camera declined"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val peer = pendingPeers[endpointId]
            if ((_connection.value as? Connection.Connected)?.peer?.endpointId == endpointId ||
                (_connection.value as? Connection.Pending)?.peer?.endpointId == endpointId
            ) {
                _connection.value = Connection.None
            }
            _quality.value = LinkQuality.Unknown
            frameInFlight.set(0)
            if (peer != null) _disconnects.tryEmit(peer)
        }

        override fun onBandwidthChanged(endpointId: String, info: BandwidthInfo) {
            _quality.value = when (info.quality) {
                BandwidthInfo.Quality.HIGH -> LinkQuality.High
                BandwidthInfo.Quality.MEDIUM -> LinkQuality.Medium
                BandwidthInfo.Quality.LOW -> LinkQuality.Low
                else -> LinkQuality.Unknown
            }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> when (val msg = payload.asBytes()?.let(Protocol::decode)) {
                    is Protocol.FrameMsg -> _frames.tryEmit(msg)
                    is Protocol.CmdMsg -> _commands.tryEmit(msg.cmd)
                    null -> Unit
                }
                Payload.Type.STREAM -> {
                    // Only the Remote receives photos; a Camera never reads a pushed stream.
                    if (localRole == Role.Camera) {
                        client.cancelPayload(payload.id)
                        return
                    }
                    val input = payload.asStream()?.asInputStream() ?: return
                    scope.launch {
                        runCatching { input.use { Protocol.readPhotoStream(it) } }
                            .onSuccess { _photos.emit(it) }
                            .onFailure { Log.w(TAG, "photo stream failed", it) }
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status != PayloadTransferUpdate.Status.IN_PROGRESS) {
                frameInFlight.compareAndSet(update.payloadId, 0L)
            }
        }
    }

    private fun parsePeer(endpointId: String, name: String): Peer? {
        val parts = name.split('|', limit = 3)
        if (parts.size != 3 || parts[0].length != 1) return null
        val role = Role.entries.firstOrNull { it.code == parts[0][0] } ?: return null
        return Peer(endpointId, role, parts[1], parts[2])
    }

    private fun fail(message: String, e: Exception) {
        Log.w(TAG, message, e)
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        _error.value = linkErrorMessage(message, code)
    }

    private companion object {
        const val TAG = "NearbyLink"

        fun prettyModel(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL
            else "$manufacturer ${Build.MODEL}"
        }
    }
}
