package app.holdthatpose.media

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import kotlin.math.ceil

/**
 * Countdown beeps and chimes play on the alarm stream so bystanders hear them. While the
 * Camera runs, this raises a quiet alarm volume to half (restoring it afterwards) and reports
 * when the cues can't be heard anyway (volume 0 or Do Not Disturb set to total silence).
 */
class AlarmGuard(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private var original = -1
    private var raisedTo = -1

    fun engage() {
        val a = audio ?: return
        if (original >= 0) return
        runCatching {
            val max = a.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val floor = ceil(max * 0.5).toInt()
            val current = a.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current < floor) {
                a.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
                original = current
                raisedTo = floor
            }
        }
    }

    /** True when the Camera's cues won't be heard. */
    val muted: Boolean
        get() = runCatching {
            val silent = audio?.getStreamVolume(AudioManager.STREAM_ALARM) == 0
            val dnd = notifications?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
            silent || dnd
        }.getOrDefault(false)

    /** Puts the user's alarm volume back, unless they changed it themselves meanwhile. */
    fun release() {
        val a = audio
        if (a != null && original >= 0) {
            runCatching {
                if (a.getStreamVolume(AudioManager.STREAM_ALARM) == raisedTo) {
                    a.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
                }
            }
        }
        original = -1
        raisedTo = -1
    }
}
