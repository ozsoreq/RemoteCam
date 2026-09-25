package app.holdthatpose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.holdthatpose.ui.components.AuroraBackground
import app.holdthatpose.ui.components.GlassIconButton
import app.holdthatpose.ui.components.HSpace
import app.holdthatpose.ui.components.PrimaryButton
import app.holdthatpose.ui.components.VSpace
import app.holdthatpose.ui.components.enter
import app.holdthatpose.ui.icons.PoseIcons
import app.holdthatpose.ui.theme.PoseColors
import app.holdthatpose.ui.theme.PoseType

private data class Rule(val icon: ImageVector, val text: String)

private val rules = listOf(
    Rule(PoseIcons.Camera, "Only photograph people who know and agree."),
    Rule(PoseIcons.Lock, "Never use Hold That Pose in private places — bathrooms, changing rooms, homes that aren't yours."),
    Rule(PoseIcons.Pin, "Don't leave the Camera unattended where others can't see it."),
    Rule(PoseIcons.Info, "Follow local laws on photography and privacy. You're responsible for your photos."),
)

/**
 * One-time responsible-use agreement shown before the first session (and readable later from
 * Settings). With [onAgree] null it's read-only.
 */
@Composable
fun ConsentScreen(onAgree: (() -> Unit)?, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.6f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            VSpace(12.dp)
            GlassIconButton(PoseIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                VSpace(30.dp)
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(PoseColors.Sky.copy(alpha = 0.14f)).enter(0),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PoseIcons.Shield, null, Modifier.size(28.dp), tint = PoseColors.Sky)
                }
                VSpace(20.dp)
                Text("Shoot\nresponsibly", style = PoseType.Title, color = PoseColors.Paper, modifier = Modifier.enter(1))
                VSpace(26.dp)
                rules.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).enter(2 + i), verticalAlignment = Alignment.Top) {
                        Icon(r.icon, null, Modifier.size(20.dp), tint = PoseColors.PaperDim)
                        HSpace(14.dp)
                        Text(r.text, style = PoseType.Body, color = PoseColors.Paper)
                    }
                }
                VSpace(20.dp)
            }
            if (onAgree != null) {
                PrimaryButton("I agree", onAgree, icon = PoseIcons.Check)
                VSpace(18.dp)
            }
        }
    }
}
