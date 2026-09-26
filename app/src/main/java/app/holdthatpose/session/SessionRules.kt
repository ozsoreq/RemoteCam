package app.holdthatpose.session

import app.holdthatpose.net.Cmd

/*
 * Pure session rules shared by the Camera and Remote sessions. Kept free of Android types so
 * they can be unit-tested on the JVM.
 */

/** After a drop, how long the Camera lets the in-session Remote back in without Allow. */
const val REENTRY_WINDOW_MS = 45_000L

/** Safe mode: session length before the Camera asks "Still OK?". */
const val SESSION_CAP_MS = 30 * 60_000L

/** Safe mode: time to tap Continue on the Camera before the session ends. */
const val CAP_GRACE_MS = 60_000L

/** One CameraX capture callback; a lost callback must not hang the session. */
const val SHOT_TIMEOUT_MS = 5_000L

/** A whole capture (burst, pick, save, review copy). */
const val CAPTURE_TIMEOUT_MS = 20_000L

/** Remote: how long "Saving photo…" may wait for the review copy. */
const val AWAIT_PHOTO_TIMEOUT_MS = 30_000L

/** A handshake nobody confirms is dropped after this. */
const val PENDING_TIMEOUT_MS = 60_000L

/**
 * Seconds left in the "Still OK?" grace period, or -1 while the session is under [capMs].
 * Rounds up, so the Remote shows 60…1 and 0 means time is up.
 */
fun capLeft(elapsedMs: Long, capMs: Long, graceMs: Long): Int {
    if (elapsedMs < capMs) return -1
    val left = capMs + graceMs - elapsedMs
    if (left <= 0) return 0
    return ((left + 999) / 1000).toInt()
}

/** Only deliberate Remote actions keep an idle session alive; pings and status traffic don't. */
fun countsAsActivity(cmd: Cmd): Boolean = when (cmd) {
    is Cmd.Shutter, Cmd.CancelCountdown, is Cmd.SetLens, is Cmd.Focus, Cmd.KeepAlive, is Cmd.Delete -> true
    else -> false
}

/**
 * Remote side: accept a photo only if it's new and was asked for — either the Camera announced
 * it ([captured]) or we have sent more shutters than we have received photos (a photo synced
 * after a drop at the moment of capture). Anything else is an unsolicited push.
 */
fun acceptPhoto(id: String, received: Set<String>, captured: Set<String>, requested: Int): Boolean =
    id.isNotEmpty() && id !in received && (id in captured || received.size < requested)

/** Google Play services / Nearby status codes we translate into something actionable. */
object LinkCodes {
    // CommonStatusCodes / ConnectionResult
    const val SERVICE_MISSING = 1
    const val SERVICE_VERSION_UPDATE_REQUIRED = 2
    const val SERVICE_DISABLED = 3
    const val SERVICE_INVALID = 9
    const val API_UNAVAILABLE = 16
    const val API_NOT_CONNECTED = 17
    const val SERVICE_UPDATING = 18

    // ConnectionsStatusCodes
    const val STATUS_RADIO_ERROR = 8007 // also STATUS_BLUETOOTH_ERROR
    const val MISSING_SETTING_LOCATION_MUST_BE_ON = 8025
    val MISSING_PERMISSION = 8030..8039

    val PLAY_SERVICES = setOf(
        SERVICE_MISSING, SERVICE_VERSION_UPDATE_REQUIRED, SERVICE_DISABLED, SERVICE_INVALID,
        API_UNAVAILABLE, API_NOT_CONNECTED, SERVICE_UPDATING,
    )
}

/** Short, actionable text for a failed advertise/discover/connect; falls back to [action]. */
fun linkErrorMessage(action: String, statusCode: Int?): String = when {
    statusCode == null -> action
    statusCode in LinkCodes.PLAY_SERVICES -> "Needs Google Play services"
    statusCode == LinkCodes.STATUS_RADIO_ERROR -> "Turn on Bluetooth and Wi-Fi"
    statusCode == LinkCodes.MISSING_SETTING_LOCATION_MUST_BE_ON -> "Turn on Location"
    statusCode in LinkCodes.MISSING_PERMISSION -> "Permission needed"
    else -> action
}
