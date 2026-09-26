package app.holdthatpose

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the real app on an emulator: first-run tutorial → home → settings (safe-mode rules) →
 * responsible-use agreement → permission screen. Screenshots every step.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("holdthatpose", Context.MODE_PRIVATE)

    @Before
    fun freshInstall() {
        stopRunningSessions()
        prefs.edit().clear().commit()
    }

    @Test
    fun firstRunThroughSettingsAndConsent() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Tutorial
            compose.screenshot("01_onboarding")
            compose.onNodeWithText("STEP 01").assertIsDisplayed()
            compose.onNodeWithText("Next").performClick()
            compose.screenshot("01b_onboarding_step2")
            compose.onNodeWithText("STEP 02").assertIsDisplayed()
            compose.onNodeWithText("Skip").performClick()

            // Home: two roles, very little text
            compose.screenshot("02_home")
            compose.onNodeWithText("Camera").assertIsDisplayed()
            compose.onNodeWithText("Remote").assertIsDisplayed()

            // Settings: safe mode on by default; auto-disconnect has no "Off" in any mode
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.screenshot("03_settings")
            compose.onNodeWithText("Safe mode").assertIsDisplayed()
            compose.onAllNodesWithText("Off").assertCountEquals(0)
            compose.onNodeWithText("2 min").performClick()
            assertEquals(120, prefs.getInt("idle", 0))

            // Turning safe mode off needs a confirmation; turning it back on doesn't
            compose.onNodeWithContentDescription("Safe mode switch").performClick()
            compose.waitForIdle()
            assertEquals(true, prefs.getBoolean("safe", true))
            compose.screenshot("03b_settings_confirm_off")
            compose.onNodeWithText("Turn off").performClick()
            compose.waitForIdle()
            assertEquals(false, prefs.getBoolean("safe", true))
            compose.onAllNodesWithText("Off").assertCountEquals(0)
            compose.onNodeWithContentDescription("Safe mode switch").performClick()
            compose.waitForIdle()
            assertEquals(true, prefs.getBoolean("safe", false))
            assertEquals(120, prefs.getInt("idle", 0))

            // Privacy policy opens in-app (bundled, works offline) and returns to Settings
            compose.onNodeWithText("Privacy policy").performScrollTo().performClick()
            compose.waitForIdle()
            compose.screenshot("08_privacy_policy")
            compose.onNodeWithText("Privacy Policy").assertIsDisplayed()
            compose.onNodeWithText("In short").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Safe mode").assertIsDisplayed()

            // Back home, pick Remote → agreement first
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Remote").performClick()
            compose.screenshot("04_consent")
            compose.onNodeWithText("I agree").assertIsDisplayed()
            compose.onNodeWithText("I agree").performClick()
            assertTrue(prefs.getBoolean("consent", false))

            // Permissions persist across tests on the same device: if another test already
            // granted them, the app correctly skips straight to pairing.
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val needsPermissions = with(app.holdthatpose.ui.permissions.PermissionsCheck) { ctx.missing(app.holdthatpose.net.Role.Remote) }
            if (needsPermissions) {
                compose.screenshot("05_permissions")
                compose.onNodeWithText("Permissions").assertIsDisplayed()
                compose.onNodeWithText("Nearby devices").assertIsDisplayed()
            } else {
                compose.screenshot("05_pairing")
                compose.onNodeWithText("Finding\nCamera…").assertIsDisplayed()
            }
        }
    }
}

/**
 * With permissions granted, the Camera role opens the live viewfinder and waits for a Remote
 * without crashing (CameraX + Nearby + foreground service all start).
 */
@RunWith(AndroidJUnit4::class)
class CameraSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray(),
    )

    @Before
    fun returningUser() {
        stopRunningSessions()
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("holdthatpose", Context.MODE_PRIVATE).edit()
            .clear()
            .putBoolean("onboarded", true)
            .putBoolean("consent", true)
            .commit()
    }

    @Test
    fun cameraRoleShowsWaitingCard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Camera").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Waiting for\nRemote")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Lock screen").assertIsDisplayed()
            compose.screenshot("06_camera_waiting")

            // Lock overlay appears and the app keeps running.
            compose.onNodeWithContentDescription("Lock screen").performClick()
            compose.onNodeWithText("Hold to unlock").assertIsDisplayed()
            compose.screenshot("07_camera_locked")
        }
    }
}

/**
 * Sessions live in the Application and the test process is shared between tests, so a
 * session left running by one test would (correctly) make the app reopen straight into it.
 * Start every test from a clean slate.
 */
internal fun stopRunningSessions() {
    val instr = InstrumentationRegistry.getInstrumentation()
    val app = instr.targetContext.applicationContext as PoseApp
    instr.runOnMainSync {
        app.cameraSession.stop()
        app.remoteSession.stop()
    }
}
