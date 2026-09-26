package app.holdthatpose.ui.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.holdthatpose.PoseApp
import app.holdthatpose.net.Connection
import app.holdthatpose.net.Peer
import app.holdthatpose.net.Role
import app.holdthatpose.session.SessionService
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.components.CodeDigits
import app.holdthatpose.ui.components.Glass
import app.holdthatpose.ui.components.GlassIconButton
import app.holdthatpose.ui.components.HSpace
import app.holdthatpose.ui.components.NoticePill
import app.holdthatpose.ui.components.Overline
import app.holdthatpose.ui.components.PrimaryButton
import app.holdthatpose.ui.components.SecondaryButton
import app.holdthatpose.ui.components.StatusDot
import app.holdthatpose.ui.components.Tone
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.components.EaseOutExpo
import app.holdthatpose.ui.components.pressable
import app.holdthatpose.ui.home.AdSlot
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.permissions.RadioNotices
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType
import kotlinx.coroutines.delay

@Composable
fun RemotePairingScreen(app: PoseApp, onConnected: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val link = app.link
    val discovered by link.discovered.collectAsState()
    val connection by link.connection.collectAsState()
    val error by link.error.collectAsState()
    var slow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SessionService.start(context, Role.Remote)
        app.remoteSession.startPairing()
        delay(8_000)
        slow = true
    }
    LaunchedEffect(connection) {
        if (connection is Connection.Connected) onConnected()
    }
    LaunchedEffect(error) {
        if (error != null) {
            delay(4_000)
            link.clearError()
        }
    }

    // The Camera we paired with last sorts first as a one-tap "Reconnect"; nothing connects by itself.
    val lastPeerId = app.prefs.lastPeerId
    val cameras = discovered.filter { it.role == Role.Camera }.sortedByDescending { it.installId == lastPeerId }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.75f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            VSpace(12.dp)
            GlassIconButton(PoseIcons.Back, "Back", onBack)
            VSpace(24.dp)
            Text(
                if (cameras.isEmpty()) "Finding\nCamera…" else "Tap your\nCamera",
                style = PoseType.Title,
                color = PoseColors.Paper,
            )

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Radar(Modifier.size(260.dp))
            }

            AnimatedVisibility(slow && cameras.isEmpty(), enter = fadeIn(), exit = fadeOut()) {
                NoticePill(
                    "Open the app → Camera on the other phone",
                    Tone.Warn,
                    Modifier.padding(bottom = 12.dp),
                )
            }
            error?.let { NoticePill(it, Tone.Bad, Modifier.padding(bottom = 12.dp)) }
            RadioNotices(Modifier.padding(bottom = 12.dp))
            AnimatedVisibility(error != null || (slow && cameras.isEmpty()), enter = fadeIn(), exit = fadeOut()) {
                SecondaryButton(
                    "Try again",
                    {
                        link.clearError()
                        link.stopDiscovery()
                        link.startDiscovery()
                    },
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    icon = PoseIcons.Retake,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cameras, key = { it.installId }) { peer ->
                    CameraRow(peer, known = peer.installId == lastPeerId) {
                        app.remoteSession.connect(peer)
                    }
                }
            }
            VSpace(14.dp)
            AdSlot()
            VSpace(8.dp)
        }

        CodeSheet(
            connection = connection,
            onConfirm = { link.acceptPending() },
            onCancel = { link.rejectPending() },
        )
    }
}

@Composable
private fun CameraRow(peer: Peer, known: Boolean, onClick: () -> Unit) {
    Glass(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.97f, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        tint = Color(0xFF0F151F).copy(alpha = 0.8f),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(PoseColors.Sky.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PoseIcons.Camera, null, Modifier.size(22.dp), tint = PoseColors.Sky)
            }
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = PoseType.BodyStrong, color = PoseColors.Paper)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(PoseColors.Mint, dotSize = 5.dp)
                    Text(if (known) "Reconnect" else "Camera", style = PoseType.Caption, color = PoseColors.PaperDim)
                }
            }
            Icon(PoseIcons.Arrow, null, Modifier.size(20.dp), tint = PoseColors.PaperFaint)
        }
    }
}

/** Sonar rings expanding from the Remote. */
@Composable
private fun Radar(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            for (i in 0 until 4) {
                val q = (p + i / 4f) % 1f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.Transparent, PoseColors.Sky.copy(alpha = 0.10f * (1 - q))),
                        center = center,
                        radius = maxR * q + 1f,
                    ),
                    radius = maxR * q,
                )
                drawCircle(PoseColors.Sky.copy(alpha = 0.45f * (1 - q)), radius = maxR * q, style = Stroke(1.2f * density))
            }
            drawCircle(Color.White.copy(alpha = 0.06f), radius = maxR * 0.33f, style = Stroke(1f * density))
            drawCircle(Color.White.copy(alpha = 0.04f), radius = maxR * 0.66f, style = Stroke(1f * density))
            drawCircle(PoseColors.AccentBrush, radius = maxR * 0.2f, center = Offset(center.x, center.y))
        }
        Icon(PoseIcons.Remote, null, Modifier.size(30.dp), tint = PoseColors.Ink)
    }
}

/** The 4-digit handshake code: confirm it matches the Camera, or watch a known Camera reconnect. */
@Composable
private fun CodeSheet(connection: Connection, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val pending = connection as? Connection.Pending
    // Keep showing the last code while the sheet animates away.
    var shown by remember { mutableStateOf<Connection.Pending?>(null) }
    if (pending != null) shown = pending
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(pending != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        AnimatedVisibility(
            pending != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(480, easing = EaseOutExpo)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(),
        ) {
            val p = shown ?: return@AnimatedVisibility
            Glass(
                Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding(),
                shape = RoundedCornerShape(32.dp),
                tint = PoseColors.Ink2.copy(alpha = 0.97f),
            ) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline(p.peer.name, color = PoseColors.Sky)
                    VSpace(10.dp)
                    if (p.code.isEmpty() || p.accepted) {
                        Text("Connecting…", style = PoseType.Title, color = PoseColors.Paper)
                        VSpace(8.dp)
                        Text(
                            if (p.code.isEmpty()) "Reaching the Camera" else "Tap Allow on the Camera",
                            style = PoseType.Body,
                            color = PoseColors.PaperDim,
                            textAlign = TextAlign.Center,
                        )
                        VSpace(22.dp)
                        StatusDot(PoseColors.Sky, pulse = true, dotSize = 10.dp)
                    } else {
                        Text("Same code?", style = PoseType.TitleSmall, color = PoseColors.Paper, textAlign = TextAlign.Center)
                        VSpace(20.dp)
                        CodeDigits(p.code)
                        VSpace(26.dp)
                        PrimaryButton("Connect", onConfirm, icon = PoseIcons.Check)
                        VSpace(10.dp)
                        SecondaryButton("Cancel", onCancel, Modifier.fillMaxWidth())
                    }
                    VSpace(4.dp)
                }
            }
        }
    }
}
