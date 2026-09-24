package app.afar.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.afar.net.Role
import app.afar.ui.components.AuroraBackground
import app.afar.ui.components.Glass
import app.afar.ui.components.GlassIconButton
import app.afar.ui.components.HSpace
import app.afar.ui.components.Overline
import app.afar.ui.components.PrimaryButton
import app.afar.ui.components.StatusDot
import app.afar.ui.components.VSpace
import app.afar.ui.icons.AfarIcons
import app.afar.ui.theme.AfarColors
import app.afar.ui.theme.AfarType

/**
 * One screen that asks for everything the chosen role needs, each with a one-line reason.
 * If Android stops showing the prompt ("don't ask again"), the button becomes "Open settings".
 */
@Composable
fun PermissionScreen(role: Role, onGranted: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val groups = remember(role) { permissionGroups(role) }
    var refresh by remember { mutableIntStateOf(0) }
    var askedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        refresh++
        if (context.missingPermissions(role).isEmpty()) onGranted()
    }

    // Coming back from system settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                refresh++
                if (context.missingPermissions(role).isEmpty()) onGranted()
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val missing = remember(refresh) { context.missingPermissions(role) }
    val blocked = askedOnce && missing.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.7f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            VSpace(12.dp)
            GlassIconButton(AfarIcons.Back, "Back", onBack)
            VSpace(36.dp)
            Overline(if (role == Role.Camera) "Camera phone" else "Remote phone", color = AfarColors.Apricot)
            VSpace(10.dp)
            Text("A few quick\npermissions", style = AfarType.Title, color = AfarColors.Paper)
            VSpace(12.dp)
            Text(
                "Everything stays between your two phones. Nothing is uploaded.",
                style = AfarType.Body,
                color = AfarColors.PaperDim,
            )
            VSpace(28.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { g ->
                    val granted = g.permissions.all { it !in missing }
                    Glass(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    when (g.title) {
                                        "Camera" -> AfarIcons.Camera
                                        "Nearby devices" -> AfarIcons.Remote
                                        "Location" -> AfarIcons.Pin
                                        else -> AfarIcons.Burst
                                    },
                                    null,
                                    Modifier.size(24.dp),
                                    tint = AfarColors.Paper,
                                )
                            }
                            HSpace(14.dp)
                            Column(Modifier.weight(1f)) {
                                Text(g.title, style = AfarType.BodyStrong, color = AfarColors.Paper)
                                Text(g.reason, style = AfarType.Caption, color = AfarColors.PaperDim)
                            }
                            HSpace(10.dp)
                            if (granted) {
                                Glass(Modifier.size(26.dp), shape = CircleShape, tint = AfarColors.Mint.copy(alpha = 0.16f)) {
                                    Icon(AfarIcons.Check, "Granted", Modifier.align(Alignment.Center).size(15.dp), tint = AfarColors.Mint)
                                }
                            } else {
                                StatusDot(AfarColors.Apricot, dotSize = 7.dp)
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f))
            if (blocked) {
                Text(
                    "Android won't ask again. Turn on ${groups.filter { g -> g.permissions.any { it in missing } }.joinToString { it.title }} in Settings → Permissions.",
                    style = AfarType.Caption,
                    color = AfarColors.PaperDim,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                PrimaryButton("Open settings", onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }, icon = AfarIcons.Arrow)
            } else {
                PrimaryButton("Allow and continue", onClick = {
                    launcher.launch((missing + optionalPermissions()).distinct().toTypedArray())
                }, icon = AfarIcons.Arrow)
            }
            VSpace(20.dp)
        }
    }
}
