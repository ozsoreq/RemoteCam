package app.holdthatpose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.holdthatpose.PoseApp
import app.holdthatpose.MainActivity
import app.holdthatpose.net.Role
import app.holdthatpose.session.SessionService
import app.holdthatpose.ui.camera.CameraScreen
import app.holdthatpose.ui.home.HomeScreen
import app.holdthatpose.ui.home.OnboardingScreen
import app.holdthatpose.ui.pairing.RemotePairingScreen
import app.holdthatpose.ui.permissions.PermissionScreen
import app.holdthatpose.ui.permissions.missingPermissions
import app.holdthatpose.ui.remote.RemoteScreen
import app.holdthatpose.ui.settings.ConsentScreen
import app.holdthatpose.ui.settings.PrivacyPolicyScreen
import app.holdthatpose.ui.settings.SettingsScreen
import app.holdthatpose.ui.theme.PoseColors

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Permissions(val role: Role) : Screen
    data object Camera : Screen
    data object RemotePairing : Screen
    data object Remote : Screen
    data object Settings : Screen
    data object Privacy : Screen
    /** [then] = role waiting on the agreement; null = read-only from Settings. */
    data class Consent(val then: Role?) : Screen
}

private fun Screen.key(): String = when (this) {
    Screen.Onboarding -> "onboarding"
    Screen.Home -> "home"
    is Screen.Permissions -> "perm:${role.name}"
    Screen.Camera -> "camera"
    Screen.RemotePairing -> "pairing"
    Screen.Remote -> "remote"
    Screen.Settings -> "settings"
    Screen.Privacy -> "privacy"
    is Screen.Consent -> "consent:${then?.name.orEmpty()}"
}

private fun screenOf(key: String): Screen? {
    fun role(name: String) = Role.entries.firstOrNull { it.name == name }
    return when {
        key == "onboarding" -> Screen.Onboarding
        key == "home" -> Screen.Home
        key.startsWith("perm:") -> role(key.removePrefix("perm:"))?.let { Screen.Permissions(it) }
        key == "camera" -> Screen.Camera
        key == "pairing" -> Screen.RemotePairing
        key == "remote" -> Screen.Remote
        key == "settings" -> Screen.Settings
        key == "privacy" -> Screen.Privacy
        key.startsWith("consent:") -> Screen.Consent(role(key.removePrefix("consent:")))
        else -> null
    }
}

/** Survives activity recreation (split screen, font size, fold…) so a live session isn't orphaned. */
private val ScreenSaver = Saver<Screen, String>(
    save = { it.key() },
    restore = { screenOf(it) },
)

@Composable
fun AppRoot(app: PoseApp, activity: MainActivity) {
    val context = LocalContext.current
    var screen by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf(
            when {
                // The sessions live in the application: if one is still running, show it.
                app.cameraSession.isRunning -> Screen.Camera
                app.remoteSession.isRunning -> Screen.Remote
                app.prefs.onboarded -> Screen.Home
                else -> Screen.Onboarding
            },
        )
    }

    // Tear down the screen we're leaving *before* switching, so the outgoing screen's exit
    // animation can't stop a link the incoming screen has just started.
    fun navigate(to: Screen) {
        when (screen) {
            Screen.Camera -> {
                app.cameraSession.stop()
                SessionService.stop(context)
            }
            Screen.Remote -> app.remoteSession.stop()
            Screen.RemotePairing -> if (to != Screen.Remote) {
                app.link.stopAll()
                SessionService.stop(context)
            }
            else -> Unit
        }
        screen = to
    }

    fun startRole(role: Role) {
        app.prefs.lastRole = role
        navigate(when {
            !app.prefs.consented -> Screen.Consent(role)
            context.missingPermissions(role).isNotEmpty() -> Screen.Permissions(role)
            role == Role.Camera -> Screen.Camera
            else -> Screen.RemotePairing
        })
    }

    BackHandler(enabled = screen != Screen.Home && screen != Screen.Onboarding) {
        navigate(
            when (val s = screen) {
                Screen.Remote -> Screen.RemotePairing
                is Screen.Consent -> if (s.then == null) Screen.Settings else Screen.Home
                Screen.Privacy -> Screen.Settings
                else -> Screen.Home
            },
        )
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.985f, animationSpec = tween(420))) togetherWith fadeOut(tween(260))
        },
        modifier = Modifier.fillMaxSize().background(PoseColors.Ink),
        label = "screens",
    ) { s ->
        when (s) {
            Screen.Onboarding -> OnboardingScreen(onDone = {
                app.prefs.onboarded = true
                navigate(Screen.Home)
            })
            Screen.Home -> HomeScreen(
                lastRole = app.prefs.lastRole,
                onPick = ::startRole,
                onHowItWorks = { navigate(Screen.Onboarding) },
                onSettings = { navigate(Screen.Settings) },
            )
            Screen.Privacy -> PrivacyPolicyScreen(onBack = { navigate(Screen.Settings) })
            Screen.Settings -> SettingsScreen(
                app.prefs,
                onBack = { navigate(Screen.Home) },
                onResponsibleUse = { navigate(Screen.Consent(null)) },
                onPrivacy = { navigate(Screen.Privacy) },
            )
            is Screen.Consent -> ConsentScreen(
                onAgree = s.then?.let { role ->
                    {
                        app.prefs.consented = true
                        startRole(role)
                    }
                },
                onBack = { navigate(if (s.then == null) Screen.Settings else Screen.Home) },
            )
            is Screen.Permissions -> PermissionScreen(
                role = s.role,
                onGranted = { navigate(if (s.role == Role.Camera) Screen.Camera else Screen.RemotePairing) },
                onBack = { navigate(Screen.Home) },
            )
            Screen.Camera -> CameraScreen(app, activity, onExit = { navigate(Screen.Home) })
            Screen.RemotePairing -> RemotePairingScreen(
                app,
                onConnected = { navigate(Screen.Remote) },
                onBack = { navigate(Screen.Home) },
            )
            Screen.Remote -> RemoteScreen(app, activity, onExit = { navigate(Screen.RemotePairing) })
        }
    }
}
