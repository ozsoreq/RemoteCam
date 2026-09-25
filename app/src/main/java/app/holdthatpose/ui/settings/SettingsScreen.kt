package app.holdthatpose.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.holdthatpose.data.Prefs
import app.holdthatpose.session.formatDuration
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.components.Glass
import app.holdthatpose.ui.components.GlassIconButton
import app.holdthatpose.ui.components.HSpace
import app.holdthatpose.ui.components.Overline
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.components.pressable
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onResponsibleUse: () -> Unit) {
    var safe by remember { mutableStateOf(prefs.safeMode) }
    var idle by remember { mutableIntStateOf(prefs.idleTimeoutSec) }

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
                        Toggle(safe, "Safe mode switch") {
                            safe = it
                            prefs.safeMode = it
                            if (it && idle <= 0) {
                                idle = Prefs.DEFAULT_IDLE
                                prefs.idleTimeoutSec = idle
                            }
                        }
                    }
                    VSpace(14.dp)
                    SafeLine("Allow each new Remote on the Camera", safe)
                    SafeLine("LIVE sign always shown on the Camera", safe)
                    SafeLine("Sound when a Remote connects", safe)
                    SafeLine("Auto-disconnect can't be turned off", safe)
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
                            val enabled = !(safe && value <= 0)
                            Chip(formatDuration(value), selected = value == idle, enabled = enabled) {
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
            VSpace(24.dp)
            Text(
                "Applies from the next session.",
                style = PoseType.Caption,
                color = PoseColors.PaperFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            VSpace(20.dp)
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
