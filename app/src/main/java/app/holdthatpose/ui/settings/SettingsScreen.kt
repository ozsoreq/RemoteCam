package app.holdthatpose.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.holdthatpose.BuildConfig
import app.holdthatpose.R
import app.holdthatpose.data.Prefs
import app.holdthatpose.session.formatDuration
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.components.Glass
import app.holdthatpose.ui.components.GlassIconButton
import app.holdthatpose.ui.components.HSpace
import app.holdthatpose.ui.components.EaseOutExpo
import app.holdthatpose.ui.components.Overline
import app.holdthatpose.ui.components.PrimaryButton
import app.holdthatpose.ui.components.SecondaryButton
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.components.pressable
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onResponsibleUse: () -> Unit, onPrivacy: () -> Unit) {
    val context = LocalContext.current
    var safe by remember { mutableStateOf(prefs.safeMode) }
    var idle by remember { mutableIntStateOf(prefs.idleTimeoutSec) }
    var confirmOff by remember { mutableStateOf(false) }
    var licences by remember { mutableStateOf(false) }
    BackHandler(enabled = confirmOff || licences) {
        confirmOff = false
        licences = false
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.55f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            VSpace(12.dp)
            GlassIconButton(PoseIcons.Back, "Back", onBack)
            VSpace(28.dp)
            Text("Settings", style = PoseType.Title, color = PoseColors.Paper)
            VSpace(24.dp)

            // ── Safe mode ───────────────────────────────
            Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(PoseIcons.Shield, null, Modifier.size(24.dp), tint = if (safe) PoseColors.Sky else PoseColors.PaperDim)
                        HSpace(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Safe mode", style = PoseType.BodyStrong, color = PoseColors.Paper)
                            Text("Recommended", style = PoseType.Caption, color = PoseColors.PaperDim)
                        }
                        Toggle(safe, "Safe mode switch") { on ->
                            if (on) {
                                // Turning it back on never needs confirming.
                                safe = true
                                prefs.safeMode = true
                                if (idle <= 0) {
                                    idle = Prefs.DEFAULT_IDLE
                                    prefs.idleTimeoutSec = idle
                                }
                            } else {
                                confirmOff = true
                            }
                        }
                    }
                    VSpace(14.dp)
                    // Always on, whatever the switch says.
                    SafeLine("Allow each new Remote on the Camera", true)
                    SafeLine("LIVE sign and sounds on the Camera", true)
                    // What safe mode adds.
                    SafeLine("Check-in after 30 min", safe)
                    SafeLine("LIVE sign stays readable when dimmed", safe)
                }
            }

            VSpace(14.dp)

            // ── Auto-disconnect ─────────────────────────
            Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(PoseIcons.Timer, null, Modifier.size(24.dp), tint = PoseColors.PaperDim)
                        HSpace(14.dp)
                        Column {
                            Text("Auto-disconnect", style = PoseType.BodyStrong, color = PoseColors.Paper)
                            Text("After no activity from the Remote", style = PoseType.Caption, color = PoseColors.PaperDim)
                        }
                    }
                    VSpace(16.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Prefs.IDLE_CHOICES.forEach { value ->
                            Chip(formatDuration(value), selected = value == idle, enabled = true) {
                                idle = value
                                prefs.idleTimeoutSec = value
                            }
                        }
                    }
                }
            }

            VSpace(14.dp)

            Glass(Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onResponsibleUse), shape = RoundedCornerShape(26.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PoseIcons.Info, null, Modifier.size(24.dp), tint = PoseColors.PaperDim)
                    HSpace(14.dp)
                    Text("Responsible use", style = PoseType.BodyStrong, color = PoseColors.Paper, modifier = Modifier.weight(1f))
                    Icon(PoseIcons.Arrow, null, Modifier.size(20.dp), tint = PoseColors.PaperFaint)
                }
            }
            VSpace(14.dp)

            // ── About ───────────────────────────────────
            Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AboutRow("Privacy policy", onClick = onPrivacy)
                    AboutRow("Licences") { licences = true }
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = PoseType.Caption,
                        color = PoseColors.PaperFaint,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            VSpace(24.dp)
            Text(
                "Applies from the next session.",
                style = PoseType.Caption,
                color = PoseColors.PaperFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            VSpace(20.dp)
        }

        Sheet(confirmOff, onDismiss = { confirmOff = false }) {
            Text("Turn off safe mode?", style = PoseType.TitleSmall, color = PoseColors.Paper)
            VSpace(8.dp)
            Text("No session limit. Allow, LIVE and chimes stay on.", style = PoseType.Body, color = PoseColors.PaperDim)
            VSpace(22.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "Turn off",
                    {
                        confirmOff = false
                        safe = false
                        prefs.safeMode = false
                    },
                    Modifier.weight(1f),
                )
                PrimaryButton("Cancel", { confirmOff = false }, Modifier.weight(1f))
            }
        }

        Sheet(licences, onDismiss = { licences = false }) {
            Text("Licences", style = PoseType.TitleSmall, color = PoseColors.Paper)
            VSpace(12.dp)
            LICENCES.forEach { (what, licence) ->
                Text(what, style = PoseType.BodyStrong, color = PoseColors.Paper)
                Text(licence, style = PoseType.Caption, color = PoseColors.PaperDim)
                VSpace(10.dp)
            }
            VSpace(8.dp)
            SecondaryButton("Close", { licences = false }, Modifier.fillMaxWidth())
        }
    }
}

private val LICENCES = listOf(
    "Instrument Serif, Manrope" to "SIL Open Font License 1.1",
    "AndroidX, Jetpack Compose, CameraX" to "Apache License 2.0",
    "ZXing" to "Apache License 2.0",
    "Kotlin, kotlinx.coroutines" to "Apache License 2.0",
    "Google Play services (Nearby)" to "Google APIs Terms of Service",
)

@Composable
private fun AboutRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PoseType.BodyStrong, color = PoseColors.Paper, modifier = Modifier.weight(1f))
        Icon(PoseIcons.Arrow, null, Modifier.size(20.dp), tint = PoseColors.PaperFaint)
    }
}

/** Bottom sheet over a dimmed backdrop; tapping the backdrop dismisses it. */
@Composable
private fun BoxScope.Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(420, easing = EaseOutExpo)) { it } + fadeIn(),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(),
    ) {
        Glass(
            Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding(),
            shape = RoundedCornerShape(32.dp),
            tint = PoseColors.Ink2.copy(alpha = 0.97f),
        ) {
            Column(Modifier.padding(24.dp)) { content() }
        }
    }
}

@Composable
private fun SafeLine(text: String, on: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (on) PoseIcons.Check else PoseIcons.Close,
            null,
            Modifier.size(15.dp),
            tint = if (on) PoseColors.Mint else PoseColors.PaperFaint,
        )
        HSpace(10.dp)
        Text(text, style = PoseType.Caption, color = if (on) PoseColors.PaperDim else PoseColors.PaperFaint)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (selected) 1f else 0f, label = "chip")
    Box(
        Modifier
            .semantics { this.selected = selected }
            .height(38.dp)
            .clip(CircleShape)
            .background(PoseColors.GlassLight)
            .background(PoseColors.AccentBrush, alpha = bg)
            .pressable(enabled = enabled, pressedScale = 0.92f, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = PoseType.Mono,
            color = when {
                selected -> PoseColors.Ink
                enabled -> PoseColors.Paper
                else -> PoseColors.PaperGhost
            },
        )
    }
}

/** Small pill switch in the app's accent. */
@Composable
fun Toggle(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val knob by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.7f), label = "knob")
    val fill by animateFloatAsState(if (checked) 1f else 0f, label = "fill")
    Box(
        Modifier
            .width(50.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(PoseColors.PaperGhost)
            .background(Brush.linearGradient(listOf(PoseColors.Sky, PoseColors.Azure)), alpha = fill)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            }
            .pressable(pressedScale = 0.94f) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(PoseColors.Paper),
        )
    }
}
