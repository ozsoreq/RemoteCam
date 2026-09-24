package app.afar.ui.camera

import android.app.Activity
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.afar.AfarApp
import app.afar.MainActivity
import app.afar.net.Connection
import app.afar.net.Lens
import app.afar.net.Role
import app.afar.session.SessionService
import app.afar.ui.components.CountdownNumeral
import app.afar.ui.components.EaseOutExpo
import app.afar.ui.components.FocusReticle
import app.afar.ui.components.Glass
import app.afar.ui.components.GlassIconButton
import app.afar.ui.components.HSpace
import app.afar.ui.components.NoticePill
import app.afar.ui.components.Overline
import app.afar.ui.components.StatusDot
import app.afar.ui.components.Tone
import app.afar.ui.components.VSpace
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The phone on the rock. */
@Composable
fun CameraScreen(app: AfarApp, activity: MainActivity, onExit: () -> Unit) {
    val context = LocalContext.current
    val session = app.cameraSession
    val lifecycleOwner = LocalLifecycleOwner.current

    val connection by app.link.connection.collectAsState()
    val advertising by app.link.advertising.collectAsState()
    val linkError by app.link.error.collectAsState()
    val countdown by session.countdown.collectAsState()
    val flash by session.captureFlash.collectAsState()
    val lastSaved by session.lastSaved.collectAsState()
    val bound by session.controller.bound.collectAsState()

    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dimmed by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }
    var quadrant by remember { mutableIntStateOf(0) }
    val connected = connection is Connection.Connected

    LaunchedEffect(Unit) {
        SessionService.start(context, Role.Camera)
        session.start()
    }
    LaunchedEffect(Unit) {
        while (true) {
            quadrant = session.level.quadrant
            delay(300)
        }
    }

    // Keep the screen awake; pause capture while we're not visible (e.g. an incoming call).
    DisposableEffect(lifecycleOwner) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> session.controller.paused = true
                Lifecycle.Event.ON_START -> session.controller.paused = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    // Auto-dim after 10 s untouched (saves battery on the rock); capture keeps working.
    LaunchedEffect(lastTouch, connected, countdown) {
        dimmed = false
        if (!connected || countdown != null) return@LaunchedEffect
        delay(10_000)
        dimmed = true
    }
    LaunchedEffect(dimmed, countdown) {
        setBrightness(
            activity,
            when {
                countdown != null -> 1f
                dimmed -> 0.01f
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    session.controller.attach(lifecycleOwner, this, Lens.Ultrawide)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        session.controller.focusOnViewfinder(pos.x, pos.y)
                        focusAt = pos
                        focusKey++
                    }
                },
        )

        focusAt?.let { p ->
            val half = with(LocalDensity.current) { 38.dp.toPx() }
            FocusReticle(focusKey, Modifier.offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) })
        }

        // Soft top & bottom scrims so glass controls read over bright scenes.
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.18f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                ),
            ),
        )

        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(AfarIcons.Close, "Leave", onExit)
            HSpace(10.dp)
            AnimatedVisibility(connected, enter = fadeIn(), exit = fadeOut()) {
                val name = (connection as? Connection.Connected)?.peer?.name.orEmpty()
                NoticePill("Connected to $name", Tone.Good)
            }
            Box(Modifier.weight(1f))
            GlassIconButton(AfarIcons.Lock, "Lock screen", { locked = true })
        }

        // Pause / error notices
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!bound) NoticePill("Starting camera…", Tone.Warn, pulse = true)
            linkError?.let { NoticePill(it, Tone.Bad) }
            SavedToast(lastSaved)
        }

        // Waiting-for-Remote card / pairing code
        AnimatedVisibility(
            !connected,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(520, easing = EaseOutExpo)) { it / 2 } + fadeIn(),
            exit = slideOutVertically(tween(300)) { it / 2 } + fadeOut(),
        ) {
            WaitingCard(
                deviceName = app.link.deviceName,
                advertising = advertising,
                code = (connection as? Connection.Pending)?.code?.takeIf { it.isNotEmpty() },
            )
        }

        // Countdown — a pulsing warm field with a huge numeral, readable from 20 m.
        CountdownField(countdown, quadrant)

        // Shutter blink
        val blink = remember { Animatable(0f) }
        LaunchedEffect(flash) {
            if (flash == 0) return@LaunchedEffect
            blink.snapTo(0.95f)
            blink.animateTo(0f, tween(420))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value }.background(Color.White))

        // Dimmed standby
        AnimatedVisibility(dimmed && !locked, enter = fadeIn(tween(900)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .pointerInput(Unit) { detectTapGestures { lastTouch = System.currentTimeMillis() } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusDot(AfarColors.Mint, pulse = true, dotSize = 7.dp)
                    VSpace(10.dp)
                    Text("Live · saving battery", style = AfarType.Label, color = AfarColors.PaperDim)
                    VSpace(4.dp)
                    Text("Tap to wake", style = AfarType.Caption, color = AfarColors.PaperFaint)
                }
            }
        }

        // Lock overlay: swallows every touch; hold to unlock.
        AnimatedVisibility(locked, enter = fadeIn(), exit = fadeOut()) {
            LockOverlay(dimmed = dimmed, onUnlock = { locked = false; lastTouch = System.currentTimeMillis() })
        }
    }
}

private fun setBrightness(activity: Activity, value: Float) {
    val lp = activity.window.attributes
    if (lp.screenBrightness == value) return
    lp.screenBrightness = value
    activity.window.attributes = lp
}

@Composable
private fun SavedToast(lastSaved: Long?) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(lastSaved) {
        if (lastSaved == null) return@LaunchedEffect
        visible = true
        delay(2_200)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
        NoticePill("Saved to gallery", Tone.Good)
    }
}

@Composable
private fun WaitingCard(deviceName: String, advertising: Boolean, code: String?) {
    Glass(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(32.dp),
        tint = AfarColors.Ink2.copy(alpha = 0.82f),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (advertising || code != null) AfarColors.Mint else AfarColors.Amber, pulse = true, dotSize = 7.dp)
                Overline(if (code != null) "Remote found" else if (advertising) "Visible nearby" else "Starting…", color = AfarColors.PaperDim)
            }
            VSpace(10.dp)
            AnimatedContent(code, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "code") { c ->
                if (c == null) {
                    Column {
                        Text("Waiting for\nyour Remote", style = AfarType.Title, color = AfarColors.Paper)
                        VSpace(10.dp)
                        Text(
                            "On the other phone, open Afar and choose Remote. This phone appears as “$deviceName”.",
                            style = AfarType.Body,
                            color = AfarColors.PaperDim,
                        )
                    }
                } else {
                    Column {
                        Text("Check the Remote\nshows this code", style = AfarType.TitleSmall, color = AfarColors.Paper)
                        VSpace(18.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            c.forEach { ch ->
                                Glass(Modifier.size(width = 62.dp, height = 80.dp), shape = RoundedCornerShape(18.dp), tint = AfarColors.GlassLight) {
                                    Text("$ch", style = AfarType.Code.copy(fontSize = AfarType.Code.fontSize * 0.85f), color = AfarColors.Paper, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownField(countdown: Int?, quadrant: Int) {
    val active = countdown != null && countdown > 0
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(countdown) {
        if (countdown != null && countdown > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(900, easing = EaseOutExpo))
        }
    }
    val base by animateFloatAsState(if (active) 1f else 0f, tween(250), label = "base")
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = base }
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(AfarColors.Coral.copy(alpha = 0.55f + 0.35f * pulse.value), AfarColors.Ink.copy(alpha = 0.85f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CountdownNumeral(
            countdown,
            Modifier.graphicsLayer {
                rotationZ = quadrant.toFloat()
                val s = 1f + 0.08f * pulse.value
                scaleX = s * 1.5f
                scaleY = s * 1.5f
            },
        )
    }
}

@Composable
private fun LockOverlay(dimmed: Boolean, onUnlock: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dimmed) 0.94f else 0.25f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(1100, easing = LinearEasing))
                        onUnlock()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 2.dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.2f), radius = size.minDimension / 2 - sw, style = Stroke(sw))
                    drawArc(
                        AfarColors.AccentBrush, -90f, 360f * progress.value, false,
                        topLeft = Offset(sw, sw), size = Size(size.width - 2 * sw, size.height - 2 * sw),
                        style = Stroke(sw * 1.5f, cap = StrokeCap.Round),
                    )
                }
                Icon(AfarIcons.Lock, "Locked", Modifier.size(26.dp), tint = AfarColors.Paper)
            }
            VSpace(12.dp)
            Text("Locked · hold to unlock", style = AfarType.Label, color = AfarColors.PaperDim)
        }
    }
}
