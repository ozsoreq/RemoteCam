package app.afar.ui.pairing

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
import app.afar.AfarApp
import app.afar.net.Connection
import app.afar.net.Peer
import app.afar.net.Role
import app.afar.session.SessionService
import app.afar.ui.components.AuroraBackground
import app.afar.ui.components.Glass
import app.afar.ui.components.GlassIconButton
import app.afar.ui.components.HSpace
import app.afar.ui.components.NoticePill
import app.afar.ui.components.Overline
import app.afar.ui.components.PrimaryButton
import app.afar.ui.components.SecondaryButton
import app.afar.ui.components.StatusDot
import app.afar.ui.components.Tone
import app.afar.ui.components.VSpace
import app.afar.ui.components.EaseOutExpo
import app.afar.ui.components.pressable
import app.afar.ui.home.AdSlot
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType
import kotlinx.coroutines.delay

@Composable
fun RemotePairingScreen(app: AfarApp, onConnected: () -> Unit, onBack: () -> Unit) {
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
    // One-tap reconnect to the phone we paired with last time.
    LaunchedEffect(discovered, connection) {
        if (connection !is Connection.None) return@LaunchedEffect
        discovered.firstOrNull { it.role == Role.Camera && it.installId == app.prefs.lastPeerId }?.let {
            app.remoteSession.connect(it)
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            delay(4_000)
            link.clearError()
        }
    }

    val cameras = discovered.filter { it.role == Role.Camera }

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
            GlassIconButton(AfarIcons.Back, "Back", onBack)
            VSpace(24.dp)
            Overline("Remote", color = AfarColors.Apricot)
            VSpace(8.dp)
            Text(
                if (cameras.isEmpty()) "Looking for\nyour Camera…" else "Tap your\nCamera",
                style = AfarType.Title,
                color = AfarColors.Paper,
            )
            VSpace(10.dp)
            Text(
                "On the other phone, open Afar and choose Camera. Works with Bluetooth and Wi-Fi on — no internet needed.",
                style = AfarType.Body,
                color = AfarColors.PaperDim,
            )

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Radar(Modifier.size(260.dp))
            }

            AnimatedVisibility(slow && cameras.isEmpty(), enter = fadeIn(), exit = fadeOut()) {
                NoticePill(
                    "Nothing yet — keep the phones within a few metres",
                    Tone.Warn,
                    Modifier.padding(bottom = 12.dp),
                )
            }
            error?.let { NoticePill(it, Tone.Bad, Modifier.padding(bottom = 12.dp)) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cameras, key = { it.installId }) { peer ->
                    CameraRow(peer, known = peer.installId == app.prefs.lastPeerId) {
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
        tint = Color(0xFF121418).copy(alpha = 0.8f),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(AfarColors.Apricot.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AfarIcons.Camera, null, Modifier.size(22.dp), tint = AfarColors.Apricot)
            }
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = AfarType.BodyStrong, color = AfarColors.Paper)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(AfarColors.Mint, dotSize = 5.dp)
                    Text(if (known) "Paired before · tap to reconnect" else "Camera · tap to connect", style = AfarType.Caption, color = AfarColors.PaperDim)
                }
            }
            Icon(AfarIcons.Arrow, null, Modifier.size(20.dp), tint = AfarColors.PaperFaint)
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
                        listOf(Color.Transparent, AfarColors.Apricot.copy(alpha = 0.10f * (1 - q))),
                        center = center,
                        radius = maxR * q + 1f,
                    ),
                    radius = maxR * q,
                )
                drawCircle(AfarColors.Apricot.copy(alpha = 0.45f * (1 - q)), radius = maxR * q, style = Stroke(1.2f * density))
            }
            drawCircle(Color.White.copy(alpha = 0.06f), radius = maxR * 0.33f, style = Stroke(1f * density))
            drawCircle(Color.White.copy(alpha = 0.04f), radius = maxR * 0.66f, style = Stroke(1f * density))
            drawCircle(AfarColors.AccentBrush, radius = maxR * 0.2f, center = Offset(center.x, center.y))
        }
        Icon(AfarIcons.Remote, null, Modifier.size(30.dp), tint = AfarColors.Ink)
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
                tint = AfarColors.Ink2.copy(alpha = 0.97f),
            ) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline(p.peer.name, color = AfarColors.Apricot)
                    VSpace(10.dp)
                    if (p.code.isEmpty() || p.accepted) {
                        Text("Connecting…", style = AfarType.Title, color = AfarColors.Paper)
                        VSpace(8.dp)
                        Text(
                            if (p.code.isEmpty()) "Reaching the Camera" else "Code ${p.code} · switching to a fast Wi-Fi link",
                            style = AfarType.Body,
                            color = AfarColors.PaperDim,
                            textAlign = TextAlign.Center,
                        )
                        VSpace(22.dp)
                        StatusDot(AfarColors.Apricot, pulse = true, dotSize = 10.dp)
                    } else {
                        Text("Same code on the Camera?", style = AfarType.TitleSmall, color = AfarColors.Paper, textAlign = TextAlign.Center)
                        VSpace(20.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            p.code.forEach { ch ->
                                Glass(Modifier.size(width = 58.dp, height = 74.dp), shape = RoundedCornerShape(18.dp), tint = AfarColors.GlassLight) {
                                    Text("$ch", style = AfarType.Code.copy(fontSize = AfarType.Code.fontSize * 0.8f), color = AfarColors.Paper, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                        VSpace(26.dp)
                        PrimaryButton("Yes, connect", onConfirm, icon = AfarIcons.Check)
                        VSpace(10.dp)
                        SecondaryButton("Not my phone", onCancel, Modifier.fillMaxWidth())
                    }
                    VSpace(4.dp)
                }
            }
        }
    }
}
