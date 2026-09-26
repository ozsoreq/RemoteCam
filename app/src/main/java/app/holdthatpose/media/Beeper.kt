package app.holdthatpose.media

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Countdown beeps loud enough to hear from 20 m, plus a light haptic tick on the Remote.
 * Tones use the alarm stream; [AlarmGuard] keeps it audible while the Camera runs.
 */
class Beeper(context: Context) {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tick() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
    }

    fun shutter() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
    }

    /** Audible cue near the Camera when a Remote connects (up) or the session ends (down). */
    fun chime(up: Boolean) {
        tone?.startTone(if (up) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 300)
    }

    /** Short, quieter cue when the in-session Remote comes back after a drop. */
    fun softChime() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun haptic(strong: Boolean = false) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(if (strong) 40 else 12, if (strong) 255 else 120))
    }

    fun release() {
        tone?.release()
    }
}
