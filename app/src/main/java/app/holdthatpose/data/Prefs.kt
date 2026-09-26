package app.holdthatpose.data

import android.content.Context
import androidx.core.content.edit
import app.holdthatpose.net.Role
import java.util.UUID

/** Tiny SharedPreferences wrapper: remembered role, last paired phone and viewfinder toggles. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("holdthatpose", Context.MODE_PRIVATE)

    val installId: String = sp.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().take(8).also {
        sp.edit { putString(KEY_INSTALL, it) }
    }

    var lastRole: Role?
        get() = sp.getString("role", null)?.let { r -> Role.entries.firstOrNull { it.name == r } }
        set(value) = sp.edit { putString("role", value?.name) }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit { putBoolean("onboarded", value) }

    /** Install id of the phone we last paired with, so reconnects skip the code check. */
    var lastPeerId: String?
        get() = sp.getString("peer", null)
        set(value) = sp.edit { putString("peer", value) }

    var lastPeerName: String?
        get() = sp.getString("peerName", null)
        set(value) = sp.edit { putString("peerName", value) }

    var grid: Boolean
        get() = sp.getBoolean("grid", true)
        set(value) = sp.edit { putBoolean("grid", value) }

    var level: Boolean
        get() = sp.getBoolean("level", true)
        set(value) = sp.edit { putBoolean("level", value) }

    var mirror: Boolean
        get() = sp.getBoolean("mirror", true)
        set(value) = sp.edit { putBoolean("mirror", value) }

    var burst: Boolean
        get() = sp.getBoolean("burst", true)
        set(value) = sp.edit { putBoolean("burst", value) }

    var timer: Int
        get() = sp.getInt("timer", 3)
        set(value) = sp.edit { putInt("timer", value) }

    /** Safe mode: Camera-side approval, visible LIVE indicator, enforced auto-disconnect. */
    var safeMode: Boolean
        get() = sp.getBoolean("safe", true)
        set(value) = sp.edit { putBoolean("safe", value) }

    /** Seconds without a Remote command before the link drops (0 from older versions = default). */
    var idleTimeoutSec: Int
        get() = sp.getInt("idle", DEFAULT_IDLE)
        set(value) = sp.edit { putInt("idle", value) }

    /** The timeout actually enforced: never "off" (see [effectiveTimeout]). */
    val effectiveIdleTimeout: Int
        get() = effectiveTimeout(safeMode, idleTimeoutSec)

    /** User agreed to the responsible-use terms. */
    var consented: Boolean
        get() = sp.getBoolean("consent", false)
        set(value) = sp.edit { putBoolean("consent", value) }

    companion object {
        const val DEFAULT_IDLE = 60
        /** Longest auto-disconnect there is; with safe mode off, "off" (0) means this. */
        const val MAX_IDLE = 600
        val IDLE_CHOICES = listOf(30, 60, 120, 300, 600)

        /** Auto-disconnect is never off: 0 means the default (safe mode) or the 10-min maximum. */
        fun effectiveTimeout(safeMode: Boolean, configured: Int): Int = when {
            configured <= 0 -> if (safeMode) DEFAULT_IDLE else MAX_IDLE
            else -> configured.coerceAtMost(MAX_IDLE)
        }

        private const val KEY_INSTALL = "install"
    }
}
