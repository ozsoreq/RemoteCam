package app.afar.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.afar.AfarApp
import app.afar.MainActivity
import app.afar.net.Role
import app.afar.session.SessionService
import app.afar.ui.camera.CameraScreen
import app.afar.ui.home.HomeScreen
import app.afar.ui.home.OnboardingScreen
import app.afar.ui.pairing.RemotePairingScreen
import app.afar.ui.permissions.PermissionScreen
import app.afar.ui.permissions.missingPermissions
import app.afar.ui.remote.RemoteScreen
import app.afar.ui.settings.ConsentScreen
import app.afar.ui.settings.SettingsScreen
import app.afar.ui.theme.AfarColors

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Permissions(val role: Role) : Screen
    data object Camera : Screen
    data object RemotePairing : Screen
    data object Remote : Screen
    data object Settings : Screen
    /** [then] = role waiting on the agreement; null = read-only from Settings. */
    data class Consent(val then: Role?) : Screen
}

@Composable
fun AppRoot(app: AfarApp, activity: MainActivity) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(if (app.prefs.onboarded) Screen.Home else Screen.Onboarding) }

    // Tear down the screen we're leaving *before* switching, so the outgoing screen's exit
    // animation can't stop a link the incoming screen has just started.
    fun navigate(to: Screen) {
        when (screen) {
            Screen.Camera -> {
                app.cameraSession.stop()
                SessionService.stop(context)
            }
            Screen.Remote -> {
                app.remoteSession.stop()
                app.remoteSession.suppressAutoConnect = true
            }
            Screen.RemotePairing -> if (to == Screen.Remote) {
                app.remoteSession.suppressAutoConnect = false
            } else {
                app.remoteSession.suppressAutoConnect = false
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
                else -> Screen.Home
            },
        )
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.985f, animationSpec = tween(420))) togetherWith fadeOut(tween(260))
        },
        modifier = Modifier.fillMaxSize().background(AfarColors.Ink),
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
            Screen.Settings -> SettingsScreen(
                app.prefs,
                onBack = { navigate(Screen.Home) },
                onResponsibleUse = { navigate(Screen.Consent(null)) },
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
