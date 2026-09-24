package app.afar.ui.remote

import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.afar.AfarApp
import app.afar.MainActivity
import app.afar.net.LinkQuality
import app.afar.session.LinkPhase
import app.afar.session.WARNING_SECONDS
import app.afar.ui.components.BatteryIndicator
import app.afar.ui.components.CountdownNumeral
import app.afar.ui.components.FocusReticle
import app.afar.ui.components.Glass
import app.afar.ui.components.GlassIconButton
import app.afar.ui.components.GridOverlay
import app.afar.ui.components.HSpace
import app.afar.ui.components.LensPicker
import app.afar.ui.components.LevelOverlay
import app.afar.ui.components.NoticePill
import app.afar.ui.components.PrimaryButton
import app.afar.ui.components.SecondaryButton
import app.afar.ui.components.ShutterButton
import app.afar.ui.components.SignalBars
import app.afar.ui.components.StatusDot
import app.afar.ui.components.TimerChip
import app.afar.ui.components.Tone
import app.afar.ui.components.VSpace
import app.afar.ui.components.pressable
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val timerSteps = listOf(0, 3, 5, 10)

/** The phone in your hand: live view, framing aids and the shutter. */
@Composable
fun RemoteScreen(app: AfarApp, activity: MainActivity, onExit: () -> Unit) {
    val session = app.remoteSession
    val prefs = app.prefs

    val frame by session.frame.collectAsState()
    val status by session.status.collectAsState()
    val phase by session.phase.collectAsState()
    val countdown by session.countdown.collectAsState()
    val shots by session.shots.collectAsState()
    val awaiting by session.awaitingPhoto.collectAsState()
    val queued by session.queuedShutter.collectAsState()
    val rtt by session.rtt.collectAsState()
    val fps by session.fps.collectAsState()
    val quality by app.link.quality.collectAsState()

    var grid by remember { mutableStateOf(prefs.grid) }
    var level by remember { mutableStateOf(prefs.level) }
    var mirror by remember { mutableStateOf(prefs.mirror) }
    var burst by remember { mutableStateOf(prefs.burst) }
    var timer by remember { mutableIntStateOf(prefs.timer) }
    var reviewing by remember { mutableStateOf<Int?>(null) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }
    val notices = remember { mutableStateListOf<String>() }

    val shoot = { session.shutter(timer, burst) }

    LaunchedEffect(Unit) { session.start() }
    LaunchedEffect(Unit) {
        session.notices.collect { msg ->
            notices.add(msg)
            delay(3_500)
            notices.remove(msg)
        }
    }
    DisposableEffect(Unit) {
        activity.volumeShutter = { session.shutter(timer, burst) }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.volumeShutter = null
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    // Volume shutter must see the latest timer/burst.
    LaunchedEffect(timer, burst) { activity.volumeShutter = { session.shutter(timer, burst) } }

    val counting = (countdown ?: 0) > 0
    val bars = session.signalBars(quality, rtt)

    Box(
        Modifier
            .fillMaxSize()
            .background(AfarColors.Ink)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        session.userActive()
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ── Top bar ──────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(AfarIcons.Close, "Disconnect", onExit, size = 40.dp)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    LinkPill(session.peerName ?: "Camera", phase, bars, rtt, quality)
                }
                Glass(Modifier.height(40.dp), shape = CircleShape) {
                    Box(Modifier.align(Alignment.Center).padding(horizontal = 12.dp)) {
                        BatteryIndicator(status?.battery ?: -1, status?.charging ?: false)
                    }
                }
            }

            // ── Viewfinder ───────────────────────────────────────
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ratio = frame?.bitmap?.let { it.width.toFloat() / it.height } ?: (3f / 4f)
                Box(
                    Modifier
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(26.dp))
                        .background(AfarColors.Ink2)
                        .border(1.dp, AfarColors.Hairline, RoundedCornerShape(26.dp))
                        .pointerInput(mirror) {
                            detectTapGestures { pos ->
                                val nx = (pos.x / size.width).let { if (mirror) 1f - it else it }
                                session.focus(nx, pos.y / size.height)
                                focusAt = pos
                                focusKey++
                            }
                        },
                ) {
                    val bmp = frame?.bitmap
                    if (bmp != null) {
                        val image = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            image,
                            contentDescription = "Live view from the Camera",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = if (mirror) -1f else 1f
                                    alpha = if (phase is LinkPhase.Live) 1f else 0.35f
                                },
                        )
                    } else {
                        WaitingForFrames(Modifier.align(Alignment.Center), paused = status?.previewPaused == true || status?.lowPower == true)
                    }

                    if (grid) GridOverlay(Modifier.fillMaxSize())
                    val f = frame
                    if (level && f != null) {
                        LevelOverlay(if (mirror) -f.roll else f.roll, f.bumped, Modifier.fillMaxSize())
                    }

                    focusAt?.let { p ->
                        val half = with(LocalDensity.current) { 38.dp.toPx() }
                        FocusReticle(focusKey, Modifier.offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) })
                    }

                    // Countdown veil
                    val veil by animateFloatAsState(if (counting) 1f else 0f, tween(260), label = "veil")
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil }.background(Color.Black.copy(alpha = 0.35f)))
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CountdownNumeral(countdown)
                        AnimatedVisibility(counting, enter = fadeIn(), exit = fadeOut()) {
                            Text("Hide the Remote", style = AfarType.Label, color = AfarColors.Paper)
                        }
                    }

                    // Notices on top of the frame
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Notices(
                            status = status,
                            lowQuality = frame?.lowQuality == true,
                            bumped = frame?.bumped == true,
                            queued = queued != null,
                            awaiting = awaiting,
                            transient = notices,
                        )
                    }

                    if (fps > 0 && phase is LinkPhase.Live) {
                        Text(
                            "$fps fps",
                            style = AfarType.Mono,
                            color = AfarColors.PaperFaint,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        )
                    }

                    ReconnectVeil(phase, onRetry = session::retryConnection, onExit = onExit)
                    IdleWarning(
                        secondsLeft = status?.idleLeft ?: -1,
                        visible = phase is LinkPhase.Live,
                        onKeepGoing = session::keepAlive,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            // ── Toggles + lens ───────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(AfarIcons.Grid, "Grid", { grid = !grid; prefs.grid = grid }, active = grid, size = 40.dp)
                GlassIconButton(AfarIcons.Level, "Level", { level = !level; prefs.level = level }, active = level, size = 40.dp)
                GlassIconButton(AfarIcons.Mirror, "Mirror", { mirror = !mirror; prefs.mirror = mirror }, active = mirror, size = 40.dp)
                GlassIconButton(AfarIcons.Burst, "Best-of-3 burst", { burst = !burst; prefs.burst = burst }, active = burst, size = 40.dp)
                Box(Modifier.weight(1f))
                val st = status
                if (st != null) {
                    LensPicker(st.lenses, st.lens, onSelect = session::setLens)
                }
            }

            // ── Shutter deck ─────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 6.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Thumbnail(shots.firstOrNull()?.image, awaiting) { if (shots.isNotEmpty()) reviewing = 0 }
                }
                ShutterButton(
                    counting = counting,
                    progress = countdown?.let { 1f - it.toFloat() / timer.coerceAtLeast(1) } ?: 0f,
                    enabled = phase is LinkPhase.Live || phase is LinkPhase.Reconnecting,
                    queued = queued != null,
                    onClick = shoot,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TimerChip(timer, onClick = {
                        timer = timerSteps[(timerSteps.indexOf(timer) + 1) % timerSteps.size]
                        prefs.timer = timer
                    })
                }
            }
        }

        // Review
        AnimatedVisibility(
            reviewing != null && shots.isNotEmpty(),
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)),
            exit = fadeOut(tween(200)),
        ) {
            ReviewScreen(
                shots = shots,
                start = reviewing ?: 0,
                onClose = { reviewing = null },
                onDelete = { shot ->
                    session.delete(shot)
                    if (shots.size <= 1) reviewing = null
                },
                onRetake = {
                    reviewing = null
                    shoot()
                },
            )
        }
    }
}

@Composable
private fun LinkPill(name: String, phase: LinkPhase, bars: Int, rtt: Long?, quality: LinkQuality) {
    Glass(Modifier.height(40.dp), shape = CircleShape) {
        Row(
            Modifier.align(Alignment.Center).padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (color, pulse) = when (phase) {
                LinkPhase.Live -> AfarColors.Mint to false
                is LinkPhase.Reconnecting -> AfarColors.Amber to true
                LinkPhase.Lost -> AfarColors.Danger to false
                is LinkPhase.Ended -> AfarColors.PaperFaint to false
                LinkPhase.Idle -> AfarColors.PaperFaint to true
            }
            StatusDot(color, pulse = pulse, dotSize = 7.dp)
            AnimatedContent(phase, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "link") { p ->
                Text(
                    when (p) {
                        LinkPhase.Live -> name
                        is LinkPhase.Reconnecting -> "Reconnecting… ${p.secondsLeft}s"
                        LinkPhase.Lost -> "Connection lost"
                        is LinkPhase.Ended -> "Session ended"
                        LinkPhase.Idle -> "Connecting…"
                    },
                    style = AfarType.Label,
                    color = AfarColors.Paper,
                    maxLines = 1,
                )
            }
            if (phase == LinkPhase.Live) {
                HSpace(10.dp)
                SignalBars(bars, color = if (bars <= 1) AfarColors.Amber else AfarColors.Paper)
                if (quality == LinkQuality.Low) {
                    HSpace(6.dp)
                    Text("BT", style = AfarType.Overline, color = AfarColors.Amber)
                } else if (rtt != null) {
                    HSpace(6.dp)
                    Text("${rtt}ms", style = AfarType.Mono, color = AfarColors.PaperFaint)
                }
            }
        }
    }
}

@Composable
private fun Notices(
    status: app.afar.net.CameraStatus?,
    lowQuality: Boolean,
    bumped: Boolean,
    queued: Boolean,
    awaiting: Boolean,
    transient: List<String>,
) {
    val items = buildList {
        if (status != null) {
            if (status.lowPower) add("Camera battery critical · preview off" to Tone.Bad)
            else if (status.battery in 0 until 10 && !status.charging) add("Camera battery ${status.battery}%" to Tone.Bad)
            if (!status.storageOk) add("Camera storage full" to Tone.Bad)
            if (status.hot) add("Camera is hot · preview slowed" to Tone.Warn)
            if (status.previewPaused) add("Camera paused" to Tone.Warn)
        }
        if (bumped) add("Camera moved" to Tone.Bad)
        if (lowQuality) add("Low quality preview" to Tone.Warn)
        if (queued) add("Shutter queued" to Tone.Accent)
        if (awaiting) add("Saving photo…" to Tone.Accent)
        transient.forEach { add(it to Tone.Neutral) }
    }
    items.forEach { (text, tone) -> NoticePill(text, tone, pulse = tone == Tone.Accent) }
}

@Composable
private fun WaitingForFrames(modifier: Modifier, paused: Boolean) {
    val t = rememberInfiniteTransition(label = "wait")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StatusDot(AfarColors.Sky, pulse = true, dotSize = 9.dp)
        VSpace(10.dp)
        Text(
            if (paused) "Preview paused" else "Waiting for the picture…",
            style = AfarType.Label,
            color = AfarColors.Paper.copy(alpha = a),
        )
    }
}

@Composable
private fun ReconnectVeil(phase: LinkPhase, onRetry: () -> Unit, onExit: () -> Unit) {
    AnimatedVisibility(phase !is LinkPhase.Live && phase != LinkPhase.Idle, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (phase is LinkPhase.Ended) {
                    Text("Session ended", style = AfarType.Title, color = AfarColors.Paper, textAlign = TextAlign.Center)
                    VSpace(8.dp)
                    Text(phase.reason, style = AfarType.Body, color = AfarColors.PaperDim, textAlign = TextAlign.Center)
                    VSpace(4.dp)
                    Text("Restart it on the Camera phone.", style = AfarType.Caption, color = AfarColors.PaperFaint, textAlign = TextAlign.Center)
                    VSpace(22.dp)
                    SecondaryButton("Back to pairing", onExit, Modifier.fillMaxWidth())
                } else if (phase == LinkPhase.Lost) {
                    Text("Lost the Camera", style = AfarType.Title, color = AfarColors.Paper, textAlign = TextAlign.Center)
                    VSpace(8.dp)
                    Text("Move closer. Photos are safe.", style = AfarType.Body, color = AfarColors.PaperDim, textAlign = TextAlign.Center)
                    VSpace(22.dp)
                    PrimaryButton("Try again", onRetry, icon = AfarIcons.Retake)
                    VSpace(10.dp)
                    SecondaryButton("Back to pairing", onExit, Modifier.fillMaxWidth())
                } else {
                    StatusDot(AfarColors.Amber, pulse = true, dotSize = 12.dp)
                    VSpace(14.dp)
                    Text("Reconnecting…", style = AfarType.TitleSmall, color = AfarColors.Paper)
                    VSpace(6.dp)
                    Text("Tap the shutter to queue a shot", style = AfarType.Caption, color = AfarColors.PaperDim, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(image: android.graphics.Bitmap?, busy: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "thumb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    Box(Modifier.size(56.dp).pressable(pressedScale = 0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AfarColors.Ink3)
                .border(1.dp, AfarColors.HairlineStrong, RoundedCornerShape(16.dp)),
        ) {
            if (image != null) {
                val bmp = remember(image) { image.asImageBitmap() }
                Image(bmp, "Last photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (busy) {
            Canvas(Modifier.size(62.dp).graphicsLayer { rotationZ = spin }) {
                val sw = 2.dp.toPx()
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, AfarColors.Sky)),
                    0f, 300f, false,
                    topLeft = Offset(sw, sw),
                    size = Size(size.width - 2 * sw, size.height - 2 * sw),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** "Still there?" — the Camera drops idle sessions; this gives a 10-second heads-up. */
@Composable
private fun IdleWarning(secondsLeft: Int, visible: Boolean, onKeepGoing: () -> Unit, modifier: Modifier = Modifier) {
    val show = visible && secondsLeft in 0..WARNING_SECONDS
    AnimatedVisibility(
        show,
        modifier = modifier.padding(12.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
    ) {
        Glass(shape = RoundedCornerShape(24.dp), tint = AfarColors.Ink2.copy(alpha = 0.92f)) {
            Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(AfarColors.Amber, pulse = true, dotSize = 7.dp)
                Text("Disconnecting in ${secondsLeft.coerceAtLeast(0)}s", style = AfarType.Label, color = AfarColors.Paper)
                HSpace(12.dp)
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(AfarColors.AccentBrush)
                        .pressable(onClick = onKeepGoing)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Keep going", style = AfarType.Label, color = AfarColors.Ink)
                }
            }
        }
    }
}
