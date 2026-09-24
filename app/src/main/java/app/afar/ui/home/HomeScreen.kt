package app.afar.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.afar.R
import app.afar.net.Role
import app.afar.ui.components.AuroraBackground
import app.afar.ui.components.Glass
import app.afar.ui.components.GlassIconButton
import app.afar.ui.components.HSpace
import app.afar.ui.components.Overline
import app.afar.ui.components.SecondaryButton
import app.afar.ui.components.VSpace
import app.afar.ui.components.enter
import app.afar.ui.components.pressable
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType

@OptIn(ExperimentalTextApi::class)
@Composable
fun HomeScreen(lastRole: Role?, onPick: (Role) -> Unit, onHowItWorks: () -> Unit) {
    var sharing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).enter(0), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "afar",
                    style = AfarType.TitleSmall.copy(fontStyle = FontStyle.Italic),
                    color = AfarColors.Paper,
                )
                Box(Modifier.weight(1f))
                GlassIconButton(AfarIcons.Info, "How it works", onHowItWorks)
            }

            Box(Modifier.weight(1f))

            Overline("Remote selfie camera", Modifier.enter(1), color = AfarColors.Apricot)
            VSpace(14.dp)
            Text(
                buildAnnotatedString {
                    append("Great photos\nof yourself,\n")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, brush = AfarColors.AccentBrush)) { append("from afar.") }
                },
                style = AfarType.Hero,
                color = AfarColors.Paper,
                modifier = Modifier.enter(2),
            )
            VSpace(16.dp)
            Text(
                "Prop one phone on a rock. Hold the other. See yourself live, then shoot.",
                style = AfarType.Body,
                color = AfarColors.PaperDim,
                modifier = Modifier.enter(3).padding(end = 36.dp),
            )

            VSpace(30.dp)

            RoleCard(
                icon = AfarIcons.Camera,
                title = "This phone is the Camera",
                subtitle = "Prop it on a rock, a wall or a tripod",
                lastUsed = lastRole == Role.Camera,
                onClick = { onPick(Role.Camera) },
                modifier = Modifier.enter(4),
            )
            VSpace(12.dp)
            RoleCard(
                icon = AfarIcons.Remote,
                title = "This phone is the Remote",
                subtitle = "Keep it in hand — frame, then tap",
                lastUsed = lastRole == Role.Remote,
                onClick = { onPick(Role.Remote) },
                modifier = Modifier.enter(5),
            )

            VSpace(18.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .enter(6)
                    .clip(CircleShape)
                    .pressable { sharing = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AfarIcons.Qr, null, Modifier.size(17.dp), tint = AfarColors.PaperDim)
                HSpace(8.dp)
                Text("Get Afar on the other phone", style = AfarType.Label, color = AfarColors.PaperDim)
            }
            AdSlot()
            VSpace(8.dp)
        }

        ShareSheet(visible = sharing, onDismiss = { sharing = false })
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    lastUsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (lastUsed) {
        Brush.linearGradient(listOf(AfarColors.Apricot.copy(alpha = 0.75f), AfarColors.Coral.copy(alpha = 0.15f), Color.White.copy(alpha = 0.06f)))
    } else {
        AfarColors.HairlineBrush
    }
    Glass(
        modifier.fillMaxWidth().pressable(pressedScale = 0.975f, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        border = border,
        tint = Color(0xFF121418).copy(alpha = 0.72f),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AfarColors.Apricot.copy(alpha = 0.22f), AfarColors.Coral.copy(alpha = 0.10f))))
                    .border(1.dp, Brush.linearGradient(listOf(AfarColors.Apricot.copy(alpha = 0.6f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(26.dp), tint = AfarColors.Apricot)
            }
            HSpace(16.dp)
            Column(Modifier.weight(1f)) {
                if (lastUsed) {
                    Overline("Last used", color = AfarColors.Apricot)
                    VSpace(3.dp)
                }
                Text(title, style = AfarType.TitleSmall.copy(fontSize = AfarType.TitleSmall.fontSize * 0.86f), color = AfarColors.Paper)
                VSpace(2.dp)
                Text(subtitle, style = AfarType.Caption, color = AfarColors.PaperDim)
            }
            HSpace(8.dp)
            Icon(AfarIcons.Arrow, null, Modifier.size(20.dp), tint = AfarColors.PaperFaint)
        }
    }
}

/**
 * Banner-ad placement. The spec allows ads on Home and Pairing only (never while shooting);
 * wire the ad SDK in here. Renders nothing until then so the MVP stays small and clean.
 */
@Composable
fun AdSlot(modifier: Modifier = Modifier) {
    Box(modifier)
}

/** Bottom sheet with a QR code linking to the store, so the second phone can install fast. */
@Composable
private fun ShareSheet(visible: Boolean, onDismiss: () -> Unit) {
    val url = stringResource(R.string.play_store_url)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(420, easing = app.afar.ui.components.EaseOutExpo)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(),
        ) {
            Glass(
                Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding(),
                shape = RoundedCornerShape(32.dp),
                tint = AfarColors.Ink2.copy(alpha = 0.97f),
            ) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Overline("Second phone", color = AfarColors.Apricot)
                    VSpace(8.dp)
                    Text("Scan to get Afar", style = AfarType.Title, color = AfarColors.Paper, textAlign = TextAlign.Center)
                    VSpace(8.dp)
                    Text(
                        "Point the other phone's camera here. Both phones run the same free app.",
                        style = AfarType.Body,
                        color = AfarColors.PaperDim,
                        textAlign = TextAlign.Center,
                    )
                    VSpace(22.dp)
                    Box(
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(AfarColors.Paper)
                            .padding(22.dp),
                    ) {
                        QrCode(url, Modifier.fillMaxSize().aspectRatio(1f), color = AfarColors.Ink)
                    }
                    VSpace(22.dp)
                    SecondaryButton("Done", onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

